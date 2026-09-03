package dev.boreaslab.boreas.core

import android.os.ParcelFileDescriptor
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import dev.boreaslab.boreas.engine.AuthorityRead
import dev.boreaslab.boreas.engine.CaMaterial
import dev.boreaslab.boreas.model.CoreCounters
import dev.boreaslab.boreas.model.CoreEvent
import dev.boreaslab.boreas.model.CoreStatus
import dev.boreaslab.boreas.model.RuleSetSize
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Result of starting a tunnel. */
internal sealed interface TunnelStart {
    data class Started(val tunnel: NativeTunnel) : TunnelStart
    data class Refused(val status: CoreStatus) : TunnelStart
}

/**
 * Running tunnel and resources whose lifetimes are tied to it. A thread blocked
 * in `next_event` borrows the handle, so [shutdown] joins it before freeing the
 * handle. The descriptor is the core's from `start` on; `free` closes it.
 */
internal class NativeTunnel private constructor(
    private val library: BoreasLibrary,
    private val handle: Pointer,
    private val bypass: VpnBypass,
) {

    /** Bounded to apply backpressure instead of silently dropping events. */
    private val events = Channel<CoreEvent>(capacity = EVENT_BUFFER)

    private val stopped = AtomicBoolean(false)

    /** The reader outlived the join, so the handle remains allocated. */
    @Volatile
    var abandoned: Boolean = false
        private set

    private val reader = Thread({ readEvents() }, "boreas-events").apply {
        isDaemon = true
        start()
    }

    fun events(): Flow<CoreEvent> = events.receiveAsFlow()

    /** Replaces the rules. Blocks while the new set is compiled, proportional to list size. */
    fun reload(lists: List<String>): CoreStatus = NativeArena().use { arena ->
        // Reload results arrive on the event stream; counting this out-parameter would double them.
        val discarded = BoreasEvent()
        CoreStatus.of(
            library.boreas_tunnel_reload(
                handle,
                arena.utf8Array(lists),
                SizeT.of(lists.size),
                discarded,
            ),
        )
    }

    /**
     * Reads authority material with the two-call sizing protocol. Zero lengths
     * with `OK` means this tunnel does not intercept; `BUFFER_TOO_SMALL` is
     * expected from the sizing call.
     */
    fun authority(): AuthorityRead {
        val certificateLength = SizeTByReference()
        val keysLength = SizeTByReference()

        val sized = CoreStatus.of(
            library.boreas_tunnel_authority(
                handle, null, SizeT.ZERO, certificateLength, null, SizeT.ZERO, keysLength,
            ),
        )
        if (sized != CoreStatus.Ok && sized != CoreStatus.BufferTooSmall) {
            return AuthorityRead.Failed(sized)
        }
        if (certificateLength.value == 0L && keysLength.value == 0L) return AuthorityRead.None
        // Both halves are required for material that can be reused on the next launch.
        if (certificateLength.value == 0L || keysLength.value == 0L) {
            return AuthorityRead.Failed(CoreStatus.Authority)
        }

        return Memory(certificateLength.value).use { certificate ->
            Memory(keysLength.value).use { keys ->
                val read = CoreStatus.of(
                    library.boreas_tunnel_authority(
                        handle,
                        certificate, SizeT(certificateLength.value), certificateLength,
                        keys, SizeT(keysLength.value), keysLength,
                    ),
                )
                if (read != CoreStatus.Ok) {
                    AuthorityRead.Failed(read)
                } else {
                    val material = CaMaterial(
                        certificate = certificate.getByteArray(0, certificateLength.value.toInt()),
                        keys = keys.getByteArray(0, keysLength.value.toInt()),
                    )
                    // Clearing native memory limits how long the key remains in the native page.
                    keys.clear()
                    AuthorityRead.Present(material)
                }
            }
        }
    }

    /** Stops, joins, then frees, which closes the descriptor. Idempotent and blocking. */
    fun shutdown() {
        if (!stopped.compareAndSet(false, true)) return

        // The reader exits when the core reports BOREAS_STOPPED.
        library.boreas_tunnel_shutdown(handle)

        // Unblock a reader waiting for a stalled consumer.
        events.close()

        // The reader still borrows the handle until join returns.
        reader.join(READER_JOIN_MS)

        // Freeing while next_event is still running would use the borrowed handle.
        if (reader.isAlive) {
            abandoned = true
            return
        }

        library.boreas_tunnel_free(handle)
    }

    /** Callback defects captured instead of unwinding into C. */
    fun defects(): List<Throwable> = listOfNotNull(bypass.lastDefect)

    /** Reads events on a dedicated thread; an idle tunnel may emit nothing for hours. */
    private fun readEvents() {
        val event = BoreasEvent()
        Memory(TEXT_CAPACITY).use { name ->
            Memory(TEXT_CAPACITY).use { rule ->
                while (true) {
                    val status = CoreStatus.of(
                        library.boreas_tunnel_next_event(
                            handle, event, name, TEXT_CAPACITY_T, rule, TEXT_CAPACITY_T,
                        ),
                    )
                    if (status != CoreStatus.Ok) return

                    // JNA does not refresh Structure fields until read is called.
                    event.read()

                    val decoded = decode(event, name, rule) ?: continue
                    if (events.trySendBlocking(decoded).isClosed) return
                }
            }
        }
    }

    /** Decodes one event, ignoring kinds added after this build. See api/stability.md. */
    private fun decode(event: BoreasEvent, name: Memory, rule: Memory): CoreEvent? =
        when (event.kind) {
            BoreasEvent.KIND_RESOLVED -> {
                val nameLength = event.nameLength.toLong()
                val ruleLength = event.ruleLength.toLong()
                CoreEvent.Resolved(
                    name = name.getString(0, TEXT_ENCODING),
                    // Zero means no rule decided the packet.
                    rule = if (ruleLength == 0L) null else rule.getString(0, TEXT_ENCODING),
                    blocked = event.blocked != ZERO_BYTE,
                    // Lengths expose truncation instead of leaving it silent.
                    truncated = nameLength > TEXT_CAPACITY || ruleLength > TEXT_CAPACITY,
                )
            }

            BoreasEvent.KIND_RELOADED -> CoreEvent.Reloaded(
                RuleSetSize(
                    allowed = event.allowed.toLong(),
                    blocked = event.blockedRules.toLong(),
                    inspected = event.inspected.toLong(),
                ),
            )

            BoreasEvent.KIND_COUNTED -> CoreEvent.Counted(
                CoreCounters(
                    datagramsDropped = event.counters.datagramsDropped,
                    packetsRejected = event.counters.packetsRejected,
                    quicSteered = event.counters.quicSteered,
                    pathsReported = event.counters.pathsReported,
                    eventsLost = event.counters.eventsLost,
                    tasksPanicked = event.counters.tasksPanicked,
                ),
            )

            else -> null
        }

    internal companion object {

        /** DNS names cap at 255 bytes, so 256 bytes includes the terminator. */
        private const val TEXT_CAPACITY = 256L
        private val TEXT_CAPACITY_T = SizeT(TEXT_CAPACITY)
        private const val TEXT_ENCODING = "UTF-8"
        private const val ZERO_BYTE: Byte = 0

        private const val EVENT_BUFFER = 512
        private const val READER_JOIN_MS = 5_000L

        /**
         * Hands the descriptor to the core and starts the tunnel. Blocks through
         * lookup and handshake, so callers must keep it off the main thread.
         * The core owns the descriptor from here on every path, failure
         * included, and the bypass `release` has run on failure.
         */
        fun start(
            library: BoreasLibrary,
            config: CoreConfig,
            descriptor: ParcelFileDescriptor,
            bypass: VpnBypass,
        ): TunnelStart {
            val out = PointerByReference()

            val status = NativeArena().use { arena ->
                CoreStatus.of(
                    library.boreas_tunnel_start_fd(
                        config.marshal(arena),
                        descriptor.detachFd(),
                        config.mtu.toShort(),
                        bypass.vtable(),
                        out,
                    ),
                )
            }

            val handle = out.value
            if (status != CoreStatus.Ok || handle == null) {
                return TunnelStart.Refused(if (status == CoreStatus.Ok) CoreStatus.Datapath else status)
            }

            return TunnelStart.Started(NativeTunnel(library, handle, bypass))
        }
    }
}

package dev.boreaslab.boreas.core

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

/** Starting a tunnel: a handle, or the status that says why not. */
internal sealed interface TunnelStart {
    data class Started(val tunnel: NativeTunnel) : TunnelStart
    data class Refused(val status: CoreStatus) : TunnelStart
}

/**
 * One running tunnel and everything whose lifetime is tied to it.
 *
 * The handle, the reader thread, and both vtables are acquired together in [start]
 * and released together in [shutdown], in the order api/obligations.md#teardown
 * requires and for the reason it gives: a thread blocked in `next_event` holds a
 * borrow of the handle, so freeing it from another thread at that moment is a
 * use-after-free that no amount of internal locking can fix. Stop signals, the
 * reader observes it and returns, it is joined, and only then is the handle
 * unreferenced.
 *
 * The descriptor closes last of all, after the device's `release` callback has
 * run, because a `recv` that was still in flight when the tunnel stopped keeps
 * running after its task is abandoned.
 */
internal class NativeTunnel private constructor(
    private val library: BoreasLibrary,
    private val handle: Pointer,
    private val device: TunDevice,
    private val bypass: VpnBypass,
) {

    /**
     * Bounded, and the bound is the point.
     *
     * The consumer folds each event in O(1), so this fills only if the UI thread
     * has stalled. When it does, the reader blocks rather than dropping, which
     * pushes back on the core and makes the loss its `events_lost` counter rather
     * than a gap this program would have to invent a name for.
     */
    private val events = Channel<CoreEvent>(capacity = EVENT_BUFFER)

    private val stopped = AtomicBoolean(false)

    /**
     * The reader did not return within the join, so the handle was never freed.
     *
     * Recorded rather than retried: a second attempt would face the same borrow,
     * and the diagnostics screen is where a leak that only a long-running process
     * would notice can be seen.
     */
    @Volatile
    var abandoned: Boolean = false
        private set

    private val reader = Thread({ readEvents() }, "boreas-events").apply {
        isDaemon = true
        start()
    }

    fun events(): Flow<CoreEvent> = events.receiveAsFlow()

    /**
     * Replaces the rules in force. Safe while the reader is parked, which is the
     * case that matters, because that reader may be parked for hours.
     *
     * Blocks for as long as compiling the new set takes, proportional to total list
     * length, so callers keep it off the main thread.
     */
    fun reload(lists: List<String>): CoreStatus = NativeArena().use { arena ->
        // The out-parameter is written and discarded: the same reload also arrives
        // on the event stream, which is where the UI reads it, because a reload
        // triggered from anywhere else arrives there too. Counting both would
        // double it.
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
     * The authority's material, sized then read.
     *
     * Two calls, because that is the protocol: the first with zero capacities
     * learns the lengths, and on a tunnel that intercepts it answers
     * `BUFFER_TOO_SMALL`, which is the whole point of that status rather than a
     * failure. Both lengths zero with `OK` means this tunnel does not intercept,
     * which is an answer.
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
        // An authority is two halves. One of them alone is not a tunnel that does
        // not intercept, and it is not something to hand back next launch either.
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
                    // The private key was in this block a moment ago. Clearing it does
                    // not make the copy on the JVM heap any less exposed, but it does
                    // keep the native page from outliving the read.
                    keys.clear()
                    AuthorityRead.Present(material)
                }
            }
        }
    }

    /**
     * Stop, join, free, then let go of the descriptor. Idempotent.
     *
     * Blocks: shutdown takes as long as an ordered shutdown takes, and free waits
     * up to 250 ms for a `recv` still in flight. Callers keep it off the main
     * thread.
     */
    fun shutdown() {
        if (!stopped.compareAndSet(false, true)) return

        // 1. Stops traffic and releases the reader, which then sees BOREAS_STOPPED.
        //    Idempotent and safe from any thread, so no teardown path has to
        //    remember whether it already ran.
        library.boreas_tunnel_shutdown(handle)

        // Also unblock a reader parked handing an event to a consumer that stalled.
        events.close()

        // 2. Ours to join. Until it returns, it holds a borrow of the handle.
        reader.join(READER_JOIN_MS)

        // A bounded join that went on to free anyway would be the exact
        // use-after-free the two-call teardown exists to prevent: the reader is
        // *inside* next_event, and no locking reaches a thread that is already
        // in the call. Leaking one handle and one descriptor for the life of the
        // process is the safer of the two mistakes, and shutdown has already
        // closed every socket and returned every pooled buffer.
        if (reader.isAlive) {
            abandoned = true
            return
        }

        // 3. Only now is the handle unreferenced.
        library.boreas_tunnel_free(handle)

        // The callbacks are done when release says so, which can be after free
        // returned. The descriptor closes after that and never before.
        device.awaitRelease()
        device.dispose()
    }

    /** Defects the callbacks swallowed rather than unwinding into C. */
    fun defects(): List<Throwable> = listOfNotNull(device.lastDefect, bypass.lastDefect)

    /**
     * The reader. Parks indefinitely by design, on a thread of its own by
     * requirement: a healthy idle tunnel emits nothing, for hours, and a long
     * silence is not a hang.
     */
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
                    // BOREAS_STOPPED is how this loop is meant to end.
                    if (status != CoreStatus.Ok) return

                    // JNA reads a Structure out-parameter back for us; doing it
                    // explicitly costs one pass and removes the question.
                    event.read()

                    val decoded = decode(event, name, rule) ?: continue
                    if (events.trySendBlocking(decoded).isClosed) return
                }
            }
        }
    }

    /**
     * One event, or null for a kind this build predates.
     *
     * Ignoring the unrecognised is what api/stability.md asks for: an event added
     * later is an event nothing here was missing before, and asserting
     * exhaustiveness over a set the other side may extend is not totality.
     */
    private fun decode(event: BoreasEvent, name: Memory, rule: Memory): CoreEvent? =
        when (event.kind) {
            BoreasEvent.KIND_RESOLVED -> {
                val nameLength = event.nameLength.toLong()
                val ruleLength = event.ruleLength.toLong()
                CoreEvent.Resolved(
                    name = name.getString(0, TEXT_ENCODING),
                    // Zero means no rule decided it, which is the ordinary case.
                    rule = if (ruleLength == 0L) null else rule.getString(0, TEXT_ENCODING),
                    blocked = event.blocked != ZERO_BYTE,
                    // The lengths are what the text *would* have needed, so a
                    // truncation is visible here rather than silent.
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

        /**
         * DNS caps a name at 255 bytes, so a name plus its terminator fits exactly
         * and cannot truncate. A rule can, and says so when it does.
         */
        private const val TEXT_CAPACITY = 256L
        private val TEXT_CAPACITY_T = SizeT(TEXT_CAPACITY)
        private const val TEXT_ENCODING = "UTF-8"
        private const val ZERO_BYTE: Byte = 0

        private const val EVENT_BUFFER = 512
        private const val READER_JOIN_MS = 5_000L

        /**
         * Builds everything and starts it.
         *
         * Blocks for as long as the first connection takes: a lookup, a handshake.
         * Call it off the main thread.
         *
         * On failure nothing was allocated and the handle is untouched, but both
         * `release` callbacks have already run, so the descriptor is closed here
         * rather than left to a caller who has no handle to hang it from.
         */
        fun start(
            library: BoreasLibrary,
            config: CoreConfig,
            device: TunDevice,
            bypass: VpnBypass,
        ): TunnelStart {
            val out = PointerByReference()

            val status = NativeArena().use { arena ->
                CoreStatus.of(
                    library.boreas_tunnel_start(
                        config.marshal(arena),
                        device.vtable(config.mtu),
                        bypass.vtable(),
                        out,
                    ),
                )
            }

            val handle = out.value
            if (status != CoreStatus.Ok || handle == null) {
                device.awaitRelease()
                device.dispose()
                return TunnelStart.Refused(if (status == CoreStatus.Ok) CoreStatus.Datapath else status)
            }

            return TunnelStart.Started(NativeTunnel(library, handle, device, bypass))
        }
    }
}

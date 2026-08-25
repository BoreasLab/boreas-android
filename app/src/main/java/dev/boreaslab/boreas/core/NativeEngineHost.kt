package dev.boreaslab.boreas.core

import dev.boreaslab.boreas.engine.AuthorityRead
import dev.boreaslab.boreas.engine.CaMaterial
import dev.boreaslab.boreas.engine.EngineHost
import dev.boreaslab.boreas.engine.EngineStart
import dev.boreaslab.boreas.engine.StopReason
import dev.boreaslab.boreas.model.CoreEvent
import dev.boreaslab.boreas.model.CoreStatus
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.Filtering
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.TypedFailure
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

/**
 * The engine host that speaks to the shared library.
 *
 * Every call into C blocks, so every one of them is moved to [Dispatchers.IO]
 * here, once, rather than at each of the call sites that would otherwise have to
 * remember. `start` blocks for as long as the first connection takes and
 * `shutdown` for as long as an ordered shutdown does; neither belongs on a thread
 * anything else is waiting on.
 *
 * One session at a time. The ABI permits several tunnels in one process and they
 * share nothing, but Android permits one `VpnService` interface, so a second
 * session would be a second tunnel over the same descriptor.
 */
internal class NativeEngineHost(
    private val vpn: VpnPlatform,
    private val authority: AuthorityStore,
    private val clock: () -> Long,
) : EngineHost {

    private data class Live(val session: SessionId, val tunnel: NativeTunnel)

    private val live = AtomicReference<Live?>(null)

    override val isAvailable: Boolean
        get() = BoreasCore.library is CoreLibrary.Linked

    override val simulated: Boolean = false

    override suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart {
        val library = when (val loaded = BoreasCore.library) {
            is CoreLibrary.Linked -> loaded.library
            // Nothing was asked of the core, because nothing could be called.
            is CoreLibrary.Absent -> return EngineStart.Refused(loaded.failure)
        }

        val descriptor = when (val established = vpn.establish(platform)) {
            is Establishment.Established -> established.descriptor
            Establishment.Refused -> return EngineStart.Refused(TypedFailure.ConsentUnavailable)
            is Establishment.Rejected -> return EngineStart.Refused(TypedFailure.InterfaceRejected)
        }

        val device = TunDevice(descriptor)
        val bypass = vpn.bypass()
        // The one number that must appear twice. Read from the same field the
        // interface was built from, so the two cannot be given different answers.
        val config = CoreConfig(engine, platform.mtu.bytes, authority.load())

        return when (val started = withContext(Dispatchers.IO) {
            NativeTunnel.start(library, config, device, bypass)
        }) {
            is TunnelStart.Refused ->
                EngineStart.Refused(TypedFailure.CoreRefused(Operation.Start, started.status))

            is TunnelStart.Started -> {
                val session = SessionId("core-${clock()}")
                live.set(Live(session, started.tunnel))
                keepAuthority(engine, started.tunnel)
                EngineStart.Started(session)
            }
        }
    }

    /**
     * Reads the authority out and stores it, every launch.
     *
     * Unconditional on purpose: storing what was just restored is a no-op write,
     * and there is therefore no branch here to get wrong. Only a tunnel that
     * intercepts has one, and it says so by answering [AuthorityRead.None].
     */
    private suspend fun keepAuthority(engine: EngineConfig, tunnel: NativeTunnel) {
        val intercepts = (engine.filtering as? Filtering.Names)?.interception != null
        if (!intercepts) return

        when (val read = withContext(Dispatchers.IO) { tunnel.authority() }) {
            is AuthorityRead.Present -> authority.save(read.material)
            // Nothing to keep, or the core could not answer. Neither is a reason to
            // discard what is already stored: a failed read is not evidence that the
            // stored material is wrong, and deleting it would cost the user the
            // system dialog they already answered.
            AuthorityRead.None, is AuthorityRead.Failed -> Unit
        }
    }

    override fun events(session: SessionId): Flow<CoreEvent> =
        live.get()?.takeIf { it.session == session }?.tunnel?.events() ?: emptyFlow()

    override suspend fun reload(session: SessionId, lists: List<String>): CoreStatus {
        val tunnel = live.get()?.takeIf { it.session == session }?.tunnel ?: return CoreStatus.Stopped
        return withContext(Dispatchers.IO) { tunnel.reload(lists) }
    }

    override suspend fun authority(session: SessionId): AuthorityRead {
        val tunnel = live.get()?.takeIf { it.session == session }?.tunnel
            ?: return AuthorityRead.Failed(CoreStatus.Stopped)
        return withContext(Dispatchers.IO) { tunnel.authority() }
    }

    override suspend fun stop(session: SessionId, reason: StopReason) {
        // Cleared first, so a second stop finds nothing and the teardown below runs
        // exactly once even though it is itself idempotent.
        val ending = live.getAndUpdate { current -> current?.takeIf { it.session != session } }
        val tunnel = ending?.takeIf { it.session == session }?.tunnel ?: return
        withContext(Dispatchers.IO) { tunnel.shutdown() }
    }
}

/**
 * Where the certificate authority's material is kept between launches.
 *
 * The one thing worth persisting, and only if the tunnel intercepts. Everything
 * else the core learns is a cache with a lifetime already on it, and a stale copy
 * of any of it withholds filtering from a site that has since become interceptable
 * -- which is worse than relearning it and is discovered years later.
 */
internal interface AuthorityStore {
    suspend fun load(): CaMaterial?
    suspend fun save(material: CaMaterial)
}

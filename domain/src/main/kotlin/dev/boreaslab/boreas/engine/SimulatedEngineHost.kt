package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.CoreCounters
import dev.boreaslab.boreas.model.CoreEvent
import dev.boreaslab.boreas.model.CoreStatus
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.Filtering
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.RuleSetSize
import dev.boreaslab.boreas.model.SessionId
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A debug-only host that emits the same event shapes the core does.
 *
 * It exists so the lifecycle and the screens built on it can be exercised where
 * there is no device: an emulator has no traffic to filter and a review has no
 * emulator. Everything it emits is marked simulated at the one place that
 * matters, [EngineHost.start]'s caller, so a generated number can never be read
 * as a measured one.
 */
public class SimulatedEngineHost(
    private val clock: () -> Long = System::currentTimeMillis,
) : EngineHost {

    override val isAvailable: Boolean = true

    override val simulated: Boolean = true

    /** Identity and configuration share one atomic cell across start and event coroutines. */
    private data class Live(val session: SessionId, val config: EngineConfig)

    private val live = AtomicReference<Live?>(null)

    override suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart {
        // Keep Starting visible long enough to exercise its design.
        delay(START_DELAY_MS)
        // Per-start hosts would reset an instance counter and make consecutive sessions
        // indistinguishable, allowing stale status to look current.
        val started = Live(SessionId("sim-${clock()}"), engine)
        live.set(started)
        return EngineStart.Started(started.session)
    }

    override suspend fun stop(session: SessionId, reason: StopReason) {
        delay(STOP_DELAY_MS)
        live.updateAndGet { current -> current?.takeIf { it.session != session } }
    }

    override suspend fun reload(session: SessionId, lists: List<String>): CoreStatus =
        if (live.get()?.session == session) CoreStatus.Ok else CoreStatus.Stopped

    /** A simulated tunnel forges no certificates, so it keeps no authority. */
    override suspend fun authority(session: SessionId): AuthorityRead = AuthorityRead.None

    /** Unknown sessions produce no invented stream at all. */
    override fun events(session: SessionId): Flow<CoreEvent> = flow {
        val config = live.get()?.takeIf { it.session == session } ?: return@flow
        val filtering = config.config.filtering
        if (filtering !is Filtering.Names) return@flow

        // Seeded from the session, so one session's stream is reproducible and two
        // sessions do not read as the same one resumed.
        val random = Random(session.value.hashCode())
        emit(CoreEvent.Reloaded(RuleSetSize(allowed = 0, blocked = filtering.lists.size.toLong(), inspected = 0)))

        while (true) {
            delay(TICK_MS)
            val name = NAMES[random.nextInt(NAMES.size)]
            val blocked = random.nextInt(4) == 0
            emit(
                CoreEvent.Resolved(
                    name = name,
                    rule = if (blocked) "||$name^" else null,
                    blocked = blocked,
                    truncated = false,
                ),
            )
            // A working tunnel reports zeroes, so the simulation reports them too:
            // a screen that only looks right against invented failures is not tested.
            if (random.nextInt(12) == 0) {
                emit(CoreEvent.Counted(CoreCounters(quicSteered = random.nextInt(1, 4).toLong())))
            }
        }
    }

    private companion object {
        const val START_DELAY_MS = 900L
        const val STOP_DELAY_MS = 400L
        const val TICK_MS = 1_400L

        /** Reserved for documentation by RFC 2606, so none of these is a real host. */
        val NAMES = listOf(
            "example.com",
            "www.example.org",
            "cdn.example.net",
            "metrics.example.com",
            "ads.example.net",
        )
    }
}

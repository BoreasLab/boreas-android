package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.RuleProfile
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/** Debug-only host for reviewing lifecycle before A2 links the real engine. */
public class SimulatedEngineHost(
    private val clock: () -> Long = System::currentTimeMillis,
) : EngineHost {

    override val isAvailable: Boolean = true

    /** Identity and configuration share one atomic cell across start and status coroutines. */
    private data class Live(val session: SessionId, val config: EngineConfig)

    private val live = AtomicReference<Live?>(null)

    override suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart {
        // Keep Starting visible long enough to exercise its design.
        delay(START_DELAY_MS)
        // Per-start hosts would reset an instance counter and make consecutive sessions
        // indistinguishable, allowing stale status to look current.
        val started = Live(SessionId("sim-${clock()}"), engine)
        live.set(started)
        return EngineStart.Started(
            session = started.session,
            status = SessionStatus.initial(clock(), engine.upstream, simulated = true),
        )
    }

    override suspend fun stop(session: SessionId, reason: StopReason) {
        delay(STOP_DELAY_MS)
        live.updateAndGet { current -> current?.takeIf { it.session != session } }
    }

    /** Unknown sessions produce no invented status stream. */
    override fun status(session: SessionId): Flow<SessionStatus> = flow {
        val config = live.get()?.takeIf { it.session == session }?.config ?: return@flow
        val random = Random(session.value.hashCode())
        var status = SessionStatus.initial(clock(), config.upstream, simulated = true)
        emit(status)
        while (true) {
            delay(TICK_MS)
            val denied = when (config.profile) {
                RuleProfile.Off -> 0L
                RuleProfile.Standard -> random.nextInt(0, 6).toLong()
                RuleProfile.Strict -> random.nextInt(2, 14).toLong()
            }
            status = status.copy(
                flowsActive = random.nextInt(1, 24).toLong(),
                flowsAccepted = status.flowsAccepted + random.nextInt(3, 40),
                flowsDenied = status.flowsDenied + denied,
                bytesIn = status.bytesIn + random.nextLong(12_000, 900_000),
                bytesOut = status.bytesOut + random.nextLong(4_000, 220_000),
                socketsProtected = status.socketsProtected + random.nextInt(0, 3),
            )
            emit(status)
        }
    }

    private companion object {
        const val START_DELAY_MS = 900L
        const val STOP_DELAY_MS = 400L
        const val TICK_MS = 1_000L
    }
}

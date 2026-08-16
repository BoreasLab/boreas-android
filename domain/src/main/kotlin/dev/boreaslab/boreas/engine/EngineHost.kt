package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.flow.Flow

/**
 * Android shell boundary for shared engine.
 *
 * Not FFI or exported ABI; the real bridge arrives with core A2. Descriptor transfer
 * and protect_socket stay absent until native ownership and callbacks exist. Packets
 * never cross this boundary.
 */
public interface EngineHost {

    /** Checked before consent so unavailable engines never prompt. */
    public val isAvailable: Boolean

    public suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart

    public suspend fun stop(session: SessionId, reason: StopReason)

    /** Latest status only; slow readers skip stale snapshots. */
    public fun status(session: SessionId): Flow<SessionStatus>
}

/** The result of asking the engine to start. A closed set. */
public sealed interface EngineStart {
    public data class Started(val session: SessionId, val status: SessionStatus) : EngineStart
    public data class Refused(val failure: TypedFailure) : EngineStart
}

/** Why a session is being stopped. The contract requires a typed reason. */
public enum class StopReason { UserRequested, ConfigurationChanged, NetworkLost, ServiceDestroyed }

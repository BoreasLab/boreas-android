package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.flow.Flow

/**
 * The Android shell's seam onto the shared engine.
 *
 * This is NOT the FFI and declares no exported symbol. docs/core-contract.md is
 * explicit that the handoff contract "is not yet an exported ABI and does not
 * authorize adding placeholder FFI symbols", so this interface exists only to let
 * the control surface be built and tested against the shape of the contract. The
 * real bridge arrives in A2 alongside the matching core change and its tests.
 *
 * Two operations from the contract's Logical Interface v1 are deliberately absent:
 *
 *  - The descriptor transfer. A ParcelFileDescriptor is an affine resource with
 *    exactly one owner, and there is no native owner to move it to yet. Adding a
 *    parameter for it here would invite a detachFd() call with nothing on the far
 *    side to close it.
 *  - protect_socket. Only the running service can discharge that obligation, and
 *    only native code needs to ask. It is not the UI's to model.
 *
 * Nothing crossing this seam is a packet. Status, counters, and typed errors only.
 */
public interface EngineHost {

    /**
     * Whether a session could be started at all.
     *
     * Checked during validation, before consent, so an unlinked build never asks
     * for VPN permission it cannot use.
     */
    public val isAvailable: Boolean

    public suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart

    public suspend fun stop(session: SessionId, reason: StopReason)

    /**
     * Bounded latest-status stream for a running session.
     *
     * Latest-value only: a slow reader sees the newest snapshot, never a backlog.
     */
    public fun status(session: SessionId): Flow<SessionStatus>
}

/** The result of asking the engine to start. A closed set. */
public sealed interface EngineStart {
    public data class Started(val session: SessionId, val status: SessionStatus) : EngineStart
    public data class Refused(val failure: TypedFailure) : EngineStart
}

/** Why a session is being stopped. The contract requires a typed reason. */
public enum class StopReason { UserRequested, ConfigurationChanged, NetworkLost, ServiceDestroyed }

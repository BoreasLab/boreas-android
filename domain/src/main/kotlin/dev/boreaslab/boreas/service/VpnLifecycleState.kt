package dev.boreaslab.boreas.service

import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.model.TypedFailure
import dev.boreaslab.boreas.model.isRecoverable

/** Closed service-state hierarchy; [Starting] alone may establish TUN. */
public sealed interface VpnLifecycleState {

    public data object Stopped : VpnLifecycleState

    public data object AwaitingConsent : VpnLifecycleState

    public data object Starting : VpnLifecycleState

    public data class Running(
        val session: SessionId,
        val status: SessionStatus,
        /** Policy used by this session, compared with saved policy when needed. */
        val applied: EngineConfig,
    ) : VpnLifecycleState

    public data class Stopping(val session: SessionId) : VpnLifecycleState

    public data class Failed(
        val operation: Operation,
        val failure: TypedFailure,
    ) : VpnLifecycleState {
        public val recoverable: Boolean get() = failure.isRecoverable
    }
}

/** True while a transition owns the session; UI keeps the primary control in place. */
public val VpnLifecycleState.isTransitional: Boolean
    get() = when (this) {
        VpnLifecycleState.AwaitingConsent,
        VpnLifecycleState.Starting,
        -> true
        is VpnLifecycleState.Stopping -> true
        VpnLifecycleState.Stopped,
        is VpnLifecycleState.Running,
        is VpnLifecycleState.Failed,
        -> false
    }

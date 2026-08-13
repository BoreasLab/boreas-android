package dev.boreaslab.boreas.service

import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.model.TypedFailure
import dev.boreaslab.boreas.model.isRecoverable

/**
 * Service state, as a closed sealed hierarchy.
 *
 * AGENTS.md requires this shape and forbids the alternative: "Keep service state a
 * closed Kotlin sealed hierarchy. Do not encode lifecycle state in nullable-field
 * bags or Boolean flags." Every screen eliminates it exhaustively, so a new variant
 * fails the build at each site that must render it.
 *
 * The variants match docs/platform-integration.md exactly. Only [Starting] may
 * establish a TUN and only [Running] may request a controlled configuration change.
 */
sealed interface VpnLifecycleState {

    data object Stopped : VpnLifecycleState

    data object AwaitingConsent : VpnLifecycleState

    data object Starting : VpnLifecycleState

    data class Running(
        val session: SessionId,
        val status: SessionStatus,
        /**
         * The policy this session was started with.
         *
         * Held here so "your saved policy differs from the running one" is derived
         * by comparing two values, rather than tracked as a third piece of state
         * that has to be kept in step with both.
         */
        val applied: EngineConfig,
    ) : VpnLifecycleState

    data class Stopping(val session: SessionId) : VpnLifecycleState

    data class Failed(
        val operation: Operation,
        val failure: TypedFailure,
    ) : VpnLifecycleState {
        val recoverable: Boolean get() = failure.isRecoverable
    }
}

/**
 * True while a transition owns the session.
 *
 * The primary control reads this to show progress in place rather than swapping
 * itself for a spinner, so it never changes size or moves under the reader's thumb.
 */
val VpnLifecycleState.isTransitional: Boolean
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

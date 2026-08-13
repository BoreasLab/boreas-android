package dev.boreaslab.boreas.ui

import androidx.annotation.StringRes
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.TypedFailure

/**
 * Copy for every typed failure.
 *
 * One place, eliminating the closed set exhaustively, so a new failure reason
 * cannot reach a screen without someone writing words for it. Each pair states
 * what happened and what it means; the screen adds the action.
 */
data class FailureCopy(
    @param:StringRes val title: Int,
    @param:StringRes val detail: Int,
)

fun copyFor(failure: TypedFailure): FailureCopy = when (failure) {
    TypedFailure.EngineUnavailable -> FailureCopy(
        R.string.fail_engine_unavailable_title,
        R.string.fail_engine_unavailable_detail,
    )
    TypedFailure.ConsentDenied -> FailureCopy(
        R.string.fail_consent_denied_title,
        R.string.fail_consent_denied_detail,
    )
    TypedFailure.ConsentUnavailable -> FailureCopy(
        R.string.fail_consent_unavailable_title,
        R.string.fail_consent_unavailable_detail,
    )
    TypedFailure.BypassDenied -> FailureCopy(
        R.string.fail_bypass_denied_title,
        R.string.fail_bypass_denied_detail,
    )
    TypedFailure.InterfaceRejected -> FailureCopy(
        R.string.fail_interface_title,
        R.string.fail_interface_detail,
    )
    TypedFailure.RestartRequired -> FailureCopy(
        R.string.fail_restart_required_title,
        R.string.fail_restart_required_detail,
    )
}

@StringRes
fun labelFor(operation: Operation): Int = when (operation) {
    Operation.Start -> R.string.operation_start
    Operation.Stop -> R.string.operation_stop
    Operation.Reconfigure -> R.string.operation_reconfigure
    Operation.NetworkChange -> R.string.operation_network_change
}

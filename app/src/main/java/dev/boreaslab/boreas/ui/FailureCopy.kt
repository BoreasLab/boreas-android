package dev.boreaslab.boreas.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.model.CoreStatus
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.TypedFailure

/**
 * User-facing copy for every typed failure.
 *
 * [detail] is a resource with at most one format argument, filled from [argument].
 * A failure that carries a number the user needs -- the two ABI versions, the
 * status a refusal came back with -- would otherwise have to be described in
 * general terms, which is the kind of message that costs a support round trip.
 */
data class FailureCopy(
    @param:StringRes val title: Int,
    @param:StringRes val detail: Int,
    val argument: String? = null,
)

/** Resolves [FailureCopy.detail], with its argument when the failure carries one. */
@Composable
fun FailureCopy.detailText(): String =
    if (argument == null) stringResource(detail) else stringResource(detail, argument)

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
    is TypedFailure.CoreNotLoaded -> FailureCopy(
        R.string.fail_core_not_loaded_title,
        R.string.fail_core_not_loaded_detail,
        argument = failure.detail,
    )
    is TypedFailure.CoreAbiMismatch -> FailureCopy(
        R.string.fail_core_abi_title,
        R.string.fail_core_abi_detail,
        argument = "${failure.compiled} / ${failure.loaded}",
    )
    is TypedFailure.CoreRefused -> FailureCopy(
        R.string.fail_core_refused_title,
        detailFor(failure.status),
    )
}

/**
 * What a refusal means, in the user's terms rather than the enumeration's.
 *
 * Grouped by what the reader can do about it, which is the only distinction that
 * changes their next action. The statuses that are defects in this program or in
 * the core share one sentence, because "report this" is the whole of the advice.
 */
@StringRes
private fun detailFor(status: CoreStatus): Int = when (status) {
    CoreStatus.Config -> R.string.fail_core_config_detail
    CoreStatus.Egress -> R.string.fail_core_egress_detail
    CoreStatus.Authority -> R.string.fail_core_authority_detail
    CoreStatus.Termination -> R.string.fail_core_termination_detail
    CoreStatus.Io -> R.string.fail_core_io_detail
    CoreStatus.Stopped -> R.string.fail_core_stopped_detail
    CoreStatus.Ok, CoreStatus.NullArgument, CoreStatus.NotUtf8, CoreStatus.Datapath,
    CoreStatus.BufferTooSmall, CoreStatus.Panic, CoreStatus.Unrecognised,
    -> R.string.fail_core_defect_detail
}

@StringRes
fun labelFor(operation: Operation): Int = when (operation) {
    Operation.Start -> R.string.operation_start
    Operation.Stop -> R.string.operation_stop
    Operation.Reload -> R.string.operation_reload
    Operation.Reconfigure -> R.string.operation_reconfigure
    Operation.NetworkChange -> R.string.operation_network_change
}

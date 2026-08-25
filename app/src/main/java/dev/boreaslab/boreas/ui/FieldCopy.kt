package dev.boreaslab.boreas.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.model.FieldProblem

/**
 * One sentence per way a field can be wrong, in one place.
 *
 * Exhaustive over the sum, so a problem added to the domain is a compile error
 * here rather than a field that silently shows nothing.
 */
@Composable
fun copyFor(problem: FieldProblem): String = when (problem) {
    FieldProblem.Required -> stringResource(R.string.error_required)
    FieldProblem.AddressShape -> stringResource(R.string.error_address_shape)
    FieldProblem.AddressRange -> stringResource(R.string.error_address_range)
    FieldProblem.MtuShape -> stringResource(R.string.error_mtu_shape)
    FieldProblem.MtuRange -> stringResource(R.string.error_mtu_range)
    FieldProblem.PortRange -> stringResource(R.string.error_port_range)
    is FieldProblem.DnsShape -> stringResource(R.string.error_dns_shape, problem.entry)
    is FieldProblem.HostShape -> stringResource(R.string.error_host_shape, problem.entry)
}

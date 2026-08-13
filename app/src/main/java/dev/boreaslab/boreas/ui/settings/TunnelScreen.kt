package dev.boreaslab.boreas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasTextField
import dev.boreaslab.boreas.design.component.LoadingRegion
import dev.boreaslab.boreas.design.component.NoticeCard
import dev.boreaslab.boreas.design.component.NoticeTone
import dev.boreaslab.boreas.model.FieldProblem
import dev.boreaslab.boreas.model.TunnelDraft
import dev.boreaslab.boreas.model.TunnelField
import dev.boreaslab.boreas.model.TunnelParse
import dev.boreaslab.boreas.model.problemFor
import dev.boreaslab.boreas.service.AlwaysOn
import dev.boreaslab.boreas.service.VpnLifecycleState
import dev.boreaslab.boreas.ui.PreviewSurface

/**
 * Android addressing for the tunnel interface.
 *
 * One column, labels above the fields, and no placeholder doing a label's job.
 * Entry is written through on every keystroke, so a half-typed address survives the
 * process being killed while the reader is in another app looking one up.
 *
 * Validation reports what is wrong and what would fix it, and it does so for the
 * whole draft at once rather than the first failure only, so a reader fixing two
 * fields is not sent round twice.
 *
 * The fields lock while a session is running, because the interface already holds
 * the values it was given. The lock is stated rather than left for the reader to
 * discover by tapping a field that will not focus.
 */
@Composable
fun TunnelScreen(
    draft: TunnelDraft?,
    parse: TunnelParse?,
    session: VpnLifecycleState,
    alwaysOn: AlwaysOn,
    onChange: (TunnelDraft) -> Unit,
    onOpenVpnSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val locked = session !is VpnLifecycleState.Stopped && session !is VpnLifecycleState.Failed

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Text(
            text = stringResource(R.string.tunnel_intro),
            style = BoreasTheme.type.bodyMd,
            color = BoreasTheme.colors.body,
        )

        AlwaysOnCard(alwaysOn = alwaysOn, onOpenVpnSettings = onOpenVpnSettings)

        if (locked) {
            NoticeCard(
                tone = NoticeTone.Info,
                title = stringResource(R.string.tunnel_locked),
                detail = stringResource(R.string.tunnel_locked_detail),
            )
        }

        if (draft == null) {
            // Reserved space for the one disk read, which normally resolves inside
            // the flash threshold and shows nothing at all.
            LoadingRegion(label = null)
            return@Column
        }

        BoreasTextField(
            label = stringResource(R.string.tunnel_address),
            value = draft.address,
            onValueChange = { onChange(draft.copy(address = it)) },
            help = stringResource(R.string.tunnel_address_help),
            error = parse?.problemFor(TunnelField.Address)?.let { copyFor(it) },
            enabled = !locked,
            keyboardType = KeyboardType.Decimal,
        )

        BoreasTextField(
            label = stringResource(R.string.tunnel_mtu),
            value = draft.mtu,
            onValueChange = { onChange(draft.copy(mtu = it)) },
            help = stringResource(R.string.tunnel_mtu_help),
            error = parse?.problemFor(TunnelField.Mtu)?.let { copyFor(it) },
            enabled = !locked,
            keyboardType = KeyboardType.Number,
        )

        BoreasTextField(
            label = stringResource(R.string.tunnel_dns),
            value = draft.dns,
            onValueChange = { onChange(draft.copy(dns = it)) },
            help = stringResource(R.string.tunnel_dns_help),
            error = parse?.problemFor(TunnelField.Dns)?.let { copyFor(it) },
            enabled = !locked,
            singleLine = false,
            keyboardType = KeyboardType.Decimal,
        )
    }
}

/** Every field problem, turned into a sentence that names the fix. */
@Composable
private fun copyFor(problem: FieldProblem): String = when (problem) {
    FieldProblem.Required -> stringResource(R.string.error_required)
    FieldProblem.AddressShape -> stringResource(R.string.error_address_shape)
    FieldProblem.AddressRange -> stringResource(R.string.error_address_range)
    FieldProblem.MtuShape -> stringResource(R.string.error_mtu_shape)
    FieldProblem.MtuRange -> stringResource(R.string.error_mtu_range)
    is FieldProblem.DnsShape -> stringResource(R.string.error_dns_shape, problem.entry)
}

@Preview(name = "Tunnel", showBackground = true)
@Composable
private fun TunnelPreview() = PreviewSurface {
    TunnelScreen(
        draft = TunnelDraft(),
        parse = TunnelParse.of(TunnelDraft(), emptySet()),
        session = VpnLifecycleState.Stopped,
        alwaysOn = AlwaysOn.Off,
        onChange = {},
        onOpenVpnSettings = {},
    )
}

@Preview(name = "Tunnel: invalid entry", showBackground = true)
@Composable
private fun TunnelInvalidPreview() = PreviewSurface {
    TunnelScreen(
        draft = TunnelDraft(address = "10.0.0", mtu = "40"),
        parse = TunnelParse.Invalid(
            mapOf(
                TunnelField.Address to FieldProblem.AddressShape,
                TunnelField.Mtu to FieldProblem.MtuRange,
            ),
        ),
        session = VpnLifecycleState.Stopped,
        alwaysOn = AlwaysOn.On(lockdown = true),
        onChange = {},
        onOpenVpnSettings = {},
    )
}

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

/** Tunnel inputs; raw drafts persist, validation covers all fields, and active sessions lock them. */
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
            // Preserve layout while the initial draft loads.
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

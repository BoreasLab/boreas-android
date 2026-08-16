package dev.boreaslab.boreas.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasIcons
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasButton
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.ButtonVariant
import dev.boreaslab.boreas.service.AlwaysOn
import dev.boreaslab.boreas.ui.PreviewSurface

private data class AlwaysOnCopy(@param:StringRes val state: Int, @param:StringRes val detail: Int)

private fun copyFor(alwaysOn: AlwaysOn): AlwaysOnCopy = when (alwaysOn) {
    AlwaysOn.Unobserved -> AlwaysOnCopy(
        R.string.always_on_state_unobserved,
        R.string.always_on_state_unobserved_detail,
    )
    AlwaysOn.Off -> AlwaysOnCopy(
        R.string.always_on_state_off,
        R.string.always_on_state_off_detail,
    )
    // Keep lockdown as an explicit case in the exhaustive mapping.
    is AlwaysOn.On if alwaysOn.lockdown -> AlwaysOnCopy(
        R.string.always_on_state_lockdown,
        R.string.always_on_state_lockdown_detail,
    )
    is AlwaysOn.On -> AlwaysOnCopy(
        R.string.always_on_state_on,
        R.string.always_on_state_on_detail,
    )
}

/** Reports Android-owned always-on state; unavailable settings produce no dead control. */
@Composable
fun AlwaysOnCard(
    alwaysOn: AlwaysOn,
    onOpenVpnSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val copy = copyFor(alwaysOn)

    BoreasCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.always_on_title),
            style = BoreasTheme.type.titleSm,
            color = BoreasTheme.colors.muted,
        )
        Text(
            text = stringResource(copy.state),
            style = BoreasTheme.type.displaySm,
            color = if (alwaysOn is AlwaysOn.On) {
                BoreasTheme.colors.primary
            } else {
                BoreasTheme.colors.ink
            },
            modifier = Modifier.padding(top = Space.xxs),
        )
        Text(
            text = stringResource(copy.detail),
            style = BoreasTheme.type.bodySm,
            color = BoreasTheme.colors.body,
            modifier = Modifier.padding(top = Space.xs),
        )
        Text(
            text = stringResource(R.string.always_on_explain),
            style = BoreasTheme.type.bodySm,
            color = BoreasTheme.colors.muted,
            modifier = Modifier.padding(top = Space.xs),
        )
        if (onOpenVpnSettings != null) {
            BoreasButton(
                label = stringResource(R.string.always_on_open_settings),
                onClick = onOpenVpnSettings,
                variant = ButtonVariant.Secondary,
                icon = BoreasIcons.Globe,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}

@Preview(name = "Always-on: off", showBackground = true)
@Composable
private fun AlwaysOnOffPreview() = PreviewSurface { AlwaysOnCard(AlwaysOn.Off, {}) }

@Preview(name = "Always-on: lockdown", showBackground = true)
@Composable
private fun AlwaysOnLockdownPreview() =
    PreviewSurface(dark = true) { AlwaysOnCard(AlwaysOn.On(lockdown = true), {}) }

package dev.boreaslab.boreas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasIcons
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.CardSurface
import dev.boreaslab.boreas.design.component.NavigationRow
import dev.boreaslab.boreas.design.component.RowDivider
import dev.boreaslab.boreas.ui.Destination
import dev.boreaslab.boreas.ui.PreviewSurface

@Composable
fun SettingsScreen(
    onOpen: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
            NavigationRow(
                title = stringResource(R.string.settings_tunnel),
                detail = stringResource(R.string.settings_tunnel_detail),
                leading = BoreasIcons.Globe,
                onClick = { onOpen(Destination.Detail.Tunnel) },
            )
            RowDivider()
            NavigationRow(
                title = stringResource(R.string.settings_apps),
                detail = stringResource(R.string.settings_apps_detail),
                leading = BoreasIcons.Apps,
                onClick = { onOpen(Destination.Detail.Apps) },
            )
            RowDivider()
            NavigationRow(
                title = stringResource(R.string.settings_certificate),
                detail = stringResource(R.string.settings_certificate_detail),
                leading = BoreasIcons.Certificate,
                onClick = { onOpen(Destination.Detail.Certificate) },
            )
        }

        BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
            NavigationRow(
                title = stringResource(R.string.settings_diagnostics),
                detail = stringResource(R.string.settings_diagnostics_detail),
                leading = BoreasIcons.Document,
                onClick = { onOpen(Destination.Detail.Diagnostics) },
            )
            RowDivider()
            NavigationRow(
                title = stringResource(R.string.settings_about),
                detail = stringResource(R.string.settings_about_detail),
                leading = BoreasIcons.Info,
                onClick = { onOpen(Destination.Detail.About) },
            )
        }
    }
}

@Preview(name = "Settings", showBackground = true)
@Composable
private fun SettingsPreview() = PreviewSurface { SettingsScreen(onOpen = {}) }

@Preview(name = "Settings: dark", showBackground = true)
@Composable
private fun SettingsDarkPreview() = PreviewSurface(dark = true) { SettingsScreen(onOpen = {}) }

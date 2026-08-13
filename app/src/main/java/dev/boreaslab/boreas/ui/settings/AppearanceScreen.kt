package dev.boreaslab.boreas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.ThemeChoice
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.CardSurface
import dev.boreaslab.boreas.design.component.ChoiceRow
import dev.boreaslab.boreas.design.component.RowDivider
import dev.boreaslab.boreas.ui.PreviewSurface
import dev.boreaslab.boreas.ui.SettingsGroup

/** Theme selection. Persisted, so the choice survives the app being killed. */
@Composable
fun AppearanceScreen(
    choice: ThemeChoice,
    onChoose: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        SettingsGroup(title = stringResource(R.string.theme_heading)) {
            BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
                ThemeChoice.entries.forEachIndexed { index, option ->
                    if (index > 0) RowDivider()
                    ChoiceRow(
                        title = stringResource(
                            when (option) {
                                ThemeChoice.System -> R.string.theme_system
                                ThemeChoice.Light -> R.string.theme_light
                                ThemeChoice.Dark -> R.string.theme_dark
                            },
                        ),
                        selected = choice == option,
                        onSelect = { onChoose(option) },
                    )
                }
            }
        }
    }
}

@Preview(name = "Appearance", showBackground = true)
@Composable
private fun AppearancePreview() = PreviewSurface {
    AppearanceScreen(ThemeChoice.System, {})
}

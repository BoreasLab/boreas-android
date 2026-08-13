package dev.boreaslab.boreas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.boreaslab.boreas.design.component.CardSurface
import dev.boreaslab.boreas.design.component.ContainerState
import dev.boreaslab.boreas.design.component.EmptyState
import dev.boreaslab.boreas.design.component.MetricRow
import dev.boreaslab.boreas.design.component.NoticeCard
import dev.boreaslab.boreas.design.component.NoticeTone
import dev.boreaslab.boreas.design.component.RowDivider
import dev.boreaslab.boreas.design.component.StateContainer
import dev.boreaslab.boreas.design.component.SwitchRow
import dev.boreaslab.boreas.service.TransitionRecord
import dev.boreaslab.boreas.service.VpnLifecycleState
import dev.boreaslab.boreas.ui.PreviewSurface
import dev.boreaslab.boreas.ui.SettingsGroup
import dev.boreaslab.boreas.ui.copyFor
import dev.boreaslab.boreas.ui.formatClockTime

/**
 * Lifecycle transitions, newest first.
 *
 * Clearing is reversible, so it happens immediately and offers undo rather than
 * asking a confirmation the reader would learn to dismiss without reading.
 */
@Composable
fun DiagnosticsScreen(
    records: List<TransitionRecord>,
    simulationAvailable: Boolean,
    simulationEnabled: Boolean,
    onSimulationChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onRestore: (List<TransitionRecord>) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cleared by remember { mutableStateOf<List<TransitionRecord>?>(null) }

    val container: ContainerState<List<TransitionRecord>> =
        if (records.isEmpty()) ContainerState.Empty else ContainerState.Ready(records)

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Text(
            text = stringResource(R.string.diagnostics_intro),
            style = BoreasTheme.type.bodyMd,
            color = BoreasTheme.colors.body,
        )

        if (simulationAvailable) {
            SettingsGroup(title = stringResource(R.string.simulation_setting)) {
                BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
                    SwitchRow(
                        title = stringResource(R.string.simulation_setting),
                        detail = stringResource(R.string.simulation_setting_detail),
                        checked = simulationEnabled,
                        onCheckedChange = onSimulationChange,
                    )
                }
            }
        }

        val undo = cleared
        if (undo != null) {
            NoticeCard(
                tone = NoticeTone.Info,
                title = stringResource(R.string.diagnostics_cleared),
                actionLabel = stringResource(R.string.action_undo),
                onAction = {
                    onRestore(undo)
                    cleared = null
                },
            )
        }

        StateContainer(
            state = container,
            empty = {
                EmptyState(
                    icon = BoreasIcons.Document,
                    title = stringResource(R.string.diagnostics_empty_title),
                    detail = stringResource(R.string.diagnostics_empty_detail),
                )
            },
        ) { list ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                BoreasCard(modifier = Modifier.fillMaxWidth()) {
                    list.forEachIndexed { index, record ->
                        if (index > 0) RowDivider()
                        MetricRow(
                            label = describe(record.state),
                            value = formatClockTime(record.atMillis),
                        )
                    }
                }
                // `map` is inline, so the composable lookup is legal inside it;
                // joinToString's transform is not, so the join happens after.
                val transcript = list
                    .map { "${formatClockTime(it.atMillis)}  ${describe(it.state)}" }
                    .joinToString("\n")
                var copied by remember { mutableStateOf(false) }

                BoreasButton(
                    label = stringResource(
                        if (copied) R.string.diagnostics_copied else R.string.diagnostics_copy,
                    ),
                    onClick = {
                        onCopy(transcript)
                        copied = true
                    },
                    variant = ButtonVariant.Secondary,
                    icon = if (copied) BoreasIcons.Check else BoreasIcons.Document,
                )
                BoreasButton(
                    label = stringResource(R.string.diagnostics_clear),
                    onClick = {
                        cleared = list
                        onClear()
                    },
                    variant = ButtonVariant.Secondary,
                )
            }
        }
    }
}

/** One line per state, naming the reason when there is one. */
@Composable
private fun describe(state: VpnLifecycleState): String = when (state) {
    VpnLifecycleState.Stopped -> stringResource(R.string.state_stopped)
    VpnLifecycleState.AwaitingConsent -> stringResource(R.string.state_awaiting_consent)
    VpnLifecycleState.Starting -> stringResource(R.string.state_starting)
    is VpnLifecycleState.Running -> stringResource(R.string.state_running)
    is VpnLifecycleState.Stopping -> stringResource(R.string.state_stopping)
    is VpnLifecycleState.Failed -> stringResource(copyFor(state.failure).title)
}

@Preview(name = "Diagnostics: empty", showBackground = true)
@Composable
private fun DiagnosticsEmptyPreview() = PreviewSurface {
    DiagnosticsScreen(emptyList(), true, false, {}, {}, {}, {})
}

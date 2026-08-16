package dev.boreaslab.boreas.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasIcons
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.ContainerState
import dev.boreaslab.boreas.design.component.EmptyState
import dev.boreaslab.boreas.design.component.MetricRow
import dev.boreaslab.boreas.design.component.NoticeCard
import dev.boreaslab.boreas.design.component.NoticeTone
import dev.boreaslab.boreas.design.component.RowDivider
import dev.boreaslab.boreas.design.component.StateContainer
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.model.UpstreamRoute
import dev.boreaslab.boreas.service.VpnLifecycleState
import dev.boreaslab.boreas.ui.PreviewSurface
import dev.boreaslab.boreas.ui.formatBytes
import dev.boreaslab.boreas.ui.formatClockTime
import dev.boreaslab.boreas.ui.formatCount

/** Engine counters only; packet payloads never cross the boundary. */
@Composable
fun ActivityScreen(
    state: VpnLifecycleState,
    modifier: Modifier = Modifier,
) {
    val container: ContainerState<VpnLifecycleState.Running> = when (state) {
        is VpnLifecycleState.Running -> ContainerState.Ready(state)
        VpnLifecycleState.Starting, VpnLifecycleState.AwaitingConsent -> ContainerState.Loading
        is VpnLifecycleState.Stopping -> ContainerState.Loading
        VpnLifecycleState.Stopped -> ContainerState.Empty
        is VpnLifecycleState.Failed -> ContainerState.Empty
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = stringResource(R.string.activity_title),
            style = BoreasTheme.type.titleMd,
            color = BoreasTheme.colors.body,
        )

        StateContainer(
            state = container,
            loadingLabel = stringResource(R.string.activity_loading),
            empty = {
                EmptyState(
                    icon = BoreasIcons.Activity,
                    title = stringResource(R.string.activity_empty_title),
                    detail = stringResource(R.string.activity_empty_detail),
                )
            },
        ) { running ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                if (running.status.simulated) {
                    NoticeCard(
                        tone = NoticeTone.Warning,
                        title = stringResource(R.string.simulation_title),
                        detail = stringResource(R.string.simulation_detail),
                    )
                }
                CounterGroups(running.status)
            }
        }
    }
}

@Composable
private fun CounterGroups(status: SessionStatus, modifier: Modifier = Modifier) {
    val since = stringResource(R.string.activity_since, formatClockTime(status.startedAtMillis))

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        CounterCard(title = stringResource(R.string.activity_group_flows), footnote = since) {
            MetricRow(
                label = stringResource(R.string.metric_flows_active),
                value = formatCount(status.flowsActive),
            )
            RowDivider()
            MetricRow(
                label = stringResource(R.string.metric_flows_accepted),
                value = formatCount(status.flowsAccepted),
            )
            RowDivider()
            MetricRow(
                label = stringResource(R.string.metric_flows_denied),
                value = formatCount(status.flowsDenied),
                valueColor = BoreasTheme.colors.primary,
            )
        }

        CounterCard(title = stringResource(R.string.activity_group_transfer), footnote = since) {
            MetricRow(
                label = stringResource(R.string.metric_bytes_in),
                value = formatBytes(status.bytesIn),
            )
            RowDivider()
            MetricRow(
                label = stringResource(R.string.metric_bytes_out),
                value = formatBytes(status.bytesOut),
            )
        }

        CounterCard(title = stringResource(R.string.activity_group_upstream), footnote = since) {
            MetricRow(
                label = stringResource(R.string.metric_upstream_kind),
                value = when (status.upstream) {
                    UpstreamRoute.Direct -> stringResource(R.string.policy_egress_direct)
                    UpstreamRoute.Proxy -> stringResource(R.string.policy_egress_proxy)
                },
            )
            RowDivider()
            MetricRow(
                label = stringResource(R.string.metric_upstream_protected),
                value = formatCount(status.socketsProtected),
            )
        }
    }
}

@Composable
private fun CounterCard(
    title: String,
    footnote: String,
    content: @Composable () -> Unit,
) {
    BoreasCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = BoreasTheme.type.titleSm, color = BoreasTheme.colors.ink)
        Text(
            text = footnote,
            style = BoreasTheme.type.label,
            color = BoreasTheme.colors.muted,
            modifier = Modifier.padding(bottom = Space.sm, top = Space.xxs),
        )
        content()
    }
}

@Preview(name = "Activity: no session", showBackground = true)
@Composable
private fun ActivityEmptyPreview() = PreviewSurface {
    ActivityScreen(VpnLifecycleState.Stopped)
}

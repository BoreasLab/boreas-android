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
import androidx.compose.ui.text.style.TextOverflow
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
import dev.boreaslab.boreas.model.CoreCounters
import dev.boreaslab.boreas.model.ResolvedName
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.service.VpnLifecycleState
import dev.boreaslab.boreas.ui.PreviewSurface
import dev.boreaslab.boreas.ui.formatClockTime
import dev.boreaslab.boreas.ui.formatCount

/**
 * What the core reported, and nothing else.
 *
 * There are no byte or flow counters here because the ABI has none. Every number
 * on this screen is folded from the event stream, and the stream is the whole
 * diagnostic surface by design.
 *
 * The counters card is absent while everything is zero. That is not hiding a
 * problem: a working tunnel reports zeroes, so a card of zeroes says only that
 * nothing has gone wrong, at the cost of the space where something wrong would be
 * noticed.
 */
@Composable
fun ActivityScreen(
    state: VpnLifecycleState,
    resolutions: List<ResolvedName>,
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
                NamesCard(running.status)
                if (!running.status.counters.quiet) CountersCard(running.status.counters)
                ResolutionsCard(resolutions)
            }
        }
    }
}

@Composable
private fun NamesCard(status: SessionStatus, modifier: Modifier = Modifier) {
    val since = stringResource(R.string.activity_since, formatClockTime(status.startedAtMillis))

    CounterCard(title = stringResource(R.string.activity_group_names), footnote = since, modifier) {
        MetricRow(
            label = stringResource(R.string.metric_names_allowed),
            value = formatCount(status.namesAllowed),
        )
        RowDivider()
        MetricRow(
            label = stringResource(R.string.metric_names_blocked),
            value = formatCount(status.namesBlocked),
            valueColor = BoreasTheme.colors.primary,
        )

        // Absent until a reload reports it, which is honest: nothing has said how
        // many rules are in force, so nothing here claims to know.
        status.rules?.let { rules ->
            RowDivider()
            MetricRow(
                label = stringResource(R.string.metric_rules_blocked),
                value = formatCount(rules.blocked),
            )
            RowDivider()
            MetricRow(
                label = stringResource(R.string.metric_hosts_inspected),
                value = formatCount(rules.inspected),
            )
        }
    }
}

/**
 * Shown only when something is non-zero.
 *
 * Every field is something that went wrong or was refused, so any of them being
 * visible is itself the signal, and no reader has to know which numbers are
 * supposed to be small.
 */
@Composable
private fun CountersCard(counters: CoreCounters, modifier: Modifier = Modifier) {
    CounterCard(
        title = stringResource(R.string.activity_group_counters),
        footnote = stringResource(R.string.activity_counters_footnote),
        modifier = modifier,
    ) {
        val rows = listOf(
            R.string.counter_datagrams_dropped to counters.datagramsDropped,
            R.string.counter_packets_rejected to counters.packetsRejected,
            R.string.counter_quic_steered to counters.quicSteered,
            R.string.counter_paths_reported to counters.pathsReported,
            R.string.counter_events_lost to counters.eventsLost,
            R.string.counter_tasks_panicked to counters.tasksPanicked,
        ).filter { (_, value) -> value > 0 }

        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) RowDivider()
            MetricRow(label = stringResource(label), value = formatCount(value))
        }
    }
}

/** The newest answered questions, which is what a "what did it block" screen is. */
@Composable
private fun ResolutionsCard(entries: List<ResolvedName>, modifier: Modifier = Modifier) {
    BoreasCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.activity_group_resolutions),
            style = BoreasTheme.type.titleSm,
            color = BoreasTheme.colors.ink,
        )

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.activity_resolutions_empty),
                style = BoreasTheme.type.bodySm,
                color = BoreasTheme.colors.muted,
                modifier = Modifier.padding(top = Space.xs),
            )
            return@BoreasCard
        }

        entries.take(RESOLUTIONS_SHOWN).forEachIndexed { index, entry ->
            if (index > 0) RowDivider()
            MetricRow(
                label = entry.name,
                value = stringResource(
                    if (entry.blocked) R.string.activity_blocked else R.string.activity_allowed,
                ),
                valueColor = if (entry.blocked) {
                    BoreasTheme.colors.primary
                } else {
                    BoreasTheme.colors.muted
                },
            )
            entry.rule?.let { rule ->
                Text(
                    // A rule the core had more of than the buffer held says so, so a
                    // shortened rule is never read as the whole rule.
                    text = if (entry.truncated) "$rule…" else rule,
                    style = BoreasTheme.type.code,
                    color = BoreasTheme.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CounterCard(
    title: String,
    footnote: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoreasCard(modifier = modifier.fillMaxWidth()) {
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

/** Enough to read at a glance; the controller keeps more than this. */
private const val RESOLUTIONS_SHOWN = 25

@Preview(name = "Activity: no session", showBackground = true)
@Composable
private fun ActivityEmptyPreview() = PreviewSurface {
    ActivityScreen(VpnLifecycleState.Stopped, emptyList())
}

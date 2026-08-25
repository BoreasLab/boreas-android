package dev.boreaslab.boreas.ui.shield

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.LocalReducedMotion
import dev.boreaslab.boreas.design.Motion
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasButton
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.ButtonVariant
import dev.boreaslab.boreas.design.component.CardSurface
import dev.boreaslab.boreas.design.component.NoticeCard
import dev.boreaslab.boreas.design.component.NoticeTone
import dev.boreaslab.boreas.design.component.Overline
import dev.boreaslab.boreas.design.component.SessionMetric
import dev.boreaslab.boreas.model.Filtering
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.TypedFailure
import dev.boreaslab.boreas.service.AlwaysOn
import dev.boreaslab.boreas.service.VpnLifecycleState
import dev.boreaslab.boreas.ui.PreviewSurface
import dev.boreaslab.boreas.ui.copyFor
import dev.boreaslab.boreas.ui.detailText
import dev.boreaslab.boreas.ui.formatCount
import dev.boreaslab.boreas.ui.formatDuration
import dev.boreaslab.boreas.ui.labelFor
import kotlinx.coroutines.delay

@Composable
fun ShieldScreen(
    state: VpnLifecycleState,
    /** The stored policy differs from what the running session is applying. */
    policyPending: Boolean,
    alwaysOn: AlwaysOn,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            SessionCard(state, alwaysOn)

            if (state is VpnLifecycleState.Running && state.status.simulated) {
                NoticeCard(
                    tone = NoticeTone.Warning,
                    title = stringResource(R.string.simulation_title),
                    detail = stringResource(R.string.simulation_detail),
                )
            }

            if (state is VpnLifecycleState.Failed) {
                val copy = copyFor(state.failure)
                NoticeCard(
                    tone = NoticeTone.Danger,
                    title = stringResource(copy.title),
                    detail = copy.detailText() + " " + stringResource(
                        R.string.failed_context,
                        stringResource(labelFor(state.operation)),
                    ),
                )
            }

            if (state is VpnLifecycleState.Running && policyPending) {
                NoticeCard(
                    tone = NoticeTone.Warning,
                    title = stringResource(R.string.policy_pending_title),
                    detail = stringResource(R.string.policy_pending_detail),
                )
            }
        }

        PrimaryControl(state = state, onStart = onStart, onStop = onStop)
    }
}

@Composable
private fun SessionCard(
    state: VpnLifecycleState,
    alwaysOn: AlwaysOn,
    modifier: Modifier = Modifier,
) {
    val colors = BoreasTheme.colors
    val reduced = LocalReducedMotion.current

    val headline = when (state) {
        VpnLifecycleState.Stopped -> stringResource(R.string.state_stopped)
        VpnLifecycleState.AwaitingConsent -> stringResource(R.string.state_awaiting_consent)
        VpnLifecycleState.Starting -> stringResource(R.string.state_starting)
        is VpnLifecycleState.Running -> stringResource(R.string.state_running)
        is VpnLifecycleState.Stopping -> stringResource(R.string.state_stopping)
        is VpnLifecycleState.Failed -> stringResource(R.string.state_failed)
    }
    val detail = when (state) {
        VpnLifecycleState.Stopped -> stringResource(R.string.state_stopped_detail)
        VpnLifecycleState.AwaitingConsent -> stringResource(R.string.state_awaiting_consent_detail)
        VpnLifecycleState.Starting -> stringResource(R.string.state_starting_detail)
        is VpnLifecycleState.Running -> null
        is VpnLifecycleState.Stopping -> stringResource(R.string.state_stopping_detail)
        is VpnLifecycleState.Failed -> stringResource(copyFor(state.failure).title)
    }

    // Accent marks only Running, preserving its meaning as "this is on".
    val headlineColor by animateColorAsState(
        targetValue = if (state is VpnLifecycleState.Running) {
            colors.sessionPrimary
        } else {
            colors.sessionInk
        },
        animationSpec = Motion.state(reduced),
        label = "sessionHeadline",
    )

    BoreasCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        surface = CardSurface.Session,
        padding = Space.lg,
    ) {
        Overline(
            text = stringResource(R.string.session_heading).uppercase(),
            color = colors.sessionMuted,
        )
        Text(
            text = headline,
            style = BoreasTheme.type.displayLg,
            color = headlineColor,
            modifier = Modifier.padding(top = Space.sm),
        )
        if (detail != null) {
            Text(
                text = detail,
                style = BoreasTheme.type.bodyMd,
                color = colors.sessionMuted,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
        if (state is VpnLifecycleState.Running) {
            RunningDetail(state, modifier = Modifier.padding(top = Space.lg))
        }

        // Show only On; Off is the default and Unobserved belongs to Settings.
        if (alwaysOn is AlwaysOn.On) {
            SessionMetric(
                label = stringResource(R.string.shield_always_on),
                value = stringResource(
                    if (alwaysOn.lockdown) {
                        R.string.always_on_state_lockdown
                    } else {
                        R.string.always_on_state_on
                    },
                ),
                modifier = Modifier.padding(top = Space.md),
            )
        }
    }
}

@Composable
private fun RunningDetail(state: VpnLifecycleState.Running, modifier: Modifier = Modifier) {
    var now by remember(state.session) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.session) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        SessionMetric(
            label = stringResource(R.string.session_uptime),
            value = formatDuration(now - state.status.startedAtMillis),
            modifier = Modifier.weight(1f),
        )
        SessionMetric(
            label = stringResource(R.string.session_tier),
            value = stringResource(tierLabel(state.applied.filtering)),
            modifier = Modifier.weight(1f),
        )
        SessionMetric(
            label = stringResource(R.string.metric_names_blocked),
            value = formatCount(state.status.namesBlocked),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The one action, and what it is at each point in the lifecycle.
 *
 * During a transition the control offers a way out rather than going inert, so a
 * consent prompt that never returns or a start that hangs is not a dead end. The
 * label stays in place while the indicator runs, so the control does not resize
 * under a thumb already moving toward it.
 */
@Composable
private fun PrimaryControl(
    state: VpnLifecycleState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        VpnLifecycleState.Stopped, is VpnLifecycleState.Failed -> BoreasButton(
            label = stringResource(R.string.action_start),
            onClick = onStart,
            variant = ButtonVariant.Primary,
            modifier = modifier.fillMaxWidth(),
        )

        VpnLifecycleState.AwaitingConsent, VpnLifecycleState.Starting -> BoreasButton(
            label = stringResource(R.string.action_cancel),
            onClick = onStop,
            variant = ButtonVariant.Secondary,
            loading = true,
            modifier = modifier.fillMaxWidth(),
        )

        is VpnLifecycleState.Running -> BoreasButton(
            label = stringResource(R.string.action_stop),
            onClick = onStop,
            variant = ButtonVariant.Secondary,
            modifier = modifier.fillMaxWidth(),
        )

        is VpnLifecycleState.Stopping -> BoreasButton(
            label = stringResource(R.string.action_stop),
            onClick = {},
            variant = ButtonVariant.Secondary,
            enabled = false,
            loading = true,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/**
 * Which tier this session is running, named by what it does rather than by a
 * number nobody outside the contract would recognise.
 */
private fun tierLabel(filtering: Filtering): Int = when (filtering) {
    Filtering.Off -> R.string.tier_off
    is Filtering.Names ->
        if (filtering.interception == null) R.string.tier_names else R.string.tier_requests
}

@Preview(name = "Shield: stopped", showBackground = true)
@Composable
private fun ShieldStoppedPreview() = PreviewSurface {
    ShieldScreen(VpnLifecycleState.Stopped, policyPending = false, AlwaysOn.Off, {}, {})
}

@Preview(name = "Shield: core not loaded", showBackground = true)
@Composable
private fun ShieldFailedPreview() = PreviewSurface {
    ShieldScreen(
        VpnLifecycleState.Failed(
            Operation.Start,
            TypedFailure.CoreNotLoaded("library \"libc++_shared.so\" not found"),
        ),
        policyPending = false,
        AlwaysOn.On(lockdown = true),
        {},
        {},
    )
}

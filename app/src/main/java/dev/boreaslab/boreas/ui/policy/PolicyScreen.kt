package dev.boreaslab.boreas.ui.policy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.CardSurface
import dev.boreaslab.boreas.design.component.ChoiceRow
import dev.boreaslab.boreas.design.component.NoticeCard
import dev.boreaslab.boreas.design.component.NoticeTone
import dev.boreaslab.boreas.design.component.RowDivider
import dev.boreaslab.boreas.design.component.SwitchRow
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.RuleProfile
import dev.boreaslab.boreas.model.UpstreamRoute
import dev.boreaslab.boreas.service.VpnLifecycleState
import dev.boreaslab.boreas.ui.PreviewSurface
import dev.boreaslab.boreas.ui.SettingsGroup

/**
 * What the engine should do with what it sees.
 *
 * The engine owns every decision here; this screen only chooses the values it will
 * be handed. Nothing on this screen filters anything itself.
 *
 * Choices save the moment they are made, so there is no submit button to lose work
 * against. What a running session cannot pick up without restarting is stated at
 * the top, next to the one action that applies it.
 */
@Composable
fun PolicyScreen(
    config: EngineConfig,
    session: VpnLifecycleState,
    certificateInstalled: Boolean,
    onProfile: (RuleProfile) -> Unit,
    onInspectTls: (Boolean) -> Unit,
    onUpstream: (UpstreamRoute) -> Unit,
    onOpenCertificate: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = session is VpnLifecycleState.Running && session.applied != config

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Text(
            text = stringResource(R.string.policy_intro),
            style = BoreasTheme.type.bodyMd,
            color = BoreasTheme.colors.body,
        )

        if (pending) {
            NoticeCard(
                tone = NoticeTone.Warning,
                title = stringResource(R.string.policy_pending_title),
                detail = stringResource(R.string.policy_pending_detail),
                actionLabel = stringResource(R.string.policy_apply),
                onAction = onRestart,
            )
        }

        SettingsGroup(title = stringResource(R.string.policy_group_filtering)) {
            BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
                RuleProfile.entries.forEachIndexed { index, profile ->
                    if (index > 0) RowDivider()
                    ChoiceRow(
                        title = stringResource(profileTitle(profile)),
                        detail = stringResource(profileDetail(profile)),
                        selected = config.profile == profile,
                        onSelect = { onProfile(profile) },
                    )
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.policy_group_inspection)) {
            BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
                SwitchRow(
                    title = stringResource(R.string.policy_tls),
                    detail = stringResource(R.string.policy_tls_detail),
                    checked = config.inspectTls && certificateInstalled,
                    enabled = certificateInstalled,
                    onCheckedChange = onInspectTls,
                )
            }
            // The control stays visible and its blocker is named next to it, rather
            // than the row vanishing or going quiet with no reason given.
            if (!certificateInstalled) {
                NoticeCard(
                    tone = NoticeTone.Info,
                    title = stringResource(R.string.policy_tls_blocked),
                    actionLabel = stringResource(R.string.certificate_install),
                    onAction = onOpenCertificate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.policy_group_egress)) {
            BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
                UpstreamRoute.entries.forEachIndexed { index, route ->
                    if (index > 0) RowDivider()
                    ChoiceRow(
                        title = stringResource(upstreamTitle(route)),
                        detail = stringResource(upstreamDetail(route)),
                        selected = config.upstream == route,
                        onSelect = { onUpstream(route) },
                    )
                }
            }
        }
    }
}

private fun profileTitle(profile: RuleProfile) = when (profile) {
    RuleProfile.Off -> R.string.policy_profile_off
    RuleProfile.Standard -> R.string.policy_profile_standard
    RuleProfile.Strict -> R.string.policy_profile_strict
}

private fun profileDetail(profile: RuleProfile) = when (profile) {
    RuleProfile.Off -> R.string.policy_profile_off_detail
    RuleProfile.Standard -> R.string.policy_profile_standard_detail
    RuleProfile.Strict -> R.string.policy_profile_strict_detail
}

private fun upstreamTitle(route: UpstreamRoute) = when (route) {
    UpstreamRoute.Direct -> R.string.policy_egress_direct
    UpstreamRoute.Proxy -> R.string.policy_egress_proxy
}

private fun upstreamDetail(route: UpstreamRoute) = when (route) {
    UpstreamRoute.Direct -> R.string.policy_egress_direct_detail
    UpstreamRoute.Proxy -> R.string.policy_egress_proxy_detail
}

@Preview(name = "Policy", showBackground = true)
@Composable
private fun PolicyPreview() = PreviewSurface {
    PolicyScreen(
        config = EngineConfig(),
        session = VpnLifecycleState.Stopped,
        certificateInstalled = false,
        onProfile = {},
        onInspectTls = {},
        onUpstream = {},
        onOpenCertificate = {},
        onRestart = {},
    )
}

@Preview(name = "Policy: dark", showBackground = true)
@Composable
private fun PolicyDarkPreview() = PreviewSurface(dark = true) {
    PolicyScreen(
        config = EngineConfig(),
        session = VpnLifecycleState.Stopped,
        certificateInstalled = true,
        onProfile = {},
        onInspectTls = {},
        onUpstream = {},
        onOpenCertificate = {},
        onRestart = {},
    )
}

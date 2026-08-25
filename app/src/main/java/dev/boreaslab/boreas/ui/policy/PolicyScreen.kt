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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.BoreasTextField
import dev.boreaslab.boreas.design.component.CardSurface
import dev.boreaslab.boreas.design.component.ChoiceRow
import dev.boreaslab.boreas.design.component.LoadingRegion
import dev.boreaslab.boreas.design.component.NoticeCard
import dev.boreaslab.boreas.design.component.NoticeTone
import dev.boreaslab.boreas.design.component.RowDivider
import dev.boreaslab.boreas.design.component.SwitchRow
import dev.boreaslab.boreas.model.NatBehavior
import dev.boreaslab.boreas.model.PolicyDraft
import dev.boreaslab.boreas.model.PolicyParse
import dev.boreaslab.boreas.model.TunnelField
import dev.boreaslab.boreas.model.problemFor
import dev.boreaslab.boreas.service.VpnLifecycleState
import dev.boreaslab.boreas.ui.PreviewSurface
import dev.boreaslab.boreas.ui.SettingsGroup
import dev.boreaslab.boreas.ui.copyFor

/**
 * The three tiers, disclosed in the order they escalate.
 *
 * Each one is a switch that reveals what it needs, so the screen shows exactly the
 * fields the chosen tier actually has. That is not a presentation trick: the tiers
 * nest in the domain for the same reason, and a field with nothing above it to
 * make it meaningful is a field with no job.
 *
 * A running session is not locked out. Rules reach it through a reload, which
 * drops no connection; everything else is fixed at start and says so.
 */
@Composable
fun PolicyScreen(
    draft: PolicyDraft?,
    parse: PolicyParse?,
    session: VpnLifecycleState,
    pending: Boolean,
    onChange: (PolicyDraft) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Text(
            text = stringResource(R.string.policy_intro),
            style = BoreasTheme.type.bodyMd,
            color = BoreasTheme.colors.body,
        )

        if (draft == null) {
            // Preserve layout while the stored draft loads.
            LoadingRegion(label = null)
            return@Column
        }

        if (pending) {
            NoticeCard(
                tone = NoticeTone.Warning,
                title = stringResource(R.string.policy_pending_title),
                detail = stringResource(R.string.policy_pending_detail),
                actionLabel = stringResource(R.string.policy_apply),
                onAction = onApply,
            )
        }

        NameTier(draft, parse, onChange)
        if (draft.filterNames) RequestTier(draft, parse, onChange)
        EgressGroup(draft, session, onChange)
    }
}

/** Tier one: answer names here, against rules, through one resolver. */
@Composable
private fun NameTier(
    draft: PolicyDraft,
    parse: PolicyParse?,
    onChange: (PolicyDraft) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.policy_group_names)) {
        BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
            SwitchRow(
                title = stringResource(R.string.policy_names),
                detail = stringResource(R.string.policy_names_detail),
                checked = draft.filterNames,
                onCheckedChange = { onChange(draft.copy(filterNames = it)) },
            )
        }

        if (!draft.filterNames) return@SettingsGroup

        BoreasTextField(
            label = stringResource(R.string.policy_resolver),
            value = draft.resolver,
            onValueChange = { onChange(draft.copy(resolver = it)) },
            help = stringResource(R.string.policy_resolver_help),
            error = parse?.problemFor(TunnelField.Resolver)?.let { copyFor(it) },
            keyboardType = KeyboardType.Decimal,
        )

        // Not a warning about a choice the user made wrongly. It is a property of
        // what the engine can do today: the ABI carries cleartext DNS and nothing
        // else, so a resolver reached across a network you do not control is
        // readable by anything on the path. Said plainly, next to the field, rather
        // than buried where it would be read after the fact.
        NoticeCard(
            tone = NoticeTone.Info,
            title = stringResource(R.string.policy_resolver_cleartext_title),
            detail = stringResource(R.string.policy_resolver_cleartext_detail),
            modifier = Modifier.fillMaxWidth(),
        )

        BoreasTextField(
            label = stringResource(R.string.policy_rules),
            value = draft.rules,
            onValueChange = { onChange(draft.copy(rules = it)) },
            help = stringResource(R.string.policy_rules_help),
            singleLine = false,
        )
    }
}

/** Tier two and three: terminate TLS for an allowlist, and optionally rewrite what streams. */
@Composable
private fun RequestTier(
    draft: PolicyDraft,
    parse: PolicyParse?,
    onChange: (PolicyDraft) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.policy_group_requests)) {
        BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
            SwitchRow(
                title = stringResource(R.string.policy_intercept),
                detail = stringResource(R.string.policy_intercept_detail),
                checked = draft.intercept,
                onCheckedChange = { onChange(draft.copy(intercept = it)) },
            )
            if (draft.intercept) {
                RowDivider()
                SwitchRow(
                    title = stringResource(R.string.policy_rewrite),
                    detail = stringResource(R.string.policy_rewrite_detail),
                    checked = draft.rewriteDocuments,
                    onCheckedChange = { onChange(draft.copy(rewriteDocuments = it)) },
                )
            }
        }

        if (!draft.intercept) return@SettingsGroup

        BoreasTextField(
            label = stringResource(R.string.policy_intercept_hosts),
            value = draft.interceptHosts,
            onValueChange = { onChange(draft.copy(interceptHosts = it)) },
            help = stringResource(R.string.policy_intercept_hosts_help),
            error = parse?.problemFor(TunnelField.Hosts)?.let { copyFor(it) },
            singleLine = false,
        )

        NoticeCard(
            tone = NoticeTone.Warning,
            title = stringResource(R.string.policy_intercept_root_title),
            detail = stringResource(R.string.policy_intercept_root_detail),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What the network in front of this device does to a mapping. */
@Composable
private fun EgressGroup(
    draft: PolicyDraft,
    session: VpnLifecycleState,
    onChange: (PolicyDraft) -> Unit,
) {
    val locked = session is VpnLifecycleState.Running

    SettingsGroup(title = stringResource(R.string.policy_group_nat)) {
        Text(
            text = stringResource(R.string.policy_nat_intro),
            style = BoreasTheme.type.bodySm,
            color = BoreasTheme.colors.muted,
            modifier = Modifier.fillMaxWidth(),
        )
        BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
            NatBehavior.entries.forEachIndexed { index, behavior ->
                if (index > 0) RowDivider()
                ChoiceRow(
                    title = stringResource(natTitle(behavior)),
                    detail = stringResource(natDetail(behavior)),
                    selected = draft.nat == behavior,
                    onSelect = { if (!locked) onChange(draft.copy(nat = behavior)) },
                )
            }
        }
    }
}

private fun natTitle(behavior: NatBehavior) = when (behavior) {
    NatBehavior.EndpointIndependent -> R.string.policy_nat_endpoint
    NatBehavior.AddressDependent -> R.string.policy_nat_address
    NatBehavior.AddressAndPortDependent -> R.string.policy_nat_address_port
}

private fun natDetail(behavior: NatBehavior) = when (behavior) {
    NatBehavior.EndpointIndependent -> R.string.policy_nat_endpoint_detail
    NatBehavior.AddressDependent -> R.string.policy_nat_address_detail
    NatBehavior.AddressAndPortDependent -> R.string.policy_nat_address_port_detail
}

private val PREVIEW = PolicyDraft(filterNames = true, resolver = "9.9.9.9", rules = "||example.com^")

@Preview(name = "Policy", showBackground = true)
@Composable
private fun PolicyPreview() = PreviewSurface {
    PolicyScreen(
        draft = PREVIEW,
        parse = PolicyParse.of(PREVIEW),
        session = VpnLifecycleState.Stopped,
        pending = false,
        onChange = {},
        onApply = {},
    )
}

@Preview(name = "Policy: intercepting", showBackground = true)
@Composable
private fun PolicyInterceptPreview() = PreviewSurface(dark = true) {
    val draft = PREVIEW.copy(intercept = true, interceptHosts = "example.com")
    PolicyScreen(
        draft = draft,
        parse = PolicyParse.of(draft),
        session = VpnLifecycleState.Stopped,
        pending = true,
        onChange = {},
        onApply = {},
    )
}

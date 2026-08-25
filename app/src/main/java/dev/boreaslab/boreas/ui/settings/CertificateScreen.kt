package dev.boreaslab.boreas.ui.settings

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
import dev.boreaslab.boreas.data.AuthoritySummary
import dev.boreaslab.boreas.data.ExportState
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasButton
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.ButtonVariant
import dev.boreaslab.boreas.design.component.CardSurface
import dev.boreaslab.boreas.design.component.NoticeCard
import dev.boreaslab.boreas.design.component.NoticeTone
import dev.boreaslab.boreas.design.component.SwitchRow
import dev.boreaslab.boreas.ui.PreviewSurface

/**
 * The root certificate, and the two steps that install it.
 *
 * There is no one-tap flow, and the screen does not pretend otherwise.
 * `KeyChain.createInstallIntent()` stopped installing CA certificates at Android
 * 11, and the platform's own documentation names writing the file to Downloads and
 * sending the user to Settings as the route. So this offers exactly that, in
 * order, with the second step disabled until the first has produced a file.
 *
 * Android does not let an app read the user trust store, so whether the root is
 * actually installed is not a fact this program can observe. The switch records
 * what the user says rather than claiming to know.
 */
@Composable
fun CertificateScreen(
    installed: Boolean,
    authority: AuthoritySummary?,
    export: ExportState,
    onExport: () -> Unit,
    onOpenSecuritySettings: (() -> Unit)?,
    onInstalledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Text(
            text = stringResource(R.string.certificate_intro),
            style = BoreasTheme.type.bodyMd,
            color = BoreasTheme.colors.body,
        )

        if (authority == null) {
            // No authority exists until a tunnel that intercepts has run once. Saying
            // so is more useful than an install button that would have nothing to
            // write.
            NoticeCard(
                tone = NoticeTone.Info,
                title = stringResource(R.string.certificate_absent),
                detail = stringResource(R.string.certificate_absent_detail),
            )
            return@Column
        }

        BoreasCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.certificate_present),
                style = BoreasTheme.type.displaySm,
                color = BoreasTheme.colors.ink,
            )
            Text(
                text = stringResource(R.string.certificate_fingerprint),
                style = BoreasTheme.type.label,
                color = BoreasTheme.colors.muted,
                modifier = Modifier.padding(top = Space.sm),
            )
            Text(
                text = authority.fingerprint,
                style = BoreasTheme.type.code,
                color = BoreasTheme.colors.body,
            )
        }

        Steps(
            export = export,
            onExport = onExport,
            onOpenSecuritySettings = onOpenSecuritySettings,
        )

        BoreasCard(surface = CardSurface.Filled, padding = Space.xs) {
            SwitchRow(
                title = stringResource(R.string.certificate_confirm),
                detail = stringResource(R.string.certificate_confirm_detail),
                checked = installed,
                onCheckedChange = onInstalledChange,
            )
        }

        BoreasCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.certificate_scope_title),
                style = BoreasTheme.type.titleSm,
                color = BoreasTheme.colors.ink,
            )
            Text(
                text = stringResource(R.string.certificate_scope_user),
                style = BoreasTheme.type.bodySm,
                color = BoreasTheme.colors.body,
                modifier = Modifier.padding(top = Space.xs),
            )
            Text(
                text = stringResource(R.string.certificate_scope_removal),
                style = BoreasTheme.type.bodySm,
                color = BoreasTheme.colors.body,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

/** Two steps, in order, with the second unavailable until the first has happened. */
@Composable
private fun Steps(
    export: ExportState,
    onExport: () -> Unit,
    onOpenSecuritySettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        BoreasButton(
            label = stringResource(R.string.certificate_export),
            onClick = onExport,
            variant = ButtonVariant.Primary,
            loading = export is ExportState.Working,
            enabled = export !is ExportState.Working,
            modifier = Modifier.fillMaxWidth(),
        )

        when (export) {
            ExportState.Idle, ExportState.Working -> Unit

            is ExportState.Written -> NoticeCard(
                tone = NoticeTone.Info,
                title = stringResource(R.string.certificate_exported_title, export.name),
                detail = stringResource(R.string.certificate_exported_detail),
            )

            ExportState.NoAuthority -> NoticeCard(
                tone = NoticeTone.Warning,
                title = stringResource(R.string.certificate_absent),
                detail = stringResource(R.string.certificate_absent_detail),
            )

            is ExportState.Failed -> NoticeCard(
                tone = NoticeTone.Danger,
                title = stringResource(R.string.certificate_export_failed),
                detail = export.reason,
            )
        }

        if (onOpenSecuritySettings != null) {
            BoreasButton(
                label = stringResource(R.string.certificate_open_settings),
                onClick = onOpenSecuritySettings,
                variant = ButtonVariant.Secondary,
                enabled = export is ExportState.Written,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Certificate: none yet", showBackground = true)
@Composable
private fun CertificateAbsentPreview() = PreviewSurface {
    CertificateScreen(
        installed = false,
        authority = null,
        export = ExportState.Idle,
        onExport = {},
        onOpenSecuritySettings = {},
        onInstalledChange = {},
    )
}

@Preview(name = "Certificate: exported", showBackground = true)
@Composable
private fun CertificateExportedPreview() = PreviewSurface(dark = true) {
    CertificateScreen(
        installed = false,
        authority = AuthoritySummary(fingerprint = "3B:1A:CE:04:9F:22:7D:6C"),
        export = ExportState.Written("boreas-root.crt"),
        onExport = {},
        onOpenSecuritySettings = {},
        onInstalledChange = {},
    )
}

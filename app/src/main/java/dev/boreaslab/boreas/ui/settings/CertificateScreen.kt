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
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasButton
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.ButtonVariant
import dev.boreaslab.boreas.design.component.NoticeCard
import dev.boreaslab.boreas.design.component.NoticeTone
import dev.boreaslab.boreas.ui.PreviewSurface

/** Certificate status; Android cannot verify the personal store and the engine is absent. */
@Composable
fun CertificateScreen(
    installed: Boolean,
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

        BoreasCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    if (installed) R.string.certificate_present else R.string.certificate_absent,
                ),
                style = BoreasTheme.type.displaySm,
                color = BoreasTheme.colors.ink,
            )
            if (!installed) {
                Text(
                    text = stringResource(R.string.certificate_absent_detail),
                    style = BoreasTheme.type.bodySm,
                    color = BoreasTheme.colors.muted,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }

        // Engine owns certificate generation; this build cannot offer an install flow.
        NoticeCard(
            tone = NoticeTone.Info,
            title = stringResource(R.string.fail_engine_unavailable_title),
            detail = stringResource(R.string.fail_engine_unavailable_detail),
        )

        BoreasButton(
            label = stringResource(R.string.certificate_install),
            onClick = {},
            variant = ButtonVariant.Primary,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )

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

@Preview(name = "Certificate", showBackground = true)
@Composable
private fun CertificatePreview() = PreviewSurface { CertificateScreen(installed = false) }

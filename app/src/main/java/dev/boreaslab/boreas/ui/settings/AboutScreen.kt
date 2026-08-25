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
import dev.boreaslab.boreas.BuildConfig
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasCard
import dev.boreaslab.boreas.design.component.MetricRow
import dev.boreaslab.boreas.design.component.RowDivider
import dev.boreaslab.boreas.ui.PreviewSurface

/**
 * Version, what this build contains, and the licenses it ships under.
 *
 * The three rows are one composition record. An artefact from this repository is
 * an app half and a core half pinned together, and a bug report maps to that pair
 * or it maps to nothing: the version alone cannot say which core it carries, and
 * the version of a pre-release cannot say which release it is an offset from. The
 * same three values head the release notes.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Text(
            text = stringResource(R.string.about_role),
            style = BoreasTheme.type.bodyMd,
            color = BoreasTheme.colors.body,
        )

        BoreasCard(modifier = Modifier.fillMaxWidth()) {
            MetricRow(
                label = stringResource(R.string.about_version),
                value = BuildConfig.VERSION_NAME,
            )
            RowDivider()
            MetricRow(
                label = stringResource(R.string.about_built_from),
                value = BuildConfig.BOREAS_APP_PROVENANCE,
            )
            RowDivider()
            MetricRow(
                label = stringResource(R.string.about_engine),
                value = BuildConfig.BOREAS_CORE_TAG,
            )
        }

        BoreasCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.about_typefaces),
                style = BoreasTheme.type.titleSm,
                color = BoreasTheme.colors.ink,
            )
            Text(
                text = stringResource(R.string.about_typefaces_detail),
                style = BoreasTheme.type.bodySm,
                color = BoreasTheme.colors.body,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

@Preview(name = "About", showBackground = true)
@Composable
private fun AboutPreview() = PreviewSurface { AboutScreen() }

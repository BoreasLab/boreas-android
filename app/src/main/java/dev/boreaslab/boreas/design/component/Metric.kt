package dev.boreaslab.boreas.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space

/** Counter row with stable digits and one combined accessibility announcement. */
@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    valueColor: Color? = null,
) {
    val colors = BoreasTheme.colors
    // Resource controls label/value grammar in each locale.
    val description = if (caption != null) {
        stringResource(R.string.a11y_metric_captioned, label, value, caption)
    } else {
        stringResource(R.string.a11y_metric, label, value)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            Text(label, style = BoreasTheme.type.bodySm, color = colors.body)
            if (caption != null) {
                Text(caption, style = BoreasTheme.type.label, color = colors.muted)
            }
        }
        Text(
            text = value,
            style = BoreasTheme.type.code,
            color = valueColor ?: colors.ink,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun SessionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = BoreasTheme.colors
    val description = stringResource(R.string.a11y_metric, label, value)
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        Text(label, style = BoreasTheme.type.label, color = colors.sessionMuted)
        Text(value, style = BoreasTheme.type.code, color = colors.sessionInk)
    }
}

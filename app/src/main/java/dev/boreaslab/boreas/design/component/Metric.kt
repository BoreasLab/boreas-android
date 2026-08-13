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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space

/**
 * One counter: what it counts on the left, the count on the right.
 *
 * The value is set in the monospaced face so digits hold their column while the
 * number changes, which is what stops a live counter from twitching sideways once
 * a second. Label and value are announced as one phrase rather than as two
 * unrelated fragments.
 *
 * @param caption a baseline, target, or note. A number with nothing to compare it
 *   against cannot be acted on, so where no baseline exists the caption says so
 *   rather than leaving the reader to assume one.
 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    valueColor: Color? = null,
) {
    val colors = BoreasTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = if (caption != null) {
                    "$label: $value. $caption"
                } else {
                    "$label: $value"
                }
            },
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

/**
 * A counter shown on the inverted session surface.
 *
 * A separate composable rather than a color parameter on [MetricRow]: the session
 * surface does not follow the theme, so its text roles are their own set and are
 * not something a call site should be able to pass in piecemeal.
 */
@Composable
fun SessionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = BoreasTheme.colors
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label: $value" },
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        Text(label, style = BoreasTheme.type.label, color = colors.sessionMuted)
        Text(value, style = BoreasTheme.type.code, color = colors.sessionInk)
    }
}

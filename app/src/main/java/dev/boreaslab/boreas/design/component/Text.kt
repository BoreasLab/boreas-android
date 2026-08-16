package dev.boreaslab.boreas.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            text = title,
            style = BoreasTheme.type.displaySm,
            color = BoreasTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (description != null) {
            Text(
                text = description,
                style = BoreasTheme.type.bodyMd,
                color = BoreasTheme.colors.muted,
            )
        }
    }
}

/** Small uppercase label, reserved for regions needing extra orientation. */
@Composable
fun Overline(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = BoreasTheme.colors.muted,
) {
    Text(
        text = text,
        style = BoreasTheme.type.overline,
        color = color,
        modifier = modifier,
    )
}

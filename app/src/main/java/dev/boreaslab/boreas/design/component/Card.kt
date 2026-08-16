package dev.boreaslab.boreas.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Radius
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.Stroke

/** Card surfaces used by the page rhythm. */
enum class CardSurface {
    Outlined,

    Filled,

    /** Inverted session surface, used at most once per screen. */
    Session,
}

@Composable
fun BoreasCard(
    modifier: Modifier = Modifier,
    surface: CardSurface = CardSurface.Filled,
    padding: androidx.compose.ui.unit.Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BoreasTheme.colors

    val fill = when (surface) {
        CardSurface.Outlined -> colors.canvas
        CardSurface.Filled -> colors.surface
        CardSurface.Session -> colors.sessionSurface
    }
    // Dark session and canvas fills are too close, so the boundary uses a line.
    val outline = when (surface) {
        CardSurface.Outlined -> colors.hairline
        CardSurface.Filled -> colors.hairline
        CardSurface.Session -> colors.sessionMuted.copy(alpha = 0.35f)
    }

    Column(
        modifier = modifier
            .background(fill, Radius.lg)
            .border(Stroke.hairline, outline, Radius.lg)
            .padding(padding),
        content = content,
    )
}

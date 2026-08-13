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

/**
 * Surfaces a card can sit on. A closed set, eliminated exhaustively.
 *
 * The supplied design document paces a page by alternating a light floor, a
 * one-step card, and an inverted dark surface. On a handheld the same rhythm
 * applies down a single column: [Filled] groups ordinary content, and [Session] is
 * spent once per screen on the thing the screen is about.
 */
enum class CardSurface {
    /** Canvas with a hairline. For content that must not compete with the session. */
    Outlined,

    /** One step off the canvas. The default for a discrete object. */
    Filled,

    /** The inverted surface. At most one per screen. */
    Session,
}

/**
 * A container for one discrete object the reader acts on as a unit.
 *
 * It renders what it is given rather than switching subtrees on flags, so there is
 * no header, footer, avatar, or badge boolean to combine into states nobody has
 * looked at.
 *
 * Where the content is running text rather than an object, use space and alignment
 * instead of a card.
 */
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
    // In dark, the session surface sits 1.3:1 from the canvas and would otherwise
    // be invisible, so the boundary is carried by a line rather than by the fill.
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

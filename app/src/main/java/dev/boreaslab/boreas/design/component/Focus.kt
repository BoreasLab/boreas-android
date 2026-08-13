package dev.boreaslab.boreas.design.component

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Stroke

/**
 * The focus indicator, drawn outside the control it belongs to.
 *
 * Placement is a contrast decision, not a stylistic one. Drawn on top of a filled
 * control, a cream ring on the dark theme's accent measures 2.74:1 and misses the
 * threshold for information identifying a control. Drawn outside, with a gap of
 * canvas between ring and control, the measured pairing becomes ring against
 * canvas: 17.46:1 in light and 18.21:1 in dark. Same token, same look, compliant.
 *
 * The inset is reserved whether or not the control has focus, so gaining focus
 * never moves anything.
 */
@Composable
fun Modifier.focusRing(shape: Shape, source: InteractionSource): Modifier {
    val focused by source.collectIsFocusedAsState()
    val color = if (focused) BoreasTheme.colors.focus else Color.Transparent
    return this
        .border(Stroke.focus, color, shape)
        .padding(Stroke.focus + Stroke.focusGap)
}

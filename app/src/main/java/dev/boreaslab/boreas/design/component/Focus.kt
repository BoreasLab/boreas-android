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

/** Draws focus outside the control for canvas contrast and reserves its space. */
@Composable
fun Modifier.focusRing(shape: Shape, source: InteractionSource): Modifier {
    val focused by source.collectIsFocusedAsState()
    val color = if (focused) BoreasTheme.colors.focus else Color.Transparent
    return this
        .border(Stroke.focus, color, shape)
        .padding(Stroke.focus + Stroke.focusGap)
}

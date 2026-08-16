package dev.boreaslab.boreas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space

/** Shared preview canvas; screens render both themes. */
@Composable
fun PreviewSurface(
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    BoreasTheme(dark = dark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BoreasTheme.colors.canvas)
                .padding(Space.md),
        ) {
            content()
        }
    }
}

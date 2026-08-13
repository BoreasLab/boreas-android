package dev.boreaslab.boreas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasIcons
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.IconSize
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.Stroke
import dev.boreaslab.boreas.design.component.BoreasButton
import dev.boreaslab.boreas.design.component.ButtonVariant

/**
 * The screen frame.
 *
 * One title, in the serif display face, matching the navigation label exactly.
 * Detail screens carry a back control in the same slot rather than a second
 * pattern, so the frame is one shape everywhere.
 */
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BoreasTheme.colors.canvas),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            if (onBack != null) {
                BoreasButton(
                    label = stringResource(R.string.action_back),
                    onClick = onBack,
                    variant = ButtonVariant.Quiet,
                    icon = BoreasIcons.ArrowLeft,
                )
            }
            Text(
                text = title,
                style = BoreasTheme.type.displayMd,
                color = BoreasTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

/**
 * The navigation bar.
 *
 * Four peers, which is inside the range a bar handles well. The current location is
 * carried by the label's weight and a filled indicator, not by color alone.
 */
@Composable
fun BoreasNavigationBar(
    current: String?,
    onSelect: (Destination.TopLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BoreasTheme.colors
    Column(modifier) {
        HorizontalDivider(thickness = Stroke.hairline, color = colors.hairline)
        NavigationBar(
            containerColor = colors.canvas,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars,
        ) {
            Destination.topLevel.forEach { destination ->
                val selected = current == destination.route
                val label = stringResource(destination.label)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.md),
                        )
                    },
                    label = { Text(label, style = BoreasTheme.type.label) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.onPrimary,
                        selectedTextColor = colors.ink,
                        indicatorColor = colors.primary,
                        unselectedIconColor = colors.muted,
                        unselectedTextColor = colors.muted,
                    ),
                )
            }
        }
    }
}

/** A labelled group of rows, separated from its neighbours by space rather than a box. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = BoreasTheme.type.titleSm,
                color = BoreasTheme.colors.muted,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

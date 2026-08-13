package dev.boreaslab.boreas.design

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the reader has turned animations off at the OS level.
 *
 * Read at the point of use rather than cached at startup, so a mid-session change
 * takes effect. Reduced motion removes movement only; nothing here gates content,
 * state, or an affordance behind an animation.
 */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
private fun systemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * One theme, set once at the root, following the system.
 *
 * There is deliberately no in-app light/dark setting. An app-level override is a
 * second place the answer can live, and the two disagree the moment the reader
 * changes the system and forgets this: they then carry a preference they set once
 * and cannot remember setting. The window background in `res/values-night` already
 * followed the system unconditionally, so an override also meant a cold start
 * painted one theme and Compose replaced it with the other.
 *
 * [isSystemInDarkTheme] is read during composition rather than captured, and the
 * Activity declares `uiMode` in `configChanges`, so a system change recomposes this
 * tree in place instead of restarting anything.
 *
 * Material 3 is the only component system in the tree. Its color scheme and
 * typography are derived from the Boreas tokens below so that the components this
 * surface does use (navigation bar, switch, dialog, snackbar) inherit the same
 * values rather than carrying a second palette. Roles Material has no slot for
 * (the session surface, the decorative hairline against the control border, the
 * offset focus ring) live on [BoreasColors] and are read through [BoreasTheme].
 *
 * @param dark defaults to the system's answer, which is the only value production
 *   ever passes. It is a parameter so a `@Preview` can render both without the app
 *   carrying a setting that exists for tooling.
 */
@Composable
fun BoreasTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors

    val material = if (dark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surfaceStrong,
            onSurfaceVariant = colors.muted,
            background = colors.canvas,
            onBackground = colors.ink,
            error = colors.danger,
            onError = colors.canvas,
            outline = colors.border,
            outlineVariant = colors.hairline,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surfaceStrong,
            onSurfaceVariant = colors.muted,
            background = colors.canvas,
            onBackground = colors.ink,
            error = colors.danger,
            onError = colors.canvas,
            outline = colors.border,
            outlineVariant = colors.hairline,
        )
    }

    CompositionLocalProvider(
        LocalBoreasColors provides colors,
        LocalBoreasType provides BoreasType,
        LocalReducedMotion provides systemReducedMotion(),
        LocalContentColor provides colors.ink,
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = Typography(
                titleLarge = BoreasType.titleMd,
                titleMedium = BoreasType.titleSm,
                bodyLarge = BoreasType.bodyMd,
                bodyMedium = BoreasType.bodySm,
                labelLarge = BoreasType.button,
                labelMedium = BoreasType.label,
                labelSmall = BoreasType.overline,
            ),
            content = content,
        )
    }
}

object BoreasTheme {
    val colors: BoreasColors
        @Composable @ReadOnlyComposable get() = LocalBoreasColors.current

    val type: BoreasTypography
        @Composable @ReadOnlyComposable get() = LocalBoreasType.current
}

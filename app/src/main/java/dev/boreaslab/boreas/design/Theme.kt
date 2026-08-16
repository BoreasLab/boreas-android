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

/** OS reduced-motion setting, read at use so mid-session changes take effect. */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
private fun systemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** System-following theme with no in-app override; [dark] exists for previews. */
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

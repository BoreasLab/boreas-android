package dev.boreaslab.boreas.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Compose color roles lifted from domain tokens, whose contrast law is tested there. */
@Immutable
data class BoreasColors(
    val canvas: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val sessionSurface: Color,

    val ink: Color,
    val body: Color,
    val muted: Color,

    val sessionInk: Color,
    val sessionMuted: Color,
    val sessionPrimary: Color,

    val primary: Color,
    val primaryPressed: Color,
    val onPrimary: Color,

    /** Semantic role, always paired with an icon and label. */
    val danger: Color,
    val warning: Color,

    /** Decorative divider; contrast-exempt. */
    val hairline: Color,
    val border: Color,
    /** Focus ring measured against the canvas, not the control fill. */
    val focus: Color,
)

private fun Srgb.toColor() = Color(0xFF000000L.toInt() or packed)

private fun ColorRoles.toCompose() = BoreasColors(
    canvas = canvas.toColor(),
    surface = surface.toColor(),
    surfaceStrong = surfaceStrong.toColor(),
    sessionSurface = sessionSurface.toColor(),
    ink = ink.toColor(),
    body = body.toColor(),
    muted = muted.toColor(),
    sessionInk = sessionInk.toColor(),
    sessionMuted = sessionMuted.toColor(),
    sessionPrimary = sessionPrimary.toColor(),
    primary = primary.toColor(),
    primaryPressed = primaryPressed.toColor(),
    onPrimary = onPrimary.toColor(),
    danger = danger.toColor(),
    warning = warning.toColor(),
    hairline = hairline.toColor(),
    border = border.toColor(),
    focus = focus.toColor(),
)

internal val LightColors = LightRoles.toCompose()
internal val DarkColors = DarkRoles.toCompose()

internal val LocalBoreasColors = staticCompositionLocalOf { LightColors }

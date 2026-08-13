package dev.boreaslab.boreas.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color roles, as Compose values.
 *
 * The values themselves live in `:domain` as [ColorRoles], where the contrast
 * floor is stated as a law and checked by a plain JVM test. This file only lifts
 * them into Compose, so a token cannot be changed here without the law seeing it,
 * and there is no second place a hex literal could enter the interface.
 *
 * Sources, in precedence order:
 *  1. The WCAG 2.2 AA floor, which overrides everything below it.
 *  2. The supplied palette, which overrides the design document's colors only.
 *  3. The supplied design document, which keeps its surface roles: a light canvas
 *     floor, a one-step card, and an inverted surface as the pacing device.
 */
@Immutable
data class BoreasColors(
    /** Page floor. */
    val canvas: Color,
    /** One step off the canvas. Cards holding a discrete object. */
    val surface: Color,
    /** Two steps off the canvas. Selected chips and pressed rows. */
    val surfaceStrong: Color,
    /** The inverted surface. Carries the session state and nothing else. */
    val sessionSurface: Color,

    /** Headings and primary text. */
    val ink: Color,
    /** Running text. */
    val body: Color,
    /** Secondary text. Measured against every surface it sits on. */
    val muted: Color,

    /** Text and fills on [sessionSurface], which does not follow the theme. */
    val sessionInk: Color,
    val sessionMuted: Color,
    val sessionPrimary: Color,

    /** The single accent. Means action and selection, nothing else. */
    val primary: Color,
    val primaryPressed: Color,
    val onPrimary: Color,

    /** Semantic. Never the only channel; always paired with an icon and a label. */
    val danger: Color,
    val warning: Color,

    /** Decorative divider between two same-elevation surfaces. Contrast-exempt. */
    val hairline: Color,
    /** Identifies where a control begins. Held at the non-text threshold or better. */
    val border: Color,
    /** Drawn offset outside the control, so it is measured against the canvas. */
    val focus: Color,
)

/** Opaque lift from the pure 24-bit token to a Compose color. */
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

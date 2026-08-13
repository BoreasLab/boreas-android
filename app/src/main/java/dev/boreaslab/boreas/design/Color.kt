package dev.boreaslab.boreas.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color roles for the Boreas surface.
 *
 * Sources, in precedence order:
 *  1. The WCAG 2.2 AA floor, which overrides everything below it.
 *  2. The supplied palette (space-indigo / beige / light-bronze / dusty-rose), which
 *     overrides the colors of the supplied design document and nothing else.
 *  3. The supplied design document, which keeps its surface roles: a light canvas
 *     floor, a one-step-darker card, and an inverted dark surface used as the
 *     pacing device.
 *
 * Every value here is a step of a supplied ramp, except [BoreasColors.muted] and
 * the dark-theme border, which are space-indigo-800 blended toward beige-50 at the
 * ratio each theme needs to clear 4.5:1 on its densest surface. Ratios for every
 * pairing are recorded in docs/design-system.md.
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
    /** Identifies where a control begins. Held at 3:1 or better. */
    val border: Color,
    /** Drawn offset outside the control, so it is measured against the canvas. */
    val focus: Color,
)

private val Indigo800 = Color(0xFF19254D)
private val Indigo900 = Color(0xFF0D1226)
private val Indigo950 = Color(0xFF090D1B)
private val Beige50 = Color(0xFFF8F9EC)
private val Beige100 = Color(0xFFF2F3D8)
private val Beige200 = Color(0xFFE5E7B1)
private val Beige300 = Color(0xFFD8DA8B)
private val Beige800 = Color(0xFF4C4E18)
private val Bronze300 = Color(0xFFDDA388)
private val Bronze400 = Color(0xFFD28460)
private val Bronze500 = Color(0xFFC76538)
private val Bronze600 = Color(0xFF9F512D)
private val Bronze700 = Color(0xFF773D22)
private val Rose300 = Color(0xFFD19495)
private val Rose600 = Color(0xFF8F3D3F)

/** space-indigo-800 blended 27% toward beige-50. Clears 4.5:1 on beige-50/100/200. */
private val MutedLight = Color(0xFF555E78)

/** space-indigo-800 blended 55% toward beige-50. Clears 4.5:1 on indigo-950/900/800. */
private val MutedDark = Color(0xFF949AA4)

internal val LightColors = BoreasColors(
    canvas = Beige50,
    surface = Beige100,
    surfaceStrong = Beige200,
    sessionSurface = Indigo900,
    ink = Indigo900,
    body = Indigo800,
    muted = MutedLight,
    sessionInk = Beige50,
    sessionMuted = MutedDark,
    sessionPrimary = Bronze400,
    primary = Bronze600,
    primaryPressed = Bronze700,
    onPrimary = Beige50,
    danger = Rose600,
    warning = Beige800,
    hairline = Beige200,
    border = MutedLight,
    focus = Indigo900,
)

internal val DarkColors = BoreasColors(
    canvas = Indigo950,
    surface = Indigo900,
    surfaceStrong = Indigo800,
    sessionSurface = Indigo800,
    ink = Beige50,
    body = Beige100,
    muted = MutedDark,
    sessionInk = Beige50,
    sessionMuted = MutedDark,
    sessionPrimary = Bronze300,
    primary = Bronze400,
    primaryPressed = Bronze500,
    onPrimary = Indigo950,
    danger = Rose300,
    warning = Beige300,
    hairline = Indigo800,
    border = MutedDark,
    focus = Beige50,
)

internal val LocalBoreasColors = staticCompositionLocalOf { LightColors }

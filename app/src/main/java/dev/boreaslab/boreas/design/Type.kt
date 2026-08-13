package dev.boreaslab.boreas.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import dev.boreaslab.boreas.R

/**
 * The display/text split from the supplied design document: a serif for display
 * sizes at weight 400 with negative tracking, a humanist sans for everything else.
 *
 * Copernicus and StyreneB are licensed and cannot ship here. These are the
 * substitutes the document itself names: EB Garamond for the serif and Inter for
 * the sans, both under the SIL Open Font License, plus JetBrains Mono for counters
 * and identifiers. This is an approximation of the brand's voice, not the brand's
 * own typefaces. License texts ship in assets/licenses.
 *
 * All three are variable fonts, subset to Latin and pinned to one optical size, so
 * one file per family covers every weight in use (240KB total).
 */
private fun serif(weight: Int) = Font(
    R.font.boreas_serif,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun sans(weight: Int) = Font(
    R.font.boreas_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun mono(weight: Int) = Font(
    R.font.boreas_mono,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val BoreasSerif = FontFamily(serif(400))
val BoreasSans = FontFamily(sans(400), sans(500))
val BoreasMono = FontFamily(mono(400), mono(500))

/** Trim the font's own metric whitespace so type aligns optically, not mechanically. */
private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Eleven steps. The document declares fourteen; its 64px and 48px display sizes are
 * desktop marketing values with no role on a handheld, and its 22px title-lg
 * duplicated display-sm at this scale. Sizes are in sp, so they honor the reader's
 * text-size preference.
 */
@Immutable
data class BoreasTypography(
    val displayLg: TextStyle,
    val displayMd: TextStyle,
    val displaySm: TextStyle,
    val titleMd: TextStyle,
    val titleSm: TextStyle,
    val bodyMd: TextStyle,
    val bodySm: TextStyle,
    val label: TextStyle,
    val overline: TextStyle,
    val code: TextStyle,
    val button: TextStyle,
)

internal val BoreasType = BoreasTypography(
    displayLg = TextStyle(
        fontFamily = BoreasSerif, fontWeight = FontWeight.W400,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp,
        lineHeightStyle = Trim,
    ),
    displayMd = TextStyle(
        fontFamily = BoreasSerif, fontWeight = FontWeight.W400,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp,
        lineHeightStyle = Trim,
    ),
    displaySm = TextStyle(
        fontFamily = BoreasSerif, fontWeight = FontWeight.W400,
        fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
        lineHeightStyle = Trim,
    ),
    titleMd = TextStyle(
        fontFamily = BoreasSans, fontWeight = FontWeight.W500,
        fontSize = 18.sp, lineHeight = 25.sp,
    ),
    titleSm = TextStyle(
        fontFamily = BoreasSans, fontWeight = FontWeight.W500,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyMd = TextStyle(
        fontFamily = BoreasSans, fontWeight = FontWeight.W400,
        fontSize = 16.sp, lineHeight = 25.sp,
    ),
    bodySm = TextStyle(
        fontFamily = BoreasSans, fontWeight = FontWeight.W400,
        fontSize = 14.sp, lineHeight = 22.sp,
    ),
    label = TextStyle(
        fontFamily = BoreasSans, fontWeight = FontWeight.W500,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    overline = TextStyle(
        fontFamily = BoreasSans, fontWeight = FontWeight.W500,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp,
    ),
    // Counters and identifiers. Monospaced, so digits hold their column while updating.
    code = TextStyle(
        fontFamily = BoreasMono, fontWeight = FontWeight.W400,
        fontSize = 14.sp, lineHeight = 22.sp,
    ),
    button = TextStyle(
        fontFamily = BoreasSans, fontWeight = FontWeight.W500,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
)

internal val LocalBoreasType = staticCompositionLocalOf { BoreasType }

package dev.boreaslab.boreas.design

import kotlin.math.pow

/**
 * An opaque sRGB color, packed as `0xRRGGBB`.
 *
 * Refined rather than an `Int`: the only way to hold one is to have parsed one,
 * so a value outside the 24-bit range cannot reach the luminance computation and
 * silently produce a ratio for a color nobody can render.
 */
@JvmInline
value class Srgb private constructor(val packed: Int) {

    val red: Int get() = (packed shr 16) and 0xFF
    val green: Int get() = (packed shr 8) and 0xFF
    val blue: Int get() = packed and 0xFF

    /** `0xRRGGBB` as lowercase hex, for test output and documentation. */
    fun hex(): String = "#%06x".format(packed)

    companion object {
        fun of(packed: Int): Srgb {
            require(packed in 0x000000..0xFFFFFF) {
                "not a 24-bit sRGB value: 0x${packed.toString(16)}"
            }
            return Srgb(packed)
        }
    }
}

/**
 * How much contrast a pairing must reach.
 *
 * A closed set, because the thresholds are the two the conformance target
 * defines and inventing a third would be inventing a standard. Values are from
 * WCAG 2.2 (W3C Recommendation, 12 December 2024), conformance level AA.
 */
enum class ContrastRequirement(val minimum: Double) {

    /** SC 1.4.3 Contrast (Minimum): text below the large-text threshold. */
    BodyText(4.5),

    /**
     * SC 1.4.11 Non-text Contrast: visual information identifying a control or
     * its state, and parts of a graphic required to understand the content.
     */
    NonText(3.0),
}

/**
 * Relative luminance, per the WCAG 2.2 definition.
 *
 * Pure and total over [Srgb]. Three channel transfers and a dot product, so
 * $O(1)$ with no allocation beyond the boxed `Double` the caller asks for.
 */
fun Srgb.relativeLuminance(): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

/**
 * Contrast ratio between two opaque colors, in `1.0..21.0`.
 *
 * Symmetric: the brighter of the two is always the numerator, so callers need
 * not know which color is the foreground.
 */
fun contrastRatio(a: Srgb, b: Srgb): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * One pairing the interface actually renders, and the threshold it must clear.
 *
 * Pairings are data rather than assertions written out one by one, so the set can
 * be folded over: the test is a single traversal accumulating every failure
 * instead of stopping at the first.
 */
data class Pairing(
    val describe: String,
    val foreground: Srgb,
    val background: Srgb,
    val requirement: ContrastRequirement,
) {
    val ratio: Double get() = contrastRatio(foreground, background)
    val holds: Boolean get() = ratio >= requirement.minimum
}

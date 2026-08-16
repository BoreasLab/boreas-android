package dev.boreaslab.boreas.design

import kotlin.math.pow

/** Opaque sRGB color packed as `0xRRGGBB` and validated at construction. */
@JvmInline
public value class Srgb private constructor(public val packed: Int) {

    public val red: Int get() = (packed shr 16) and 0xFF
    public val green: Int get() = (packed shr 8) and 0xFF
    public val blue: Int get() = packed and 0xFF

    /** Lowercase `0xRRGGBB` for test output and documentation. */
    public fun hex(): String = "#%06x".format(packed)

    public companion object {
        public fun of(packed: Int): Srgb {
            require(packed in 0x000000..0xFFFFFF) {
                "not a 24-bit sRGB value: 0x${packed.toString(16)}"
            }
            return Srgb(packed)
        }
    }
}

/** Contrast thresholds required by the WCAG 2.2 AA target. */
public enum class ContrastRequirement(public val minimum: Double) {

    BodyText(4.5),

    NonText(3.0),
}

/** Relative luminance per WCAG 2.2. */
public fun Srgb.relativeLuminance(): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

/** Symmetric contrast ratio between two opaque colors, in `1.0..21.0`. */
public fun contrastRatio(a: Srgb, b: Srgb): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

public data class Pairing(
    val describe: String,
    val foreground: Srgb,
    val background: Srgb,
    val requirement: ContrastRequirement,
) {
    public val ratio: Double get() = contrastRatio(foreground, background)
    public val holds: Boolean get() = ratio >= requirement.minimum
}

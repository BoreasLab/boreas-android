package dev.boreaslab.boreas.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ContrastLawTest {

    @Test
    fun `relative luminance matches the specification's endpoints`() {
        assertEquals(0.0, Srgb.of(0x000000).relativeLuminance(), 1e-9)
        assertEquals(1.0, Srgb.of(0xFFFFFF).relativeLuminance(), 1e-9)
    }

    @Test
    fun `contrast is symmetric and bounded by the black on white extreme`() {
        val black = Srgb.of(0x000000)
        val white = Srgb.of(0xFFFFFF)
        assertEquals(21.0, contrastRatio(black, white), 1e-6)
        assertEquals(contrastRatio(black, white), contrastRatio(white, black), 1e-12)
        assertEquals(1.0, contrastRatio(black, black), 1e-12)
    }

    @Test
    fun `a value outside the 24 bit range is rejected rather than rendered`() {
        listOf(-1, 0x1000000).forEach { packed ->
            runCatching { Srgb.of(packed) }
                .onSuccess { fail("accepted an unrenderable value: $packed") }
        }
    }

    @Test
    fun `every light theme pairing clears its threshold`() = assertPairingsHold("light", LightRoles)

    @Test
    fun `every dark theme pairing clears its threshold`() = assertPairingsHold("dark", DarkRoles)

    @Test
    fun `both themes declare the same pairing set`() {
        val light = LightRoles.pairings().map { it.describe }
        val dark = DarkRoles.pairings().map { it.describe }
        assertEquals(light, dark)
        assertEquals(
            "a new pairing needs its count updated here deliberately",
            22,
            light.size,
        )
        assertEquals("pairing descriptions must be unique", light.size, light.toSet().size)
    }

    private fun assertPairingsHold(theme: String, roles: ColorRoles) {
        val failures = roles.pairings().filterNot { it.holds }
        assertTrue(
            failures.joinToString(
                prefix = "$theme theme, ${failures.size} pairing(s) below the floor:\n",
                separator = "\n",
            ) { pairing ->
                "  %-32s %.2f:1, needs %.1f:1  (%s on %s)".format(
                    pairing.describe,
                    pairing.ratio,
                    pairing.requirement.minimum,
                    pairing.foreground.hex(),
                    pairing.background.hex(),
                )
            },
            failures.isEmpty(),
        )
    }
}

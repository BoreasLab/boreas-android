package dev.boreaslab.boreas.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Parsing at the untrusted entry.
 *
 * Two properties are being held here. Values are accepted whenever the reader's
 * intent is unambiguous, because reformatting is the system's job. And a value that
 * is genuinely wrong produces the specific problem naming the fix, not a blanket
 * rejection.
 */
class ConfigParsingTest {

    private fun <T> valueOf(parsed: Parsed<T>): T = (parsed as Parsed.Valid<T>).value
    private fun problemOf(parsed: Parsed<*>) = (parsed as Parsed.Invalid).problem

    @Test
    fun `addresses are accepted despite formatting the reader did not intend`() {
        val cases = mapOf(
            "10.24.0.2" to "10.24.0.2",
            "  10.24.0.2  " to "10.24.0.2",
            "10.24.0.2." to "10.24.0.2",
            "010.024.000.002" to "10.24.0.2",
        )
        cases.forEach { (input, expected) ->
            assertEquals(input, expected, valueOf(Ipv4Address.parse(input)).text)
        }
    }

    @Test
    fun `an address problem names which rule was broken`() {
        assertEquals(FieldProblem.Required, problemOf(Ipv4Address.parse("   ")))
        assertEquals(FieldProblem.AddressShape, problemOf(Ipv4Address.parse("10.24.0")))
        assertEquals(FieldProblem.AddressShape, problemOf(Ipv4Address.parse("10.24.0.x")))
        assertEquals(FieldProblem.AddressRange, problemOf(Ipv4Address.parse("10.24.0.999")))
    }

    @Test
    fun `an octet too large for an Int is still a range problem`() {
        // Every character is a digit, so the shape was never in question. Converting
        // before checking length reported this as a shape problem and sent the
        // reader to fix punctuation that was already correct.
        assertEquals(FieldProblem.AddressRange, problemOf(Ipv4Address.parse("10.0.0.99999999999")))
        assertEquals(FieldProblem.AddressRange, problemOf(Ipv4Address.parse("2147483648.0.0.1")))
        // Zero padding is normalization, not size: the value is 2, so it parses.
        assertEquals("10.0.0.2", valueOf(Ipv4Address.parse("10.0.0.00000002")).text)
    }

    @Test
    fun `mtu accepts digit grouping and spacing`() {
        assertEquals(1500, valueOf(Mtu.parse("1500")).bytes)
        assertEquals(1500, valueOf(Mtu.parse(" 1,500 ")).bytes)
        assertEquals(9000, valueOf(Mtu.parse("9000")).bytes)
    }

    @Test
    fun `mtu outside the carrying range is a range problem, not a shape problem`() {
        assertEquals(FieldProblem.MtuRange, problemOf(Mtu.parse("1279")))
        assertEquals(FieldProblem.MtuRange, problemOf(Mtu.parse("9001")))
        assertEquals(FieldProblem.MtuShape, problemOf(Mtu.parse("wide")))
        assertEquals(FieldProblem.Required, problemOf(Mtu.parse("")))
    }

    @Test
    fun `a draft reports every bad field at once`() {
        val parse = TunnelParse.of(
            TunnelDraft(address = "10.24.0", mtu = "12", dns = ""),
            excludedPackages = emptySet(),
        )

        // The type is what stops a trusted value escaping an invalid draft: there
        // is no PlatformConfig on this branch to reach for.
        val invalid = parse as TunnelParse.Invalid
        assertEquals(FieldProblem.AddressShape, invalid.problems[TunnelField.Address])
        assertEquals(FieldProblem.MtuRange, invalid.problems[TunnelField.Mtu])
        assertEquals(FieldProblem.AddressShape, parse.problemFor(TunnelField.Address))
        assertNull(parse.problemFor(TunnelField.Dns))
    }

    @Test
    fun `an invalid parse cannot be constructed without naming a problem`() {
        runCatching { TunnelParse.Invalid(emptyMap()) }
            .onSuccess { fail("an invalid parse with no problem is not a state") }
    }

    @Test
    fun `dns accepts newlines, commas, and spaces between entries`() {
        val parse = TunnelParse.of(
            TunnelDraft(dns = "9.9.9.9\n149.112.112.112, 1.1.1.1"),
            excludedPackages = emptySet(),
        )

        assertEquals(
            listOf("9.9.9.9", "149.112.112.112", "1.1.1.1"),
            (parse as TunnelParse.Valid).config.dnsServers.map { it.text },
        )
    }

    @Test
    fun `empty dns is valid and means keep what the network supplies`() {
        val parse = TunnelParse.of(TunnelDraft(dns = "  "), emptySet())
        assertTrue((parse as TunnelParse.Valid).config.dnsServers.isEmpty())
    }

    @Test
    fun `a bad dns entry is reported with the entry that caused it`() {
        val parse = TunnelParse.of(TunnelDraft(dns = "9.9.9.9\nnope"), emptySet())

        assertEquals(
            FieldProblem.DnsShape("nope"),
            (parse as TunnelParse.Invalid).problems[TunnelField.Dns],
        )
    }

    @Test
    fun `the default draft is valid, so a first run has nothing to fix`() {
        assertTrue(TunnelParse.of(TunnelDraft(), emptySet()) is TunnelParse.Valid)
    }
}

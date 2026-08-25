package dev.boreaslab.boreas.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The policy boundary, and the states the core would refuse.
 *
 * Each of the refusals in api/abi.md#what-produces-boreas_config that this app
 * could reach is checked here for the same thing: not that the parse rejects it,
 * but that a valid parse cannot produce it. A combination the type system already
 * forbids needs no test, and the ones below are the ones it does not.
 */
class PolicyParsingTest {

    private fun valid(parse: PolicyParse) = (parse as PolicyParse.Valid).config
    private fun problems(parse: PolicyParse) = (parse as PolicyParse.Invalid).problems

    // Endpoints.

    @Test
    fun `an endpoint takes the DNS port when none is written`() {
        assertEquals("9.9.9.9:53", (Endpoint.parse("9.9.9.9") as Parsed.Valid).value.text)
        assertEquals("9.9.9.9:5353", (Endpoint.parse(" 9.9.9.9:5353 ") as Parsed.Valid).value.text)
    }

    @Test
    fun `an endpoint problem names which rule was broken`() {
        assertEquals(FieldProblem.Required, (Endpoint.parse("  ") as Parsed.Invalid).problem)
        assertEquals(FieldProblem.PortRange, (Endpoint.parse("9.9.9.9:0") as Parsed.Invalid).problem)
        assertEquals(FieldProblem.PortRange, (Endpoint.parse("9.9.9.9:70000") as Parsed.Invalid).problem)
        assertEquals(FieldProblem.PortRange, (Endpoint.parse("9.9.9.9:dns") as Parsed.Invalid).problem)
        // The core wants a numeric address, so a name is a shape problem here.
        assertEquals(FieldProblem.AddressShape, (Endpoint.parse("dns.example.com") as Parsed.Invalid).problem)
    }

    // Hostnames.

    @Test
    fun `a hostname is normalised, not merely accepted`() {
        assertEquals("example.com", (Hostname.parse(" Example.COM. ") as Parsed.Valid).value.text)
    }

    @Test
    fun `an address is not a hostname, which is what the core would refuse it for`() {
        assertTrue(Hostname.parse("10.0.0.1") is Parsed.Invalid)
        assertTrue(Hostname.parse("-leading.example.com") is Parsed.Invalid)
        assertTrue(Hostname.parse("has space.example.com") is Parsed.Invalid)
        assertTrue(Hostname.parse("*.example.com") is Parsed.Invalid)
    }

    // Tiers.

    @Test
    fun `filtering off needs no resolver and produces one`() {
        val config = valid(PolicyParse.of(PolicyDraft(filterNames = false, resolver = "nonsense")))

        assertEquals(Filtering.Off, config.filtering)
    }

    @Test
    fun `filtering on without a resolver is a field problem, never a valid config`() {
        val parse = PolicyParse.of(PolicyDraft(filterNames = true, resolver = ""))

        assertEquals(FieldProblem.Required, problems(parse)[TunnelField.Resolver])
    }

    @Test
    fun `an empty rule set is a resolver that answers everything, which is legal`() {
        val config = valid(PolicyParse.of(PolicyDraft(filterNames = true, resolver = "9.9.9.9", rules = "  ")))

        val filtering = config.filtering as Filtering.Names
        assertEquals(emptyList<String>(), filtering.lists)
        assertNull(filtering.interception)
    }

    @Test
    fun `rules cross as one list's text rather than one entry per line`() {
        val rules = "||ads.example.net^\n||metrics.example.com^"
        val config = valid(PolicyParse.of(PolicyDraft(filterNames = true, resolver = "9.9.9.9", rules = rules)))

        assertEquals(listOf(rules), (config.filtering as Filtering.Names).lists)
    }

    @Test
    fun `interception over the empty set is refused rather than silently downgraded`() {
        val parse = PolicyParse.of(
            PolicyDraft(filterNames = true, resolver = "9.9.9.9", intercept = true, interceptHosts = "  "),
        )

        assertEquals(FieldProblem.Required, problems(parse)[TunnelField.Hosts])
    }

    @Test
    fun `a bad intercepted host is reported with the entry that caused it`() {
        val parse = PolicyParse.of(
            PolicyDraft(
                filterNames = true,
                resolver = "9.9.9.9",
                intercept = true,
                interceptHosts = "example.com\n10.0.0.1",
            ),
        )

        assertEquals(FieldProblem.HostShape("10.0.0.1"), problems(parse)[TunnelField.Hosts])
    }

    @Test
    fun `document rewriting only exists inside interception`() {
        val without = valid(
            PolicyParse.of(
                PolicyDraft(filterNames = true, resolver = "9.9.9.9", rewriteDocuments = true),
            ),
        )
        // The flag was set and there is nowhere for it to go, which is the point:
        // it is a field of Interception, so it cannot be asked for without one.
        assertNull((without.filtering as Filtering.Names).interception)

        val with = valid(
            PolicyParse.of(
                PolicyDraft(
                    filterNames = true,
                    resolver = "9.9.9.9",
                    intercept = true,
                    interceptHosts = "example.com",
                    rewriteDocuments = true,
                ),
            ),
        )
        assertTrue((with.filtering as Filtering.Names).interception!!.rewriteDocuments)
    }

    @Test
    fun `an invalid parse cannot be constructed without naming a problem`() {
        runCatching { PolicyParse.Invalid(emptyMap()) }
            .onSuccess { throw AssertionError("an invalid parse with no problem is not a state") }
    }

    @Test
    fun `every bad field is reported at once`() {
        val parse = PolicyParse.of(
            PolicyDraft(filterNames = true, resolver = "nope", intercept = true, interceptHosts = ""),
        )

        assertEquals(FieldProblem.AddressShape, problems(parse)[TunnelField.Resolver])
        assertEquals(FieldProblem.Required, problems(parse)[TunnelField.Hosts])
    }

    // What a reload can carry.

    @Test
    fun `a rule change reaches a running session`() {
        val a = EngineConfig(Filtering.Names(resolver(), listOf("||a.example.com^")))
        val b = EngineConfig(Filtering.Names(resolver(), listOf("||b.example.com^")))

        assertTrue(a.reachesRunning(b))
    }

    @Test
    fun `everything fixed at start does not reach a running session`() {
        val base = EngineConfig(Filtering.Names(resolver(), emptyList()))

        assertTrue(
            "the resolver is fixed at start",
            !base.reachesRunning(EngineConfig(Filtering.Names(resolver("1.1.1.1"), emptyList()))),
        )
        assertTrue(
            "the intercepted host list is fixed at start",
            !base.reachesRunning(
                EngineConfig(
                    Filtering.Names(
                        resolver(),
                        emptyList(),
                        Interception(listOf((Hostname.parse("example.com") as Parsed.Valid).value)),
                    ),
                ),
            ),
        )
        assertTrue(
            "NAT behaviour is read at start",
            !base.reachesRunning(base.copy(nat = NatBehavior.EndpointIndependent)),
        )
        assertTrue(
            "turning filtering off is not a reload",
            !base.reachesRunning(EngineConfig(Filtering.Off)),
        )
    }

    private fun resolver(text: String = "9.9.9.9") =
        (Endpoint.parse(text) as Parsed.Valid).value
}

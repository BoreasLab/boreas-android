package dev.boreaslab.boreas.model

/**
 * What the core is asked to do to what crosses it.
 *
 * The tiers escalate and the nesting is the escalation, so the combinations that
 * would run and filter nothing are not states this program can reach:
 *
 *   - Document rewriting without interception cannot be written down, because
 *     it is a field of [Interception].
 *   - Interception without name filtering cannot be written down, because it is
 *     a field of [Filtering.Names].
 *   - Filtering without a resolver cannot be written down, because [Filtering.Names]
 *     carries one. On the packet path a flow is selected for inspection *because a
 *     DNS answer named its address*, so a tunnel that never sees a question can
 *     filter nothing while reporting itself healthy. The core refuses that
 *     combination; here it is simply unrepresentable, so nothing has to check.
 */
public sealed interface Filtering {

    /** Carry traffic, decide nothing. Questions cross untouched. */
    public data object Off : Filtering

    /** Answer names here against [lists], forwarding what policy allows to [upstream]. */
    public data class Names(
        val upstream: Endpoint,
        /** Filter-list text in AdGuard/uBlock syntax. A malformed line is skipped, not fatal. */
        val lists: List<String>,
        /** Absent for the name tier alone, which needs no certificate authority. */
        val interception: Interception? = null,
    ) : Filtering
}

/**
 * Terminate TLS for [hosts] and filter the requests inside.
 *
 * An allowlist, never a pattern: interception forges a certificate, and the set of
 * hosts that happens to should be one a person can read.
 */
public data class Interception(
    val hosts: List<Hostname>,
    /** Rewrite HTML bodies as they stream, inside a memory budget the core owns. */
    val rewriteDocuments: Boolean = false,
) {
    init {
        require(hosts.isNotEmpty()) {
            "interception over the empty set is the name tier with extra machinery"
        }
    }
}

/**
 * What the NAT in front of this device does to a mapping.
 *
 * The core cannot measure this and it decides whether a QUIC flow can survive.
 * [AddressAndPortDependent] is the conservative answer: it never claims more than
 * is true, at the cost of steering some flows that would have worked.
 */
public enum class NatBehavior(public val code: Int) {
    EndpointIndependent(0),
    AddressDependent(1),
    AddressAndPortDependent(2),
}

/**
 * A `host:port` with a numeric address, which is the only shape the core accepts.
 *
 * Parsed once here so that a malformed resolver is a field error on the screen
 * that owns it rather than a `BOREAS_CONFIG` at start, by which point the user has
 * pressed the button and the message names a struct field.
 */
@JvmInline
public value class Endpoint private constructor(public val text: String) {

    override fun toString(): String = text

    public companion object {
        /** What DNS uses when nobody says otherwise. */
        public const val DNS_PORT: Int = 53

        /** Accepts "1.1.1.1" and "1.1.1.1:5353"; the port defaults to [DNS_PORT]. */
        public fun parse(raw: String, defaultPort: Int = DNS_PORT): Parsed<Endpoint> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return Parsed.Invalid(FieldProblem.Required)

            val separator = trimmed.lastIndexOf(':')
            val hostText = if (separator < 0) trimmed else trimmed.take(separator)
            val portText = if (separator < 0) null else trimmed.substring(separator + 1)

            val port = if (portText == null) {
                defaultPort
            } else {
                portText.toIntOrNull()?.takeIf { it in 1..65_535 }
                    ?: return Parsed.Invalid(FieldProblem.PortRange)
            }

            return when (val host = Ipv4Address.parse(hostText)) {
                is Parsed.Valid -> Parsed.Valid(Endpoint("${host.value.text}:$port"))
                is Parsed.Invalid -> Parsed.Invalid(host.problem)
            }
        }
    }
}

/**
 * A name, not a pattern and not an address.
 *
 * The core rejects an intercepted host that is not a hostname, and carries the
 * offending text back so a user can be told which line to fix. Parsing here means
 * they are told while typing instead.
 */
@JvmInline
public value class Hostname private constructor(public val text: String) {

    override fun toString(): String = text

    public companion object {
        private const val MAX_LENGTH = 253
        private val LABEL = Regex("[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

        public fun parse(raw: String): Parsed<Hostname> {
            // Trailing dot is the root label written out; it names the same host.
            val trimmed = raw.trim().lowercase().trimEnd('.')
            if (trimmed.isEmpty()) return Parsed.Invalid(FieldProblem.Required)
            if (trimmed.length > MAX_LENGTH) return Parsed.Invalid(FieldProblem.HostShape(raw.trim()))

            val labels = trimmed.split('.')
            // A dotted quad parses as labels but is an address; the core would refuse it.
            val numeric = labels.size == 4 && labels.all { it.all(Char::isDigit) }
            if (numeric || labels.any { !LABEL.matches(it) }) {
                return Parsed.Invalid(FieldProblem.HostShape(raw.trim()))
            }
            return Parsed.Valid(Hostname(trimmed))
        }
    }
}

/**
 * Engine policy: what the core does, as distinct from how the interface is shaped.
 *
 * There is one egress. The ABI offers direct and WireGuard, and WireGuard is not
 * modelled here because nothing in this app can produce one: it needs an endpoint
 * and three raw keys that no screen collects. Adding the variant with the screen
 * that fills it is additive; adding it without one would be a state the program
 * can represent and never reach.
 */
public data class EngineConfig(
    val filtering: Filtering = Filtering.Off,
    val nat: NatBehavior = NatBehavior.AddressAndPortDependent,
)

/** Whether a configuration change can reach a running session, or needs a restart. */
public fun EngineConfig.reachesRunning(next: EngineConfig): Boolean {
    // Reload replaces the rules in force and nothing else. The resolver, the
    // intercepted host list, the egress, and the ceilings are fixed at start.
    val here = filtering
    val there = next.filtering
    return nat == next.nat && here is Filtering.Names && there is Filtering.Names &&
        here.upstream == there.upstream && here.interception == there.interception
}

/**
 * Raw policy text, exactly as typed.
 *
 * Kept separate from [EngineConfig] for the same reason [TunnelDraft] is kept
 * separate from [PlatformConfig]: a half-typed resolver has to survive navigation
 * and process death, and it cannot do that inside a type whose whole purpose is to
 * be impossible to construct wrongly.
 */
public data class PolicyDraft(
    val filterNames: Boolean = false,
    val resolver: String = "",
    /** One filter list's text, in AdGuard/uBlock syntax. */
    val rules: String = "",
    val intercept: Boolean = false,
    /** One host per line. An allowlist a person can read. */
    val interceptHosts: String = "",
    val rewriteDocuments: Boolean = false,
    val nat: NatBehavior = NatBehavior.AddressAndPortDependent,
)

/** Parsed policy, or every field problem at once. */
public sealed interface PolicyParse {

    public data class Valid(val config: EngineConfig) : PolicyParse

    public data class Invalid(val problems: Map<TunnelField, FieldProblem>) : PolicyParse {
        init {
            require(problems.isNotEmpty()) { "an invalid parse must name at least one problem" }
        }
    }

    public companion object {

        /**
         * The one boundary raw policy text crosses.
         *
         * Reports every bad field rather than the first, because a form that fixes
         * one error at a time costs a round trip per field. O(n) in the text.
         */
        public fun of(draft: PolicyDraft): PolicyParse {
            if (!draft.filterNames) {
                return Valid(EngineConfig(Filtering.Off, draft.nat))
            }

            val problems = mutableMapOf<TunnelField, FieldProblem>()

            val upstream = when (val parsed = Endpoint.parse(draft.resolver)) {
                is Parsed.Valid -> parsed.value
                is Parsed.Invalid -> null.also { problems[TunnelField.Resolver] = parsed.problem }
            }

            val interception = if (draft.intercept) parseInterception(draft, problems) else null

            // An empty rule set is a resolver that answers everything, which is a
            // legal tunnel and a reasonable first run. Blank text is not an error.
            val lists = draft.rules.trim().let { if (it.isEmpty()) emptyList() else listOf(it) }

            return if (problems.isEmpty() && upstream != null) {
                Valid(EngineConfig(Filtering.Names(upstream, lists, interception), draft.nat))
            } else {
                Invalid(problems)
            }
        }

        private fun parseInterception(
            draft: PolicyDraft,
            problems: MutableMap<TunnelField, FieldProblem>,
        ): Interception? {
            val entries = draft.interceptHosts.split('\n', ',', ' ')
                .map(String::trim)
                .filter(String::isNotEmpty)

            if (entries.isEmpty()) {
                problems[TunnelField.Hosts] = FieldProblem.Required
                return null
            }

            val hosts = buildList {
                for (entry in entries) {
                    when (val parsed = Hostname.parse(entry)) {
                        is Parsed.Valid -> add(parsed.value)
                        is Parsed.Invalid -> {
                            problems[TunnelField.Hosts] = parsed.problem
                            return@buildList
                        }
                    }
                }
            }

            return if (hosts.size == entries.size) {
                Interception(hosts, draft.rewriteDocuments)
            } else {
                null
            }
        }
    }
}

/** The problem for a field, or null when that field parsed. */
public fun PolicyParse.problemFor(field: TunnelField): FieldProblem? = when (this) {
    is PolicyParse.Valid -> null
    is PolicyParse.Invalid -> problems[field]
}

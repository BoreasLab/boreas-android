package dev.boreaslab.boreas.model

/**
 * Policy filtering tiers. Nesting makes document rewriting require interception,
 * interception require name filtering, and name filtering require a resolver.
 */
public sealed interface Filtering {

    /** Carries traffic without filtering. */
    public data object Off : Filtering

    /** Resolves names, applies [lists], and forwards allowed traffic to [upstream]. */
    public data class Names(
        val upstream: Endpoint,
        /** Filter-list text in AdGuard/uBlock syntax; malformed lines are skipped. */
        val lists: List<String>,
        /** Optional interception, absent for the name tier alone. */
        val interception: Interception? = null,
    ) : Filtering
}

/** Terminates TLS for the explicit [hosts] allowlist and filters the requests. */
public data class Interception(
    val hosts: List<Hostname>,
    /** Rewrites HTML bodies within the core's memory budget. */
    val rewriteDocuments: Boolean = false,
) {
    init {
        require(hosts.isNotEmpty()) {
            "interception over the empty set is the name tier with extra machinery"
        }
    }
}

/** NAT mapping behavior used to decide whether a QUIC flow can survive. */
public enum class NatBehavior(public val code: Int) {
    EndpointIndependent(0),
    AddressDependent(1),
    AddressAndPortDependent(2),
}

/** Numeric `host:port` accepted by the core, parsed at the owning form boundary. */
@JvmInline
public value class Endpoint private constructor(public val text: String) {

    override fun toString(): String = text

    public companion object {
        /** Default DNS port. */
        public const val DNS_PORT: Int = 53

        /** Parses an address with an optional port defaulting to [DNS_PORT]. */
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

/** Hostname accepted for interception, rather than a pattern or address. */
@JvmInline
public value class Hostname private constructor(public val text: String) {

    override fun toString(): String = text

    public companion object {
        private const val MAX_LENGTH = 253
        private val LABEL = Regex("[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

        public fun parse(raw: String): Parsed<Hostname> {
            // A trailing dot writes the DNS root label and names the same host.
            val trimmed = raw.trim().lowercase().trimEnd('.')
            if (trimmed.isEmpty()) return Parsed.Invalid(FieldProblem.Required)
            if (trimmed.length > MAX_LENGTH) return Parsed.Invalid(FieldProblem.HostShape(raw.trim()))

            val labels = trimmed.split('.')
            // A dotted quad is an address, not a hostname.
            val numeric = labels.size == 4 && labels.all { it.all(Char::isDigit) }
            if (numeric || labels.any { !LABEL.matches(it) }) {
                return Parsed.Invalid(FieldProblem.HostShape(raw.trim()))
            }
            return Parsed.Valid(Hostname(trimmed))
        }
    }
}

/** Engine policy, independent of the interface draft. WireGuard is not modeled
 * because no screen supplies its endpoint and three raw keys. */
public data class EngineConfig(
    val filtering: Filtering = Filtering.Off,
    val nat: NatBehavior = NatBehavior.AddressAndPortDependent,
)

/** Whether a configuration change can reach a running session without restart. */
public fun EngineConfig.reachesRunning(next: EngineConfig): Boolean {
    // Reload changes only rules; resolver, interception, egress, and ceilings are fixed at start.
    val here = filtering
    val there = next.filtering
    return nat == next.nat && here is Filtering.Names && there is Filtering.Names &&
        here.upstream == there.upstream && here.interception == there.interception
}

/** Raw policy text kept separate from trusted [EngineConfig] for process death and navigation. */
public data class PolicyDraft(
    val filterNames: Boolean = false,
    val resolver: String = "",
    /** Filter-list text in AdGuard/uBlock syntax. */
    val rules: String = "",
    val intercept: Boolean = false,
    /** Intercepted hosts, one per line. */
    val interceptHosts: String = "",
    val rewriteDocuments: Boolean = false,
    val nat: NatBehavior = NatBehavior.AddressAndPortDependent,
)

/** Parsed policy or all field problems. */
public sealed interface PolicyParse {

    public data class Valid(val config: EngineConfig) : PolicyParse

    public data class Invalid(val problems: Map<TunnelField, FieldProblem>) : PolicyParse {
        init {
            require(problems.isNotEmpty()) { "an invalid parse must name at least one problem" }
        }
    }

    public companion object {

        /** Parses raw policy text and reports every invalid field in O(n) time. */
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

            // Blank rules are valid: the resolver then answers every name.
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

/** Returns a field's problem, or null when it parsed. */
public fun PolicyParse.problemFor(field: TunnelField): FieldProblem? = when (this) {
    is PolicyParse.Valid -> null
    is PolicyParse.Invalid -> problems[field]
}

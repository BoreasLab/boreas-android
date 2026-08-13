package dev.boreaslab.boreas.model

/**
 * Configuration values, parsed once at the untrusted entry and immutable after.
 *
 * The core contract requires each configuration to be "parsed once at its untrusted
 * entry" and to "become an immutable trusted value before the service starts". The
 * types below carry that rule: [Mtu] and [Ipv4Address] have private constructors,
 * so the only way to hold one is to have parsed it, and a screen cannot hand the
 * service a value it never validated.
 */

/** A validated result, or the one problem that stopped it validating. */
public sealed interface Parsed<out T> {
    public data class Valid<T>(val value: T) : Parsed<T>
    public data class Invalid(val problem: FieldProblem) : Parsed<Nothing>
}

/** Every way a field can be wrong. The UI maps each to copy naming the fix. */
public sealed interface FieldProblem {
    public data object Required : FieldProblem
    public data object AddressShape : FieldProblem
    public data object AddressRange : FieldProblem
    public data object MtuShape : FieldProblem
    public data object MtuRange : FieldProblem
    public data class DnsShape(val entry: String) : FieldProblem
}

/** Fields the tunnel form can report a problem against. */
public enum class TunnelField { Address, Mtu, Dns }

@JvmInline
public value class Ipv4Address private constructor(public val text: String) {
    override fun toString(): String = text

    public companion object {
        /**
         * Parses liberally. Surrounding whitespace, a stray leading or trailing dot,
         * and zero padding are all normalized rather than rejected, because
         * reformatting is the system's job and not the reader's.
         */
        public fun parse(raw: String): Parsed<Ipv4Address> {
            val trimmed = raw.trim().trim('.')
            if (trimmed.isEmpty()) return Parsed.Invalid(FieldProblem.Required)

            val parts = trimmed.split('.')
            if (parts.size != 4 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) {
                return Parsed.Invalid(FieldProblem.AddressShape)
            }

            // Every part is now known to be a non-empty run of digits, so the only
            // remaining way to be wrong is to be too large. Length is checked before
            // conversion because "99999999999" overflows Int: converting first would
            // report a well-formed but oversized octet as a shape problem, and send
            // the reader to fix punctuation that was never wrong.
            if (parts.any { it.trimStart('0').length > 3 }) {
                return Parsed.Invalid(FieldProblem.AddressRange)
            }
            val numbers = parts.map(String::toInt)
            if (numbers.any { it > 255 }) return Parsed.Invalid(FieldProblem.AddressRange)

            return Parsed.Valid(Ipv4Address(numbers.joinToString(".")))
        }
    }
}

@JvmInline
public value class Mtu private constructor(public val bytes: Int) {
    public companion object {
        public const val MINIMUM: Int = 1280
        public const val MAXIMUM: Int = 9000

        /** Accepts digit grouping and surrounding whitespace, for example " 1,500 ". */
        public fun parse(raw: String): Parsed<Mtu> {
            val cleaned = raw.trim().replace(",", "").replace(" ", "")
            if (cleaned.isEmpty()) return Parsed.Invalid(FieldProblem.Required)

            val value = cleaned.toIntOrNull() ?: return Parsed.Invalid(FieldProblem.MtuShape)
            if (value < MINIMUM || value > MAXIMUM) return Parsed.Invalid(FieldProblem.MtuRange)

            return Parsed.Valid(Mtu(value))
        }
    }
}

/**
 * What the tunnel form holds while the reader is typing.
 *
 * Kept as raw text so a half-typed value is never destroyed by validation, and so
 * the entry survives navigation and process death unchanged.
 */
public data class TunnelDraft(
    val address: String = "10.24.0.2",
    val mtu: String = "1500",
    val dns: String = "",
) {
    public companion object {
        public fun of(config: PlatformConfig): TunnelDraft = TunnelDraft(
            address = config.address.text,
            mtu = config.mtu.bytes.toString(),
            dns = config.dnsServers.joinToString("\n") { it.text },
        )
    }
}

/**
 * The outcome of parsing a whole draft.
 *
 * A sum rather than a nullable value beside a problem map: with a product, every
 * consumer has to re-derive "did this work" from a null check, and one of them
 * will branch on it differently. Here the two outcomes are eliminated
 * exhaustively and a trusted [PlatformConfig] exists only on the branch that has
 * one.
 */
public sealed interface TunnelParse {

    public data class Valid(val config: PlatformConfig) : TunnelParse

    public data class Invalid(val problems: Map<TunnelField, FieldProblem>) : TunnelParse {
        init {
            require(problems.isNotEmpty()) { "an invalid parse must name at least one problem" }
        }
    }

    public companion object {
        public fun of(draft: TunnelDraft, excludedPackages: Set<String>): TunnelParse =
            PlatformConfig.parse(draft, excludedPackages)
    }
}

/** The problems for a field, or null when that field parsed. */
public fun TunnelParse.problemFor(field: TunnelField): FieldProblem? = when (this) {
    is TunnelParse.Valid -> null
    is TunnelParse.Invalid -> problems[field]
}

/**
 * Android addressing for the tunnel interface. Owns no filtering policy.
 *
 * Routes are fixed to the whole address space here. Route selection is A3 work in
 * docs/implementation-plan.md and needs a device gate, so it is not exposed as a
 * control that would imply it had been tested.
 */
public data class PlatformConfig(
    val address: Ipv4Address,
    val mtu: Mtu,
    val dnsServers: List<Ipv4Address>,
    val excludedPackages: Set<String>,
) {
    public companion object {
        public fun parse(draft: TunnelDraft, excludedPackages: Set<String>): TunnelParse {
            val problems = mutableMapOf<TunnelField, FieldProblem>()

            val address = when (val r = Ipv4Address.parse(draft.address)) {
                is Parsed.Valid -> r.value
                is Parsed.Invalid -> null.also { problems[TunnelField.Address] = r.problem }
            }

            val mtu = when (val r = Mtu.parse(draft.mtu)) {
                is Parsed.Valid -> r.value
                is Parsed.Invalid -> null.also { problems[TunnelField.Mtu] = r.problem }
            }

            // Empty means "keep whatever the network supplies", which is valid.
            val dnsEntries = draft.dns.split('\n', ',', ' ')
                .map(String::trim)
                .filter(String::isNotEmpty)
            val dns = buildList {
                for (entry in dnsEntries) {
                    when (val r = Ipv4Address.parse(entry)) {
                        is Parsed.Valid -> add(r.value)
                        is Parsed.Invalid -> {
                            problems[TunnelField.Dns] = FieldProblem.DnsShape(entry)
                            return@buildList
                        }
                    }
                }
            }

            return if (problems.isEmpty() && address != null && mtu != null) {
                TunnelParse.Valid(PlatformConfig(address, mtu, dns, excludedPackages))
            } else {
                TunnelParse.Invalid(problems)
            }
        }
    }
}

/** How much the engine acts on what it sees. */
public enum class RuleProfile { Off, Standard, Strict }

/**
 * Policy and egress choices handed to the engine at start.
 *
 * The field set is provisional. The core contract defers naming and serialization
 * to the core FFI crate, so these must be reconciled against the versioned core
 * interface in A2 rather than treated as settled.
 */
public data class EngineConfig(
    val profile: RuleProfile = RuleProfile.Standard,
    val inspectTls: Boolean = false,
    val upstream: UpstreamRoute = UpstreamRoute.Direct,
)

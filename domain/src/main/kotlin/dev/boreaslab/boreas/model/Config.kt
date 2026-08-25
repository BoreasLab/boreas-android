package dev.boreaslab.boreas.model

/** Parsed once at the untrusted boundary, then immutable. */

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
    public data object PortRange : FieldProblem
    public data class DnsShape(val entry: String) : FieldProblem
    public data class HostShape(val entry: String) : FieldProblem
}

/** Fields a form can report a problem against. */
public enum class TunnelField { Address, Mtu, Dns, Resolver, Hosts }

@JvmInline
public value class Ipv4Address private constructor(public val text: String) {
    override fun toString(): String = text

    public companion object {
        /** Normalizes surrounding whitespace, edge dots, and zero padding. */
        public fun parse(raw: String): Parsed<Ipv4Address> {
            val trimmed = raw.trim().trim('.')
            if (trimmed.isEmpty()) return Parsed.Invalid(FieldProblem.Required)

            val parts = trimmed.split('.')
            if (parts.size != 4 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) {
                return Parsed.Invalid(FieldProblem.AddressShape)
            }

            // Check digit length before Int conversion so oversized octets report range, not shape.
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

/** Raw form text preserves half-typed values across validation and process death. */
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

/** Parsed configuration or field problems. */
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
 * The interface itself: what Android is told to build, and what the core is told
 * it was given.
 *
 * [mtu] is the number that must appear twice. api/obligations.md names the two
 * silent mistakes, and this is one of them: `Builder.setMtu(n)` and the core's
 * `mtu` must be the same `n`, or the tunnel works and spends its time answering
 * Packet Too Big to senders that never converge. One field, read by both call
 * sites, is what makes them agree by construction rather than by review.
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

            // Empty means use network-provided DNS.
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


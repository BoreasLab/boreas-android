package dev.boreaslab.boreas.core

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.sun.jna.Structure

/* These structs follow the widths and field order in api/abi.md. @FieldOrder
 * is explicit because the JVM does not promise declaration order, and R8 must
 * retain the fields and annotation or offsets can change without a diagnostic.
 * C `bool` is one byte, represented here by `Byte` and explicit zero comparisons.
 */

/** Exposes JNA's protected offset lookup for [BoreasLayoutTest]. */
internal abstract class BoreasStruct : Structure() {
    fun offsetOf(field: String): Int = fieldOffset(field)
}

/** `BoreasBypass`: protects sockets from re-entering the tunnel. */
@Structure.FieldOrder("context", "protect", "release")
internal class BoreasBypass : BoreasStruct() {

    @JvmField var context: Pointer? = null

    @JvmField var protect: Protect? = null

    @JvmField var release: Release? = null

    /** Excludes one socket: `0` on success, negative on refusal. */
    fun interface Protect : Callback {
        /** `BoreasSocket` is `int64_t` so it can hold a Unix fd or Windows handle. */
        fun invoke(context: Pointer?, socket: Long): Int
    }

    fun interface Release : Callback {
        fun invoke(context: Pointer?)
    }
}

/** `BoreasWireGuard`, present in the ABI but not populated by this app. */
@Structure.FieldOrder("endpoint", "privateKey", "peerPublicKey", "presharedKey", "hasPresharedKey")
internal class BoreasWireGuard : BoreasStruct() {

    @JvmField var endpoint: Pointer? = null

    @JvmField var privateKey: ByteArray = ByteArray(KEY_BYTES)

    @JvmField var peerPublicKey: ByteArray = ByteArray(KEY_BYTES)

    @JvmField var presharedKey: ByteArray = ByteArray(KEY_BYTES)

    /** Separate flag because thirty-two zero bytes can be a configured key. */
    @JvmField var hasPresharedKey: Byte = 0

    internal companion object {
        const val KEY_BYTES = 32
    }
}

/** `BoreasCeilings`. Zero selects the core default; all-zero is valid. */
@Structure.FieldOrder(
    "bufferSlices",
    "datagramsPerFlow",
    "terminatedConnections",
    "associations",
    "inspectedAddresses",
    "pendingReassemblies",
)
internal class BoreasCeilings : BoreasStruct() {
    @JvmField var bufferSlices: SizeT = SizeT.ZERO
    @JvmField var datagramsPerFlow: SizeT = SizeT.ZERO
    @JvmField var terminatedConnections: SizeT = SizeT.ZERO
    @JvmField var associations: SizeT = SizeT.ZERO
    @JvmField var inspectedAddresses: SizeT = SizeT.ZERO
    @JvmField var pendingReassemblies: SizeT = SizeT.ZERO
}

/** `BoreasConfig`. Every pointer is borrowed for the start call. */
@Structure.FieldOrder(
    "egress",
    "wireguard",
    "natBehavior",
    "resolver",
    "lists",
    "listCount",
    "interceptHosts",
    "interceptHostCount",
    "rootCertificate",
    "rootCertificateLength",
    "authorityKeys",
    "authorityKeysLength",
    "rewriteDocuments",
    "mtu",
    "ceilings",
)
internal class BoreasConfig : BoreasStruct() {

    /** `BOREAS_EGRESS_DIRECT` (0) or `BOREAS_EGRESS_WIREGUARD` (1). */
    @JvmField var egress: Int = EGRESS_DIRECT

    /** Read only when [egress] is WireGuard; present in the layout either way. */
    @JvmField var wireguard: BoreasWireGuard = BoreasWireGuard()

    /** Read only when [egress] is direct. */
    @JvmField var natBehavior: Int = 0

    /** `host:port` to filter through, or null to forward questions untouched. */
    @JvmField var resolver: Pointer? = null

    @JvmField var lists: Pointer? = null
    @JvmField var listCount: SizeT = SizeT.ZERO

    /** Allowlist, never a pattern. Zero means no interception and no authority. */
    @JvmField var interceptHosts: Pointer? = null
    @JvmField var interceptHostCount: SizeT = SizeT.ZERO

    @JvmField var rootCertificate: Pointer? = null
    @JvmField var rootCertificateLength: SizeT = SizeT.ZERO
    @JvmField var authorityKeys: Pointer? = null
    @JvmField var authorityKeysLength: SizeT = SizeT.ZERO

    @JvmField var rewriteDocuments: Byte = 0

    /** Must equal the interface MTU. See api/obligations.md. */
    @JvmField var mtu: Short = 0

    @JvmField var ceilings: BoreasCeilings = BoreasCeilings()

    internal companion object {
        const val EGRESS_DIRECT = 0
    }
}

/** `BoreasCounters`. Fields report drops, refusals, and other failures. */
@Structure.FieldOrder(
    "datagramsDropped",
    "packetsRejected",
    "quicSteered",
    "pathsReported",
    "eventsLost",
    "tasksPanicked",
)
internal class BoreasCounters : BoreasStruct() {
    @JvmField var datagramsDropped: Long = 0
    @JvmField var packetsRejected: Long = 0
    @JvmField var quicSteered: Long = 0
    @JvmField var pathsReported: Long = 0
    @JvmField var eventsLost: Long = 0
    @JvmField var tasksPanicked: Long = 0
}

/**
 * `BoreasEvent` stores every arm's fields beside its tag rather than in a union.
 * Only fields named by `kind` carry meaning. `blocked` must remain at offset four;
 * `-fshort-enums` would shrink the tag and shift the remaining fields.
 */
@Structure.FieldOrder(
    "kind",
    "blocked",
    "nameLength",
    "ruleLength",
    "allowed",
    "blockedRules",
    "inspected",
    "counters",
)
internal class BoreasEvent : BoreasStruct() {
    @JvmField var kind: Int = 0
    @JvmField var blocked: Byte = 0

    /** Full length before truncation; larger than capacity means it did not fit. */
    @JvmField var nameLength: SizeT = SizeT.ZERO
    @JvmField var ruleLength: SizeT = SizeT.ZERO

    @JvmField var allowed: SizeT = SizeT.ZERO
    @JvmField var blockedRules: SizeT = SizeT.ZERO
    @JvmField var inspected: SizeT = SizeT.ZERO
    @JvmField var counters: BoreasCounters = BoreasCounters()

    internal companion object {
        const val KIND_RESOLVED = 0
        const val KIND_RELOADED = 1
        const val KIND_COUNTED = 2
    }
}

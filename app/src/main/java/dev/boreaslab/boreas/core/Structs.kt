package dev.boreaslab.boreas.core

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.sun.jna.Structure

/*
 * The five structs that cross the boundary, at the widths api/abi.md names.
 *
 * Field *order* is the contract; the names are ours, so they read as Kotlin. Every
 * class carries @FieldOrder rather than relying on the order the JVM happens to
 * report its fields in, which it does not promise. R8 is told to keep both the
 * fields and the annotation, because a renamed field is a struct whose members
 * land at the wrong offsets and there is no diagnostic for that.
 *
 * `bool` is one byte, so every one of them is a `Byte` compared against zero. This
 * is the trap the header calls out; Kotlin has no automatic marshalling to get it
 * wrong, but it also has no automatic marshalling to get it right.
 */

/**
 * A struct at this boundary, with its layout readable.
 *
 * The header pins every offset with a static assertion, which protects a host
 * that compiles it. This app does not: JNA computes the layout at run time from
 * the declarations below, and nothing would check them until a device read a
 * field from the middle of another one. `fieldOffset` is protected in JNA, so
 * exposing it here is what lets `BoreasLayoutTest` assert the same numbers on a
 * plain JVM.
 *
 * It declares no fields of its own, so it does not appear in any layout.
 */
internal abstract class BoreasStruct : Structure() {
    fun offsetOf(field: String): Int = fieldOffset(field)
}

/** `BoreasDevice`: the TUN this app supplies. Every callback runs on a core thread. */
@Structure.FieldOrder("context", "recv", "send", "close", "release", "mtu")
internal class BoreasDevice : BoreasStruct() {

    @JvmField var context: Pointer? = null

    @JvmField var recv: Recv? = null

    @JvmField var send: Send? = null

    @JvmField var close: Close? = null

    @JvmField var release: Release? = null

    /** Must be at least 1280, the IPv6 floor, and equal to `BoreasConfig.mtu`. */
    @JvmField var mtu: Short = 0

    /** Reads one IP packet. Returns the count, `0` for "ask again", or a negative errno. */
    fun interface Recv : Callback {
        fun invoke(context: Pointer?, buffer: Pointer, capacity: SizeT): SSizeT
    }

    /** Writes one IP packet, whole. Returns `0` or a negative errno; a short write is an error. */
    fun interface Send : Callback {
        fun invoke(context: Pointer?, buffer: Pointer, length: SizeT): SSizeT
    }

    /** Makes an in-flight [Recv] return. May run *while* one is blocked. */
    fun interface Close : Callback {
        fun invoke(context: Pointer?)
    }

    /** Runs once, after every other callback has returned. */
    fun interface Release : Callback {
        fun invoke(context: Pointer?)
    }
}

/**
 * `BoreasBypass`: sockets that do not re-enter the tunnel.
 *
 * The obligation that is silent when it is skipped. An unprotected socket works
 * perfectly until the tunnel comes up, at which point every packet it sends
 * re-enters the tunnel it was serving.
 */
@Structure.FieldOrder("context", "protect", "release")
internal class BoreasBypass : BoreasStruct() {

    @JvmField var context: Pointer? = null

    @JvmField var protect: Protect? = null

    @JvmField var release: Release? = null

    /** Excludes one socket. Returns `0` on success, negative on refusal. */
    fun interface Protect : Callback {
        /** `BoreasSocket` is `int64_t`: one type has to hold a Unix fd and a Windows handle. */
        fun invoke(context: Pointer?, socket: Long): Int
    }

    fun interface Release : Callback {
        fun invoke(context: Pointer?)
    }
}

/** `BoreasWireGuard`. Present because the struct is, not because anything fills it in. */
@Structure.FieldOrder("endpoint", "privateKey", "peerPublicKey", "presharedKey", "hasPresharedKey")
internal class BoreasWireGuard : BoreasStruct() {

    @JvmField var endpoint: Pointer? = null

    @JvmField var privateKey: ByteArray = ByteArray(KEY_BYTES)

    @JvmField var peerPublicKey: ByteArray = ByteArray(KEY_BYTES)

    @JvmField var presharedKey: ByteArray = ByteArray(KEY_BYTES)

    /** A separate flag because thirty-two zero bytes is a key someone may have configured. */
    @JvmField var hasPresharedKey: Byte = 0

    internal companion object {
        const val KEY_BYTES = 32
    }
}

/** `BoreasCeilings`. Zero in any field means "use the default for it", so all-zero is valid. */
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

/** `BoreasConfig`. Every pointer in it is borrowed for the duration of the start call. */
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

    /** Read only when [egress] is WireGuard, but present in the layout either way. */
    @JvmField var wireguard: BoreasWireGuard = BoreasWireGuard()

    /** Read only when [egress] is direct. */
    @JvmField var natBehavior: Int = 0

    /** `host:port` to filter through, or null to forward questions untouched. */
    @JvmField var resolver: Pointer? = null

    @JvmField var lists: Pointer? = null
    @JvmField var listCount: SizeT = SizeT.ZERO

    /** An allowlist, never a pattern. Zero means no interception and needs no authority. */
    @JvmField var interceptHosts: Pointer? = null
    @JvmField var interceptHostCount: SizeT = SizeT.ZERO

    @JvmField var rootCertificate: Pointer? = null
    @JvmField var rootCertificateLength: SizeT = SizeT.ZERO
    @JvmField var authorityKeys: Pointer? = null
    @JvmField var authorityKeysLength: SizeT = SizeT.ZERO

    @JvmField var rewriteDocuments: Byte = 0

    /** The same number the interface was built with. See api/obligations.md. */
    @JvmField var mtu: Short = 0

    @JvmField var ceilings: BoreasCeilings = BoreasCeilings()

    internal companion object {
        const val EGRESS_DIRECT = 0
    }
}

/** `BoreasCounters`. Every field is something that went wrong or was refused. */
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
 * `BoreasEvent`. A tag and every arm's fields side by side rather than a union.
 *
 * Only the fields `kind` names carry meaning; the rest are zero. `blocked` sits at
 * offset four, which is the field the header's static assertions exist to protect:
 * under `-fshort-enums` the tag would be one byte and everything after it would
 * shift while both sides still compiled.
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

    /** The *full* length before truncation; larger than the capacity means it did not fit. */
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

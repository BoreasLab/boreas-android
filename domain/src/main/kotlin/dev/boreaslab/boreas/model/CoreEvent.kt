package dev.boreaslab.boreas.model

/**
 * Everything the core says about a running tunnel.
 *
 * The event stream is the whole diagnostic surface: the core opens no files, reads
 * no environment, and does not log. So this sum is not a convenience over some
 * richer channel, it is the channel, and a fact absent from it is a fact the
 * platform cannot have.
 *
 * A healthy idle tunnel emits nothing at all. Silence means "nothing went wrong",
 * never "not running", and no screen may read it the second way.
 */
public sealed interface CoreEvent {

    /**
     * One DNS question answered. [blocked] means policy answered it and nothing
     * left the device.
     *
     * [rule] is absent when no rule decided the outcome, which is the ordinary
     * case for a name that was simply allowed.
     */
    public data class Resolved(
        val name: String,
        val rule: String?,
        val blocked: Boolean,
        /** The core had more text than the buffer held. Names cannot; a long rule can. */
        val truncated: Boolean,
    ) : CoreEvent

    /** A rule set took effect. Reports the size of what is now in force. */
    public data class Reloaded(val rules: RuleSetSize) : CoreEvent

    /** Occurrences since the previous [Counted], which is why they are summed. */
    public data class Counted(val counters: CoreCounters) : CoreEvent
}

/** How much is in force after a reload. */
public data class RuleSetSize(
    val allowed: Long,
    val blocked: Long,
    val inspected: Long,
)

/**
 * The core's failure counters.
 *
 * Every field counts something that went wrong or was refused, so a tunnel working
 * normally reports [ZERO] and any non-zero field is worth surfacing without
 * knowing what it means.
 *
 * ([ZERO], [plus]) is a monoid: `Long` addition is associative and 0 is its
 * identity, field by field. That is what makes folding the stream correct, and it
 * is correct only because a `Counted` event reports occurrences *since the
 * previous one* rather than a running total. Summing running totals would
 * multiply them; diffing deltas would lose them.
 */
public data class CoreCounters(
    /** Ceilings too small for this device's traffic. */
    val datagramsDropped: Long = 0,
    /** Something upstream is producing malformed packets. */
    val packetsRejected: Long = 0,
    /** Expected while intercepting: browsers pushed off HTTP/3 so traffic is inspectable. */
    val quicSteered: Long = 0,
    /** A misconfiguration: the interface's MTU is wider than the one the core was told. */
    val pathsReported: Long = 0,
    /** Events were not read fast enough, counted so a gap never reads as quiet. */
    val eventsLost: Long = 0,
    /** A defect in the core, not a condition of the network. */
    val tasksPanicked: Long = 0,
) {

    public operator fun plus(other: CoreCounters): CoreCounters = CoreCounters(
        datagramsDropped = datagramsDropped + other.datagramsDropped,
        packetsRejected = packetsRejected + other.packetsRejected,
        quicSteered = quicSteered + other.quicSteered,
        pathsReported = pathsReported + other.pathsReported,
        eventsLost = eventsLost + other.eventsLost,
        tasksPanicked = tasksPanicked + other.tasksPanicked,
    )

    /** Nothing to report. The ordinary state of a working tunnel. */
    public val quiet: Boolean get() = this == ZERO

    public companion object {
        public val ZERO: CoreCounters = CoreCounters()
    }
}

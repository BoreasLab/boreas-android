package dev.boreaslab.boreas.model

/**
 * Complete diagnostic surface for a running tunnel. A healthy idle tunnel emits
 * nothing; silence means no failure, not that the tunnel is stopped.
 */
public sealed interface CoreEvent {

    /** One DNS question answered; [rule] is absent when no rule decided it. */
    public data class Resolved(
        val name: String,
        val rule: String?,
        val blocked: Boolean,
        /** The core had more text than the buffer held. Names cannot; a long rule can. */
        val truncated: Boolean,
    ) : CoreEvent

    /** A rule set took effect and reports its size. */
    public data class Reloaded(val rules: RuleSetSize) : CoreEvent

    /** Occurrences since the previous [Counted]. */
    public data class Counted(val counters: CoreCounters) : CoreEvent
}

/** Counts of rules in force after a reload. */
public data class RuleSetSize(
    val allowed: Long,
    val blocked: Long,
    val inspected: Long,
)

/**
 * Core failure counters. [ZERO] and [plus] form a fieldwise additive monoid:
 * `Counted` reports deltas since the previous event, so folding sums each field
 * without double-counting.
 */
public data class CoreCounters(
    /** Traffic exceeded a configured ceiling. */
    val datagramsDropped: Long = 0,
    /** Upstream produced malformed packets. */
    val packetsRejected: Long = 0,
    /** HTTP/3 traffic steered for inspection. */
    val quicSteered: Long = 0,
    /** Interface MTU differs from the core's MTU. */
    val pathsReported: Long = 0,
    /** Events lost because they were not read fast enough. */
    val eventsLost: Long = 0,
    /** Core task panicked. */
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

    /** No failures to report. */
    public val quiet: Boolean get() = this == ZERO

    public companion object {
        public val ZERO: CoreCounters = CoreCounters()
    }
}

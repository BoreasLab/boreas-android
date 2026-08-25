package dev.boreaslab.boreas.model

/** Opaque identity of one engine session. */
@JvmInline
public value class SessionId(public val value: String)

/**
 * What is known about a running session, and nothing else.
 *
 * Every field here is folded from the event stream, because the event stream is
 * the only thing the core reports. There are deliberately no byte or flow
 * counters: the ABI exposes none, so a screen showing them would be showing a
 * number this program invented.
 *
 * [simulated] keeps generated values from being read as measured traffic.
 */
public data class SessionStatus(
    val startedAtMillis: Long,
    /** DNS questions policy let through. */
    val namesAllowed: Long,
    /** DNS questions answered from policy, with nothing leaving the device. */
    val namesBlocked: Long,
    /** Absent until the first reload reports what is in force. */
    val rules: RuleSetSize?,
    val counters: CoreCounters,
    val simulated: Boolean,
) {

    /**
     * Folds one event in. Total, pure, and O(1).
     *
     * The counters arm sums rather than replaces because a `Counted` event carries
     * occurrences since the previous one; see [CoreCounters]. The rules arm
     * replaces rather than sums because a reload takes a whole set, never a delta,
     * so the newest report *is* what is in force.
     */
    public fun after(event: CoreEvent): SessionStatus = when (event) {
        is CoreEvent.Resolved ->
            if (event.blocked) copy(namesBlocked = namesBlocked + 1) else copy(namesAllowed = namesAllowed + 1)
        is CoreEvent.Reloaded -> copy(rules = event.rules)
        is CoreEvent.Counted -> copy(counters = counters + event.counters)
    }

    public companion object {
        public fun initial(startedAtMillis: Long, simulated: Boolean): SessionStatus =
            SessionStatus(
                startedAtMillis = startedAtMillis,
                namesAllowed = 0,
                namesBlocked = 0,
                rules = null,
                counters = CoreCounters.ZERO,
                simulated = simulated,
            )
    }
}

/** One answered question, kept for the activity list. The clock is the shell's. */
public data class ResolvedName(
    val atMillis: Long,
    val name: String,
    val rule: String?,
    val blocked: Boolean,
    val truncated: Boolean,
)

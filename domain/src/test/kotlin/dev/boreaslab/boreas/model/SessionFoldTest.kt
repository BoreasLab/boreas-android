package dev.boreaslab.boreas.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The event fold, and the monoid it rests on.
 *
 * Summing counters is only correct because a `Counted` event carries occurrences
 * *since the previous one*. These are the laws that makes true, checked rather
 * than asserted in a comment: identity, associativity, and the fold's own
 * behaviour on each arm of the sum.
 */
class SessionFoldTest {

    private val initial = SessionStatus.initial(startedAtMillis = 0, simulated = false)

    private val a = CoreCounters(datagramsDropped = 1, quicSteered = 2)
    private val b = CoreCounters(quicSteered = 3, eventsLost = 5)
    private val c = CoreCounters(tasksPanicked = 7)

    @Test
    fun `zero is the identity on both sides`() {
        assertEquals(a, a + CoreCounters.ZERO)
        assertEquals(a, CoreCounters.ZERO + a)
    }

    @Test
    fun `addition is associative, which is what makes the order of a fold irrelevant`() {
        assertEquals((a + b) + c, a + (b + c))
    }

    @Test
    fun `a tunnel with nothing to report is quiet, and one field is enough to break that`() {
        assertTrue(CoreCounters.ZERO.quiet)
        assertTrue(!CoreCounters(eventsLost = 1).quiet)
    }

    @Test
    fun `a blocked answer counts against blocked and an allowed one against allowed`() {
        val folded = initial
            .after(resolved("ads.example.net", blocked = true))
            .after(resolved("example.com", blocked = false))
            .after(resolved("metrics.example.com", blocked = true))

        assertEquals(2, folded.namesBlocked)
        assertEquals(1, folded.namesAllowed)
    }

    @Test
    fun `counters accumulate rather than replace, because each report is a delta`() {
        val folded = initial
            .after(CoreEvent.Counted(a))
            .after(CoreEvent.Counted(b))

        assertEquals(1, folded.counters.datagramsDropped)
        assertEquals(5, folded.counters.quicSteered)
        assertEquals(5, folded.counters.eventsLost)
    }

    @Test
    fun `a reload replaces rather than accumulates, because it carries a whole set`() {
        val folded = initial
            .after(CoreEvent.Reloaded(RuleSetSize(allowed = 4, blocked = 90_000, inspected = 2)))
            .after(CoreEvent.Reloaded(RuleSetSize(allowed = 1, blocked = 12, inspected = 0)))

        assertEquals(RuleSetSize(1, 12, 0), folded.rules)
    }

    @Test
    fun `nothing claims to know the rule set until a reload has said so`() {
        assertNull(initial.rules)
        assertNull(initial.after(resolved("example.com", blocked = false)).rules)
    }

    @Test
    fun `folding leaves everything the event does not name alone`() {
        val folded = initial.after(CoreEvent.Counted(a))

        assertEquals(initial.startedAtMillis, folded.startedAtMillis)
        assertEquals(initial.simulated, folded.simulated)
        assertEquals(0, folded.namesAllowed)
        assertEquals(0, folded.namesBlocked)
    }

    private fun resolved(name: String, blocked: Boolean) =
        CoreEvent.Resolved(name, rule = if (blocked) "||$name^" else null, blocked = blocked, truncated = false)
}

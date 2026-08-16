package dev.boreaslab.boreas.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Navigation invariants not enforced by route types. */
class DestinationTest {

    private val all: List<Destination> =
        Destination.TopLevel.entries + Destination.Detail.entries

    @Test
    fun `every destination has a distinct route`() {
        val routes = all.map { it.route }
        assertEquals("two destinations share a route", routes.size, routes.toSet().size)
    }

    @Test
    fun `every destination has a distinct label`() {
        val labels = all.map { it.label }
        assertEquals("two destinations share a title", labels.size, labels.toSet().size)
    }

    @Test
    fun `routes carry no argument placeholder or query`() {
        // A placeholder would mean the graph supplies no required argument.
        all.forEach { destination ->
            assertTrue(
                "${destination.route} looks parameterized",
                destination.route.none { it in "{}?&=" },
            )
        }
    }

    @Test
    fun `a detail route is nested under the top-level destination it is reached from`() {
        // Route names its Settings owner, keeping deep links and back stack aligned.
        Destination.Detail.entries.forEach { detail ->
            assertTrue(
                "${detail.route} is not under settings/",
                detail.route.startsWith("${Destination.TopLevel.Settings.route}/"),
            )
        }
    }

    @Test
    fun `the bar has enough peers to be a bar and few enough to stay legible`() {
        val count = Destination.TopLevel.entries.size
        assertTrue("a navigation bar needs three to five peers, found $count", count in 3..5)
    }
}

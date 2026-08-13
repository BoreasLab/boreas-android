package dev.boreaslab.boreas.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigation graph's shape.
 *
 * A route is a string, so nothing in the type system stops two destinations from
 * claiming the same one: the graph would build, and one of them would simply never
 * be reachable. These hold what the types cannot.
 *
 * The bar's contents are no longer asserted here. `TopLevel.entries` is generated
 * from the declaration, so the list and the membership are the same thing and
 * there is nothing left for a test to catch.
 */
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
        // Two screens under one title is a navigation bug that renders correctly.
        val labels = all.map { it.label }
        assertEquals("two destinations share a title", labels.size, labels.toSet().size)
    }

    @Test
    fun `routes carry no argument placeholder or query`() {
        // Every route here is a constant. A placeholder would mean the destination
        // takes an argument that nothing in the graph supplies.
        all.forEach { destination ->
            assertTrue(
                "${destination.route} looks parameterized",
                destination.route.none { it in "{}?&=" },
            )
        }
    }

    @Test
    fun `a detail route is nested under the top-level destination it is reached from`() {
        // The reader gets to these from Settings, and the route says so, which is
        // what keeps a deep link and the back stack telling the same story.
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

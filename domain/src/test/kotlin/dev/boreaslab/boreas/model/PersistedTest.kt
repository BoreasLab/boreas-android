package dev.boreaslab.boreas.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The storage format.
 *
 * A stored token is a compatibility promise: it was written by a build that has
 * already shipped and has to be readable by every build after it. The tests below
 * hold the two properties that promise rests on. The tokens are distinct, so two
 * constants cannot collide and silently become each other. And they are written
 * out rather than derived, so this file fails when someone renames a constant and
 * changes the format by accident, which is exactly the review the change deserves.
 *
 * These are every persisted set there is. The theme used to be a third, and is now
 * read from the system on each composition instead of stored.
 */
class PersistedTest {

    private val sets: Map<String, List<Persisted>> = mapOf(
        "RuleProfile" to RuleProfile.entries,
        "UpstreamRoute" to UpstreamRoute.entries,
    )

    @Test
    fun `tokens are distinct within a set`() {
        sets.forEach { (name, values) ->
            val tokens = values.map { it.wire }
            assertEquals("$name has a duplicate token", tokens.size, tokens.toSet().size)
        }
    }

    @Test
    fun `tokens are non-empty and carry no whitespace`() {
        sets.forEach { (name, values) ->
            values.forEach { value ->
                assertTrue("$name has a blank token", value.wire.isNotBlank())
                assertEquals("$name token is not trimmed", value.wire.trim(), value.wire)
                assertTrue(
                    "$name token contains whitespace",
                    value.wire.none(Char::isWhitespace),
                )
            }
        }
    }

    @Test
    fun `every token reads back as the value that wrote it`() {
        sets.forEach { (name, values) ->
            values.forEach { value ->
                assertEquals(name, value, value.wire.toPersisted(values, values.first()))
            }
        }
    }

    @Test
    fun `an unknown or missing token resolves to the fallback rather than throwing`() {
        assertEquals(RuleProfile.Standard, null.toPersisted(RuleProfile.entries, RuleProfile.Standard))
        assertEquals(RuleProfile.Standard, "".toPersisted(RuleProfile.entries, RuleProfile.Standard))
        assertEquals(
            RuleProfile.Standard,
            "a profile from a later version".toPersisted(RuleProfile.entries, RuleProfile.Standard),
        )
    }

    @Test
    fun `the token is not the identifier, so a rename cannot change the format`() {
        // Written out, so this assertion is what a format change has to walk past.
        assertEquals("off", RuleProfile.Off.wire)
        assertEquals("standard", RuleProfile.Standard.wire)
        assertEquals("strict", RuleProfile.Strict.wire)
        assertEquals("direct", UpstreamRoute.Direct.wire)
        assertEquals("proxy", UpstreamRoute.Proxy.wire)
    }
}

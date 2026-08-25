package dev.boreaslab.boreas.release

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the tag algebra's representation and ordering laws. */
class TagAlgebraTest {

    // Fixed instant used by the expected tag strings.
    private val noon = Instant.parse("2026-08-25T11:30:00Z")

    private fun version(text: String): Version =
        requireNotNull(Version.parseTriple(text)) { "'$text' is a triple" }

    private fun sha(hex: String = "1a2b3c4d5e6f7890abcdef1234567890abcdef12"): Sha =
        requireNotNull(Sha.abbreviate(hex)) { "'$hex' is hex" }

    private fun pre(released: List<String>, at: Instant = noon): Publish = resolve(
        event = Event.Push,
        released = released.mapNotNull(Version::parseTag),
        now = Stamp.at(at),
        sha = sha(),
    )

    /** The tag shape is part of the published contract. */
    @Test
    fun `a pre-release names its time and its commit`() {
        assertEquals(
            "v0.4.3-dev.2026-08-25.11-30-00.g1a2b3c4",
            pre(listOf("v0.4.2")).tag(),
        )
    }

    @Test
    fun `a release is the tag and nothing else`() {
        val published = resolve(Event.Release(version("0.4.2")), emptyList(), Stamp.at(noon), sha())
        assertEquals("v0.4.2", published.tag())
        assertTrue(!published.isPrerelease)
    }

    /** Law 1: numeric major, minor, then patch precedence. */
    @Test
    fun `versions compare field by field and numerically`() {
        assertTrue(version("0.9.0") < version("0.10.0"))
        assertTrue(version("1.0.0") > version("0.99.99"))
        assertTrue(version("0.1.9") < version("0.1.10"))
        assertEquals(version("2.3.4"), version("2.3.4"))
    }

    /** SemVer §11 ordering: a pre-release lies below its release. */
    @Test
    fun `a pre-release sorts between the releases it lies between`() {
        val before: Publish = Publish.Release(version("0.4.2"))
        val middle = pre(listOf("v0.4.2"))
        val after: Publish = Publish.Release(version("0.4.3"))

        assertEquals(version("0.4.3"), middle.version)
        assertTrue(before < middle)
        assertTrue(middle < after)
        assertTrue(middle.isPrerelease && !after.isPrerelease)
    }

    /** Law 2: fixed-width padding makes rendered timestamps chronological. */
    @Test
    fun `later builds sort later, an hour apart and either side of ten`() {
        val morning = pre(listOf("v1.0.0"), noon.minusSeconds(2 * 3600)).tag()
        val midday = pre(listOf("v1.0.0"), noon).tag()

        assertTrue("$morning should sort below $midday", morning < midday)
        assertTrue(morning, morning.contains("09-30-00"))
        assertTrue(midday, midday.contains("11-30-00"))

        // Stamp ordering must match its rendering.
        assertTrue(Stamp.at(noon.minusSeconds(2 * 3600)) < Stamp.at(noon))
    }

    /** Padding also preserves order across date boundaries. */
    @Test
    fun `the stamp pads every field, not only the hour`() {
        assertEquals(
            "2026-01-02.03-04-05",
            Stamp.at(Instant.parse("2026-01-02T03:04:05Z")).toString(),
        )
    }

    /** Law 3: the `g` prefix prevents a commit from becoming numeric SemVer data. */
    @Test
    fun `a commit is never an all-digit identifier`() {
        val digits = sha("0012345678901234567890123456789012345678")
        assertEquals("g0012345", digits.toString())
        assertTrue(digits.toString().any { it !in '0'..'9' })

        // The encoded tag still preserves time order.
        val earlier = Publish.Pre(version("1.0.0"), Stamp.at(noon.minusSeconds(60)), digits)
        val later = Publish.Pre(version("1.0.0"), Stamp.at(noon), sha())
        assertTrue(earlier < later)
    }

    /** Law 4: the base is the newest release successor, or 0.0.1. */
    @Test
    fun `the base version is the newest release tag's successor and nothing else`() {
        assertEquals(version("0.0.1"), pre(emptyList()).version)
        assertEquals(version("0.1.1"), pre(listOf("v0.1.0")).version)
        // Precedence, not input order, selects the newest tag.
        assertEquals(version("0.3.1"), pre(listOf("v0.3.0", "v0.1.0", "v0.2.9")).version)
        // A minor is selected by its tag.
        assertEquals(version("0.2.1"), pre(listOf("v0.1.0", "v0.2.0")).version)
    }

    /** Pre-release tags do not participate in the release base. */
    @Test
    fun `a pre-release tag is not a release and does not raise the base`() {
        assertEquals(
            version("0.1.1"),
            pre(listOf("v0.1.0", "v0.9.9-dev.2026-01-01.00-00-00.gabc1234")).version,
        )
        assertNull(Version.parseTag("v0.9.9-dev.2026-01-01.00-00-00.gabc1234"))
    }

    /** The edge parser refuses anything other than a strict triple behind `v`. */
    @Test
    fun `the ref parser accepts a release tag and refuses everything else`() {
        assertEquals(
            Decided.Accepted(Event.Release(version("0.4.2"))),
            Event.of("tag", "v0.4.2"),
        )

        for (malformed in listOf(
            "0.4.2", "v0.4", "v0.4.2.1", "v0.04.2", "v0.4.2-dev.2026-08-25.11-30-00.gabc1234",
            "release-0.4.2", "v", "v+1.2.3", "v1.2.-3", "",
        )) {
            val decoded = Event.of("tag", malformed)
            assertTrue("'$malformed' was accepted", decoded is Decided.Refused)
        }
    }

    /** Branch pushes and local builds without CI variables are pushes. */
    @Test
    fun `anything that is not a tag ref is a push`() {
        assertEquals(Decided.Accepted(Event.Push), Event.of("branch", "main"))
        assertEquals(Decided.Accepted(Event.Push), Event.of(null, null))
        assertEquals(Decided.Accepted(Event.Push), Event.of(null, "v1.0.0"))
    }

    /** A tag ref without a name is refused rather than demoted to a push. */
    @Test
    fun `a tag ref with no name is refused rather than treated as a push`() {
        assertTrue(Event.of("tag", null) is Decided.Refused)
        assertTrue(Event.of("tag", "") is Decided.Refused)
    }

    @Test
    fun `a tag round-trips through its parser`() {
        for (text in listOf("v0.0.0", "v1.2.3", "v10.20.30", "v0.0.1")) {
            assertEquals(text, "v${requireNotNull(Version.parseTag(text))}")
        }
    }

    /** SemVer forbids leading zeroes in numeric identifiers. */
    @Test
    fun `a leading zero is a refusal rather than a value`() {
        assertNull(Version.parseTriple("0.04.2"))
        assertNull(Version.parseTriple("00.4.2"))
        assertNotEquals(Version.parseTriple("0.0.0"), Version.parseTriple("0.0.1"))
        // A single zero is legitimate.
        assertEquals(version("0.0.0"), Version.parseTriple("0.0.0"))
    }

    @Test
    fun `an object name shorter than seven digits is not a commit`() {
        assertNull(Sha.abbreviate("abc123"))
        assertNull(Sha.abbreviate("not-hex-at-all"))
        assertEquals("gabc1234", requireNotNull(Sha.abbreviate("abc1234")).toString())
    }

    @Test
    fun `a stamp that is not fixed width does not parse`() {
        assertNull(Stamp.parse("2026-8-25.11-30-00"))
        assertNull(Stamp.parse("2026-08-25.9-30-00"))
        assertNull(Stamp.parse(""))
        assertEquals("2026-08-25.11-30-00", requireNotNull(Stamp.parse("2026-08-25.11-30-00")).toString())
    }
}

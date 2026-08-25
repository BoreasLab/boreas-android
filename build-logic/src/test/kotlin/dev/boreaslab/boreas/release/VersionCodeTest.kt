package dev.boreaslab.boreas.release

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The versionCode law and the refusals that protect it.
 *
 * The law is order preservation: the tag order embeds into the integers, so for
 * publishes `a < b` by SemVer precedence, `code(a) < code(b)`. It is checked as a
 * property over a simulated history rather than at hand-picked points, because
 * the interesting failures are at the seams: the release that follows its own
 * pre-releases, and the first pre-release of the namespace after it.
 */
class VersionCodeTest {

    private val start = Instant.parse("2026-08-25T11:30:00Z")

    private fun version(text: String): Version =
        requireNotNull(Version.parseTriple(text)) { "'$text' is a triple" }

    private fun sha(hex: String = "1a2b3c4d5e6f7890abcdef1234567890abcdef12"): Sha =
        requireNotNull(Sha.abbreviate(hex))

    private fun accept(decided: Decided<Identity>): Identity = when (decided) {
        is Decided.Accepted -> decided.value
        is Decided.Refused -> error("refused: ${decided.refusal.message}")
    }

    private fun refusal(decided: Decided<Identity>): Refusal = when (decided) {
        is Decided.Accepted -> error("accepted ${decided.value.tag}, expected a refusal")
        is Decided.Refused -> decided.refusal
    }

    private fun identity(
        event: Event,
        released: List<String>,
        since: Int,
        at: Instant = start,
    ): Decided<Identity> = identify(
        event = event,
        history = History(released.mapNotNull(Version::parseTag), since),
        now = Stamp.at(at),
        sha = sha(),
    )

    /** The layout, read as bytes, which is the whole reason it is byte aligned. */
    @Test
    fun `the packing reads as major minor patch revision, one byte each`() {
        val pre = accept(identity(Event.Push, listOf("v0.1.0"), since = 7))
        assertEquals("0.1.1", pre.publish.version.toString())
        assertEquals(0x00010107, pre.versionCode)

        val release = accept(identity(Event.Release(version("0.1.1")), listOf("v0.1.0"), since = 7))
        assertEquals(0x000101FF, release.versionCode)

        val wide = accept(identity(Event.Release(version("63.255.255")), emptyList(), since = 0))
        assertEquals(0x3FFFFFFF, wide.versionCode)
    }

    /**
     * A release takes the field's maximum so that it outranks the pre-releases
     * that led to it. The obvious encoding gives it the revision it was cut at,
     * which sorts it below them and makes Play reject it as a downgrade.
     */
    @Test
    fun `a release outranks every pre-release of its own triple`() {
        val released = listOf("v0.1.0")
        val release = accept(identity(Event.Release(version("0.1.1")), released, since = 3))
        val highestPre = accept(identity(Event.Push, released, since = Revision.CEILING - 1))

        assertTrue(highestPre.publish < release.publish)
        assertTrue(highestPre.versionCode < release.versionCode)
    }

    /**
     * The order-preservation law, over a history with several releases and the
     * pre-releases between them.
     *
     * Built the way main actually moves: commits accumulate, a release is cut,
     * the counter restarts inside the namespace above it. Every consecutive pair
     * is checked in both orders, so a violation names the pair.
     */
    @Test
    fun `versionCode is order preserving over a simulated history`() {
        val published = mutableListOf<Identity>()
        val released = mutableListOf<String>()
        var instant = start

        // Cut releases of differing shapes, so a patch bump, a minor bump, and a
        // major bump each get their seam tested.
        for (next in listOf("v0.0.1", "v0.1.0", "v0.1.1", "v0.2.0", "v1.0.0", "v1.0.1")) {
            for (commit in 0 until 5) {
                instant = instant.plusSeconds(3600)
                published += accept(identity(Event.Push, released.toList(), commit, instant))
            }
            instant = instant.plusSeconds(3600)
            val cut = requireNotNull(Version.parseTag(next))
            published += accept(identity(Event.Release(cut), released.toList(), 5, instant))
            released += next
        }

        // Something was actually produced, so a law that holds over an empty list
        // cannot pass by accident.
        assertEquals(6 * 6, published.size)

        published.zipWithNext { earlier, later ->
            assertTrue(
                "${earlier.tag} should precede ${later.tag}",
                earlier.publish < later.publish,
            )
            assertTrue(
                "${earlier.tag} (${earlier.versionCode}) should be numbered below " +
                    "${later.tag} (${later.versionCode})",
                earlier.versionCode < later.versionCode,
            )
        }

        // And every code is a legal Play code, which is the property the field
        // widths exist to guarantee.
        for (identity in published) {
            assertTrue(identity.versionCode in 1..VersionCode.PLAY_CEILING)
        }
    }

    /**
     * The cap is not 2^31. Play's maximum is below `Int.MAX_VALUE`, so a 31-bit
     * packing overflows something that looks like it fits.
     */
    @Test
    fun `the widest representable code is under the Play ceiling`() {
        val widest = accept(identity(Event.Release(version("63.255.255")), emptyList(), 0))
        assertTrue(widest.versionCode < VersionCode.PLAY_CEILING)
        assertTrue(widest.versionCode < Int.MAX_VALUE)
        assertEquals(1_073_741_823, widest.versionCode)
        // The trap, stated: 31 bits would clear Int and not clear Play.
        assertTrue(Int.MAX_VALUE > VersionCode.PLAY_CEILING)
    }

    /** Refuse, do not wrap. A 64th major version has no field to land in. */
    @Test
    fun `a field over its ceiling is refused rather than truncated`() {
        val refused = refusal(identity(Event.Release(version("64.0.0")), emptyList(), 0))
        assertTrue(refused is Refusal.FieldOverflows)
        assertTrue(refused.message, refused.message.contains("major"))

        assertTrue(
            refusal(identity(Event.Release(version("0.256.0")), emptyList(), 0))
                is Refusal.FieldOverflows,
        )
        assertTrue(
            refusal(identity(Event.Release(version("0.0.256")), emptyList(), 0))
                is Refusal.FieldOverflows,
        )
    }

    /**
     * The revision field runs out, and the message names the one cure. It is the
     * same act as every other release act, so it is not an extra thing to
     * remember.
     */
    @Test
    fun `an exhausted revision is refused and the message names the cure`() {
        assertTrue(
            accept(identity(Event.Push, listOf("v0.1.0"), Revision.CEILING - 1))
                .versionCode > 0,
        )

        val refused = refusal(identity(Event.Push, listOf("v0.1.0"), Revision.CEILING))
        assertTrue(refused is Refusal.RevisionExhausted)
        assertTrue(refused.message, refused.message.contains("Cut a release"))
        // And it names the tag to push, so the cure is a command rather than a hunt.
        assertTrue(refused.message, refused.message.contains("v0.1.1"))
    }

    /** A release is never refused for the revision: it takes the field's maximum. */
    @Test
    fun `a release is numbered however many commits preceded it`() {
        val far = accept(identity(Event.Release(version("0.1.1")), listOf("v0.1.0"), 10_000))
        assertEquals(0x000101FF, far.versionCode)
    }

    /**
     * The provenance string, which is the half a 30-bit integer cannot carry. A
     * bug report maps to one (app, core) pair or it maps to nothing.
     */
    @Test
    fun `provenance names the base release and the offset`() {
        assertEquals(
            "v0.1.0 + 7",
            accept(identity(Event.Push, listOf("v0.1.0"), 7)).provenance,
        )
        assertEquals(
            "before any release + 3",
            accept(identity(Event.Push, emptyList(), 3)).provenance,
        )
        assertEquals(
            "cut at v0.1.1",
            accept(identity(Event.Release(version("0.1.1")), listOf("v0.1.0"), 7)).provenance,
        )
    }

    /** The cross-job interface, pinned. Every consumer of it is a shell script. */
    @Test
    fun `the printed lines carry exactly what the pipeline consumes`() {
        val lines = accept(identity(Event.Push, listOf("v0.1.0"), 7)).lines()
        assertEquals(
            listOf(
                "tag=v0.1.1-dev.2026-08-25.11-30-00.g1a2b3c4",
                "version=0.1.1",
                "versionName=0.1.1-dev.2026-08-25.11-30-00.g1a2b3c4",
                "versionCode=65799",
                "prerelease=true",
                "provenance=v0.1.0 + 7",
            ),
            lines,
        )
        // No line can carry a newline into GITHUB_OUTPUT, which would let a
        // resolved value inject a second output.
        assertTrue(lines.none { it.contains('\n') || it.contains('\r') })
    }

    @Test
    fun `a release is not marked a pre-release and a pre-release is`() {
        assertTrue(accept(identity(Event.Push, listOf("v0.1.0"), 1)).isPrerelease)
        assertTrue(!accept(identity(Event.Release(version("0.1.1")), listOf("v0.1.0"), 1)).isPrerelease)
    }
}

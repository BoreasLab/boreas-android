package dev.boreaslab.boreas.release

/**
 * Commits since the newest release tag.
 *
 * One anchor, matching the one operand the base version has. A code repeats only
 * if the triple *and* the revision repeat, and cutting a tag moves both at once:
 * the count restarts at zero inside a namespace nothing has used yet.
 *
 * With no release tag the count runs from the repository root, which is a real
 * hazard rather than a theoretical one -- boreas-core reached 108 commits with
 * zero releases, and this field holds 255. v0.0.0 was cut before this code
 * existed for exactly that reason, and it is why [Version.successor] is total.
 *
 * The other hazard is a force-push to main, which changes a commit count and can
 * make a later build repeat an earlier code. Play rejects a duplicate, which is
 * the good outcome; branch protection forbidding force-push is the real fix.
 */
@JvmInline
value class Revision private constructor(val count: Int) {

    override fun toString(): String = count.toString()

    companion object {

        /** 8 bits, so the field holds 0 through 255. */
        const val CEILING: Int = 255

        /**
         * What a release takes: the field's maximum.
         *
         * The obvious encoding gives a release the revision it was cut at, which
         * would put it *below* every pre-release that led to it and make Play
         * reject the release as a downgrade. The maximum reproduces what SemVer
         * already does with the same intent: a pre-release of X ranks below the
         * release of X.
         */
        val RELEASED: Revision = Revision(CEILING)

        /**
         * A pre-release revision, which stops one short of the maximum because
         * the maximum belongs to the release.
         */
        fun of(count: Int, version: Version): Decided<Revision> =
            if (count in 0 until CEILING) {
                Decided.Accepted(Revision(count))
            } else {
                Decided.Refused(Refusal.RevisionExhausted(version, count))
            }
    }
}

/**
 * The monotonically increasing integer Android installs by.
 *
 *     major 6 bits | minor 8 bits | patch 8 bits | revision 8 bits
 *
 * Byte aligned deliberately, so `0x00010107` reads as 0.1.1 revision 7 at a
 * glance. A split straddling byte boundaries costs nothing to compute and
 * everything to read at three in the morning.
 *
 * **The law is order preservation.** For publishes `a < b` by SemVer precedence,
 * `code(a) < code(b)`; the tag order embeds into the integers, and the embedding
 * is tested as a property over a simulated history rather than at hand-picked
 * points.
 *
 * **The ceiling is not 2^31.** Google Play's maximum is 2,100,000,000, which is
 * *below* `Int.MAX_VALUE`, so a 31-bit packing overflows something that looks
 * like it fits. Thirty bits reach 1,073,741,823 and cannot.
 *
 * Every field and the total are checked, and a violation is refused rather than
 * wrapped: `shl` truncates in silence, so a 64th major version would land
 * outside its field and corrupt the one above it, and a number already accepted
 * by Play cannot be withdrawn.
 */
@JvmInline
value class VersionCode private constructor(val value: Int) {

    override fun toString(): String = value.toString()

    companion object {

        private const val MAJOR_BITS = 6
        private const val MINOR_BITS = 8
        private const val PATCH_BITS = 8
        private const val REVISION_BITS = 8

        private const val MAJOR_SHIFT = MINOR_BITS + PATCH_BITS + REVISION_BITS
        private const val MINOR_SHIFT = PATCH_BITS + REVISION_BITS
        private const val PATCH_SHIFT = REVISION_BITS

        private const val MAJOR_CEILING = (1L shl MAJOR_BITS) - 1
        private const val MINOR_CEILING = (1L shl MINOR_BITS) - 1
        private const val PATCH_CEILING = (1L shl PATCH_BITS) - 1

        /** developer.android.com/studio/publish/versioning. Below `Int.MAX_VALUE`, which is the trap. */
        const val PLAY_CEILING: Int = 2_100_000_000

        fun of(version: Version, revision: Revision): Decided<VersionCode> {
            overflow("major", version.major, MAJOR_BITS, MAJOR_CEILING)
                ?.let { return Decided.Refused(it) }
            overflow("minor", version.minor, MINOR_BITS, MINOR_CEILING)
                ?.let { return Decided.Refused(it) }
            overflow("patch", version.patch, PATCH_BITS, PATCH_CEILING)
                ?.let { return Decided.Refused(it) }

            // Assembled in Long and narrowed only after the total has been
            // checked, so the check cannot be the thing that overflows.
            val packed = (version.major shl MAJOR_SHIFT) or
                (version.minor shl MINOR_SHIFT) or
                (version.patch shl PATCH_SHIFT) or
                revision.count.toLong()

            return if (packed > PLAY_CEILING) {
                Decided.Refused(Refusal.AbovePlayCeiling(packed))
            } else {
                Decided.Accepted(VersionCode(packed.toInt()))
            }
        }

        private fun overflow(name: String, value: Long, bits: Int, ceiling: Long): Refusal? =
            if (value in 0..ceiling) null else Refusal.FieldOverflows(name, value, bits, ceiling)
    }
}

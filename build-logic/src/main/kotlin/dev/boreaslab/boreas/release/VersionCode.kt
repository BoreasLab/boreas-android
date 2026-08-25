package dev.boreaslab.boreas.release

/**
 * Commits since the newest release tag. Without a release tag, the count starts
 * at the repository root; `v0.0.0` reserves the revision range's initial space
 * because boreas-core had 108 commits before its first release. A force-push can
 * repeat a count and therefore a code; branch protection prevents that case.
 */
@JvmInline
value class Revision private constructor(val count: Int) {

    override fun toString(): String = count.toString()

    companion object {

        /** Eight-bit field holding 0 through 255. */
        const val CEILING: Int = 255

        /**
         * Maximum revision for a release. Using its cut count would place the
         * release below its preceding pre-releases and cause a Play downgrade.
         */
        val RELEASED: Revision = Revision(CEILING)

        /** Pre-release revision, one below the release maximum. */
        fun of(count: Int, version: Version): Decided<Revision> =
            if (count in 0 until CEILING) {
                Decided.Accepted(Revision(count))
            } else {
                Decided.Refused(Refusal.RevisionExhausted(version, count))
            }
    }
}

/**
 * Android install code packed as 6 major bits, 8 minor bits, 8 patch bits, and
 * 8 revision bits. The embedding preserves SemVer order. Google Play's maximum
 * is 2,100,000,000, below `Int.MAX_VALUE`, so every field and the packed total
 * are checked before narrowing; see developer.android.com/studio/publish/versioning.
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

        /** Google Play's published ceiling, below `Int.MAX_VALUE`. */
        const val PLAY_CEILING: Int = 2_100_000_000

        fun of(version: Version, revision: Revision): Decided<VersionCode> {
            overflow("major", version.major, MAJOR_BITS, MAJOR_CEILING)
                ?.let { return Decided.Refused(it) }
            overflow("minor", version.minor, MINOR_BITS, MINOR_CEILING)
                ?.let { return Decided.Refused(it) }
            overflow("patch", version.patch, PATCH_BITS, PATCH_CEILING)
                ?.let { return Decided.Refused(it) }

            // Check in Long before narrowing to Int.
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

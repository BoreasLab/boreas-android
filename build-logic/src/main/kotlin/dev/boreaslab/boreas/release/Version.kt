package dev.boreaslab.boreas.release

import java.io.Serializable

/**
 * A release triple: the SemVer core version and nothing else.
 *
 * Field order is the precedence law. [compareTo] names the three properties in
 * that order and nothing else, so what could disagree with the specification has
 * nowhere to live. Kotlin cannot derive an ordering from a declaration the way
 * Rust's `#[derive(Ord)]` does, and this is as close as it gets: one expression,
 * each field compared numerically, no ladder of `if`s to get backwards.
 *
 * `Long` rather than `Int` so that a well-formed but enormous identifier parses
 * and is refused later, by the versionCode encoder, with a message naming the
 * field that overflowed. Refusing it here would say "not a version", which is a
 * different and less useful thing to say.
 */
@ConsistentCopyVisibility
data class Version private constructor(
    val major: Long,
    val minor: Long,
    val patch: Long,
) : Comparable<Version>, Serializable {

    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    /** The next patch: what a build published between releases works toward. */
    fun successor(): Version = copy(patch = patch + 1)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {

        /**
         * The identity of `max` over releases: a repository that has never
         * shipped. Its [successor] is 0.0.1, which is what such a repository's
         * pre-releases are numbered for.
         */
        val ORIGIN: Version = Version(0, 0, 0)

        private const val serialVersionUID: Long = 1L

        /**
         * Strictly `v` and a triple. A pre-release tag is not a release and does
         * not parse here, which is what keeps it out of the fold that decides the
         * base version: counting pre-releases would make the version climb with
         * commit volume rather than with intent.
         */
        fun parseTag(tag: String): Version? =
            if (tag.startsWith('v')) parseTriple(tag.substring(1)) else null

        /** Three numeric identifiers, separated by dots, and nothing after them. */
        fun parseTriple(text: String): Version? {
            val fields = text.split('.')
            if (fields.size != 3) return null
            val (major, minor, patch) = fields.map(::numericIdentifier)
            return if (major == null || minor == null || patch == null) {
                null
            } else {
                Version(major, minor, patch)
            }
        }

        /**
         * `toLong` accepts a leading `+`, a leading `-`, and a leading zero. A
         * SemVer numeric identifier accepts none of the three, so `v0.04.2` is a
         * refusal rather than a 4.
         */
        private fun numericIdentifier(text: String): Long? {
            val wellFormed = text.isNotEmpty() &&
                text.all { it in '0'..'9' } &&
                !(text.length > 1 && text[0] == '0')
            return if (wellFormed) text.toLongOrNull() else null
        }
    }
}

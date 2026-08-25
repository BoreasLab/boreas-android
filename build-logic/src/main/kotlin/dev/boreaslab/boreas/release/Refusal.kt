package dev.boreaslab.boreas.release

/**
 * A value, or the refusal standing in its place.
 *
 * There are exactly two places a refusal can arise, and neither is [resolve],
 * which is total. One is the parser at the edge, where an untrusted ref name
 * either is a release tag or is not. The other is the versionCode encoder, whose
 * fields have widths a build can exceed. A build that cannot be named or cannot
 * be numbered must fail rather than ship a wrong number, so both return one of
 * these and the shell turns a [Refused] into a failed configuration.
 *
 * Nothing here throws: the refusals are ordinary values, which is what lets the
 * tests enumerate them.
 */
sealed interface Decided<out T> {
    data class Accepted<T>(val value: T) : Decided<T>
    data class Refused(val refusal: Refusal) : Decided<Nothing>
}

/**
 * Why a build was refused a name or a number.
 *
 * The only reader is a person looking at a red CI log, so every message says what
 * is wrong and what to do about it.
 */
sealed interface Refusal {

    val message: String

    /**
     * The ref is a tag, and it is not a release tag.
     *
     * The only refusal left on the naming side. There is no gate comparing a tag
     * to a declared version, because there is no declared version to disagree
     * with: the tag is the version, and a check exists only where two sources
     * can differ.
     */
    data class NotARelease(val tag: String) : Refusal {
        override val message: String
            get() = "'$tag' is not a release tag. A release is vMAJOR.MINOR.PATCH: three decimal " +
                "fields, no leading zeroes, nothing after the patch. Delete the tag and push " +
                "the corrected one."
    }

    /**
     * A field of the versionCode packing has a ceiling, and this build is over it.
     *
     * Shifting and OR-ing would truncate in silence and land the overflow in the
     * field above, so a 64th major version would read as some other version's
     * minor. There is no repair for a number already accepted by Play.
     */
    data class FieldOverflows(
        // Not `field`: inside a property getter that name is Kotlin's backing
        // field, so it would silently resolve to `message`'s own storage.
        val fieldName: String,
        val value: Long,
        val bits: Int,
        val ceiling: Long,
    ) : Refusal {
        override val message: String
            get() = "versionCode gives $fieldName $bits bits, so it stops at $ceiling, and this " +
                "build declares $value. The packing cannot be widened without renumbering " +
                "every release already published."
    }

    /**
     * The revision counter ran out between two release tags.
     *
     * One cure, and it is the same act as every other release act, so it is not
     * an extra thing to remember: cut a release. Reaching 255 commits between
     * tags is itself the signal that one is overdue.
     */
    data class RevisionExhausted(val version: Version, val revision: Int) : Refusal {
        override val message: String
            get() = "$revision commits have landed since the newest release tag, and versionCode " +
                "gives the revision 8 bits, of which 255 is reserved for the release itself. " +
                "Cut a release: push v$version, or whichever triple the next release should " +
                "be. The count restarts from that tag."
    }

    /**
     * The packed total is above what Google Play accepts.
     *
     * Unreachable while the fields are 6/8/8/8, and checked anyway: the ceiling is
     * a property of the total rather than of any one field, and a future widening
     * that forgot it would be found by a rejected upload rather than by a test.
     */
    data class AbovePlayCeiling(val code: Long) : Refusal {
        override val message: String
            get() = "versionCode $code is above the ${VersionCode.PLAY_CEILING} Google Play " +
                "accepts. The field widths in VersionCode must sum to no more than 30 bits."
    }
}

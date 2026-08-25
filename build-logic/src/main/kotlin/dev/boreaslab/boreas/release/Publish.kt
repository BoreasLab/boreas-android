package dev.boreaslab.boreas.release

import java.io.Serializable

/**
 * Publish event. [Release] carries a parsed [Version], while [Push] carries
 * none, so a release without a version is unrepresentable.
 */
sealed interface Event {

    data object Push : Event

    data class Release(val version: Version) : Event

    companion object {

        /** Parses GitHub's ref fields into an [Event]. Non-tag refs, including
         * absent local-build variables, are pushes; malformed tag refs are
         * refused rather than demoted to pushes.
         */
        fun of(refType: String?, refName: String?): Decided<Event> =
            if (refType != "tag") {
                Decided.Accepted(Push)
            } else {
                Version.parseTag(refName.orEmpty())
                    ?.let { Decided.Accepted(Release(it)) }
                    ?: Decided.Refused(Refusal.NotARelease(refName.orEmpty()))
            }
    }
}

/**
 * Publishable version and its SemVer 2.0.0 precedence. Under §11, a pre-release
 * sorts below the release sharing its core version, so generated pre-releases
 * use the patch version of the release they precede.
 */
sealed interface Publish : Comparable<Publish> {

    val version: Version

    /** The SemVer pre-release identifiers, empty for a release. */
    val identifiers: String

    /** Derived from [identifiers], so the variant cannot disagree with the flag. */
    val isPrerelease: Boolean get() = identifiers.isNotEmpty()

    fun tag(): String = if (identifiers.isEmpty()) "v$version" else "v$version-$identifiers"

    override fun compareTo(other: Publish): Int = PRECEDENCE.compare(this, other)

    data class Release(override val version: Version) : Publish {
        override val identifiers: String get() = ""
    }

    data class Pre(
        override val version: Version,
        val stamp: Stamp,
        val sha: Sha,
    ) : Publish {
        override val identifiers: String get() = "dev.$stamp.$sha"
    }

    companion object {

        /** SemVer precedence. String comparison is valid because [Stamp] is fixed
         * width and zero padded, while [Sha] includes `g` and remains non-numeric;
         * tests cover both representation laws.
         */
        private val PRECEDENCE: Comparator<Publish> =
            compareBy<Publish> { it.version }
                .thenBy { if (it.isPrerelease) 0 else 1 }
                .thenBy { it.identifiers }
    }
}

/**
 * Repository facts used to name a publish. `commitsSinceTag` is measured from
 * the repository root when no strict release tag exists; the initial `v0.0.0`
 * keeps that revision field representable.
 */
data class History(
    /** Every strict release tag. Pre-release tags are not releases and are not here. */
    val released: List<Version>,
    /** Commits on this branch since the newest release tag, or since the root if there is none. */
    val commitsSinceTag: Int,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Provenance identifies one (app version, core version) pair for bug reports.
 */
data class Identity(
    val publish: Publish,
    val code: VersionCode,
    val revision: Revision,
    val newestRelease: Version?,
) {

    val tag: String get() = publish.tag()

    /** The tag without its `v`, which is what Android calls a versionName. */
    val versionName: String get() = tag.removePrefix("v")

    val versionCode: Int get() = code.value

    val isPrerelease: Boolean get() = publish.isPrerelease

    val provenance: String
        get() = when (val published = publish) {
            is Publish.Release -> "cut at v${published.version}"
            is Publish.Pre -> newestRelease
                ?.let { "v$it + $revision" }
                ?: "before any release + $revision"
        }

    /**
     * `key=value` lines shared by the identity and build/publish jobs. The
     * identity is computed once because rereading the clock could stamp the
     * binary differently from the tag.
     */
    fun lines(): List<String> = listOf(
        "tag=$tag",
        "version=${publish.version}",
        "versionName=$versionName",
        "versionCode=$versionCode",
        "prerelease=$isPrerelease",
        "provenance=$provenance",
    )
}

/**
 * Derives a publish from an event. Every event has a publish, so no refusal is
 * needed: the tag is the version and the sole base-version input.
 *
 * Pushes use the patch successor of the newest release, or [Version.ORIGIN]
 * when none exists. A minor or major release must be expressed by its tag.
 *
 * Runs in O(n) over released tags and O(1) extra space: a fold computes the
 * maximum without sorting.
 */
fun resolve(
    event: Event,
    released: List<Version>,
    now: Stamp,
    sha: Sha,
): Publish = when (event) {
    is Event.Release -> Publish.Release(event.version)
    Event.Push -> Publish.Pre(
        version = released.fold(Version.ORIGIN, ::maxOf).successor(),
        stamp = now,
        sha = sha,
    )
}

/**
 * Combines total naming with fallible [VersionCode] numbering.
 */
fun identify(
    event: Event,
    history: History,
    now: Stamp,
    sha: Sha,
): Decided<Identity> {
    val publish = resolve(event, history.released, now, sha)
    val newestRelease = history.released.maxOrNull()

    val revision = when (publish) {
        // The field's maximum, so a release outranks every pre-release that led
        // to it. See Revision.RELEASED.
        is Publish.Release -> Decided.Accepted(Revision.RELEASED)
        is Publish.Pre -> Revision.of(history.commitsSinceTag, publish.version)
    }

    return when (revision) {
        is Decided.Refused -> revision
        is Decided.Accepted -> when (val code = VersionCode.of(publish.version, revision.value)) {
            is Decided.Refused -> code
            is Decided.Accepted -> Decided.Accepted(
                Identity(publish, code.value, revision.value, newestRelease),
            )
        }
    }
}

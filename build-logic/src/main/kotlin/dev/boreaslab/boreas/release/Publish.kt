package dev.boreaslab.boreas.release

import java.io.Serializable

/**
 * What triggered a publish.
 *
 * A sum rather than a boolean beside an optional string: [Release] carries a
 * version, [Push] carries nothing, and "a release event with no version" is not
 * a state that can be written down. That is the whole reason no YAML here has an
 * `if [ "$IS_TAG" ]`.
 *
 * **[Release] holds a parsed [Version], not the tag string.** The string is
 * untrusted, being whatever `GITHUB_REF_NAME` happens to say, so it is parsed at
 * the boundary that receives it and everything downstream takes a value that
 * already is a version. That is what leaves [resolve] total.
 */
sealed interface Event {

    data object Push : Event

    data class Release(val version: Version) : Event

    companion object {

        /**
         * GitHub's two ref fields, read as one sum.
         *
         * The one place an untrusted string becomes a domain value, and so the
         * one place a name can be refused. Anything that is not a tag ref is a
         * push, including the absent variables a local build sees.
         *
         * A tag ref with no readable name is refused rather than demoted to a
         * push. GitHub does not produce that pair, so seeing it means the
         * environment is not what this reads it as, and publishing a pre-release
         * from a tag push would be the wrong thing to do quietly.
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
 * What is being published, and how it sorts against everything else published.
 *
 * Both shapes are valid SemVer 2.0.0, and the ordering is the point: §11 sorts a
 * pre-release *below* the release sharing its core version, so
 *
 *     v0.4.2  <  v0.4.3-dev.2026-08-25.11-30-00.g1a2b3c4  <  v0.4.3
 *
 * Anything that sorts tags therefore gets "newest" right without knowing this
 * scheme exists, which is why a pre-release is numbered for the patch that has
 * not happened yet.
 */
sealed interface Publish : Comparable<Publish> {

    val version: Version

    /** The SemVer pre-release identifiers, empty for a release. */
    val identifiers: String

    /**
     * Whether GitHub should mark this a pre-release, and so keep it out of
     * "Latest". A projection of the variant, never a field that could disagree
     * with it.
     */
    val isPrerelease: Boolean get() = identifiers.isNotEmpty()

    /** The git tag, which is also the name of every asset published under it. */
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

        /**
         * SemVer precedence, in three clauses and no more.
         *
         * The third is one string comparison rather than a walk over
         * dot-separated identifiers, and it is correct *because of* the two
         * representation laws rather than in spite of them: the stamp is fixed
         * width and zero padded, so ASCII order on it is chronological order, and
         * the commit always carries its `g`, so SemVer never takes it for a
         * numeric identifier and ranks it below its siblings. Break either law
         * and this stops agreeing with the specification without failing to
         * compile, which is why both have tests of their own.
         */
        private val PRECEDENCE: Comparator<Publish> =
            compareBy<Publish> { it.version }
                .thenBy { if (it.isPrerelease) 0 else 1 }
                .thenBy { it.identifiers }
    }
}

/**
 * The facts about the repository that a publish is named from.
 *
 * One anchor, the newest release tag, and one count from it. `commitsSinceTag`
 * counts from the repository root when there is no tag at all, which is why
 * v0.0.0 was cut before this code existed: the revision field holds 255, and a
 * project that has never tagged would spend it on its own history.
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
 * Everything a build needs in order to name and number itself.
 *
 * [provenance] is the half a 30-bit integer cannot carry. A bug report has to map
 * to one (app version, core version) pair or it maps to nothing, and "v0.1.0 plus
 * seven commits" is a thing a person can check out.
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
     * The `key=value` lines the resolve step prints.
     *
     * This is the entire interface between the job that decides the identity and
     * the jobs that build and publish under it, and it is why no later job
     * recomputes anything: the timestamp inside the tag exists only at the
     * instant it was read, so two jobs reading their own clocks would tag the
     * release one way and stamp the binary another.
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
 * The naming algebra, and it is **total**: every event has a publish.
 *
 * There is no gate here and no refusal, because there is nothing left to
 * disagree with. **The tag is the version.** A design that also read a version
 * out of a committed file and refused a tag that differed from it made a release
 * two acts, and the one you forget is the one that fails the build. That check
 * existed only because there were two sources; one source makes the invariant
 * hold by construction rather than by inspection.
 *
 * **The base version has one operand.** The next pre-release heads for the patch
 * above the newest release, and a repository that has never shipped starts from
 * [Version.ORIGIN], whose successor is 0.0.1. If the next release should be a
 * minor, tag a minor: the tag says so, and saying it twice is what this removed.
 *
 * O(n) in the tag count, folding from the identity [Version.ORIGIN]. For the
 * handful of release tags a repository accumulates the fold is free; it is
 * written as a fold rather than a sort because "newest" is a `max` and asking
 * for a total ordering would be asking for more than the question needs.
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
 * The name and the number together, which is the whole answer a build needs.
 *
 * Naming is total and numbering is not, so this is where the two meet and where
 * the only remaining refusals surface.
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

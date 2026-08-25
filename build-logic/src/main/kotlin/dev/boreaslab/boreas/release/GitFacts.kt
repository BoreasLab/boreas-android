package dev.boreaslab.boreas.release

import java.io.ByteArrayOutputStream
import java.io.Serializable
import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

/**
 * What git knows that the algebra needs.
 *
 * The commit is here rather than fetched separately so that the tags, the count,
 * and the commit are all read from one view of the repository. Reading them
 * through three independent calls would let a fetch land in between and produce a
 * count that belongs to a different HEAD than the one being named.
 */
data class GitFacts(val head: String, val history: History) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * The effect boundary: the only place this build logic shells out.
 *
 * A [ValueSource] rather than a plain call so that the configuration cache knows
 * these are external inputs and re-reads them, rather than serving a name
 * computed before the last three commits landed. Everything downstream of
 * [obtain] is pure.
 *
 * Four git invocations at most, each O(refs) or O(commits) inside git and
 * negligible beside process spawn. It runs once per build: Gradle caches a value
 * source's result for identical parameters.
 */
abstract class GitFactsSource : ValueSource<GitFacts, GitFactsSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        val repository: DirectoryProperty
    }

    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): GitFacts {
        val head = git("rev-parse", "HEAD")

        // Every tag, then only the ones that are releases. A pre-release tag is
        // not a release: counting it would make the base version climb with
        // commit volume rather than with intent.
        val releases = git("tag", "--list")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { name -> Version.parseTag(name)?.let { name to it } }
            .toList()

        // The anchor. With no release tag at all the range is the whole history,
        // which is exactly the case v0.0.0 was cut to remove: the revision field
        // holds 255 and a project's own history would spend it.
        val anchor = releases.maxByOrNull { (_, version) -> version }
        val range = anchor?.let { (name, _) -> "$name..HEAD" } ?: "HEAD"
        val since = git("rev-list", "--count", range).toIntOrNull()
            ?: error("git rev-list --count $range did not answer with a number")

        return GitFacts(head, History(releases.map { (_, version) -> version }, since))
    }

    /**
     * Strict about the exit status on purpose. A tag that is not present because
     * the checkout was shallow makes `rev-list` fail, and a tolerant wrapper
     * would turn that into a count of zero and silently renumber every build.
     */
    private fun git(vararg arguments: String): String {
        val captured = ByteArrayOutputStream()
        exec.exec {
            workingDir = parameters.repository.get().asFile
            commandLine(listOf("git") + arguments)
            standardOutput = captured
        }
        return captured.toString(Charsets.UTF_8.name()).trim()
    }
}

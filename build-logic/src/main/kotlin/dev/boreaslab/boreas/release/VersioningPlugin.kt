package dev.boreaslab.boreas.release

import java.time.Instant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers `resolve`, the one command that names a build.
 *
 * There is no second command and no second answer. The release workflow runs
 * this once and passes the result down as job outputs; the build job takes the
 * version it is given rather than working it out again, because the tag carries
 * a timestamp and two jobs reading their own clocks would tag the release one
 * way and stamp the binary another.
 *
 * Applied to the root project. It configures no other project and reads no other
 * project's state.
 */
class VersioningPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val facts = project.providers.of(GitFactsSource::class.java) {
            parameters.repository.set(project.layout.projectDirectory)
        }

        // GitHub's own two fields. Read as providers so the configuration cache
        // treats them as inputs: a run under a tag and a run under a branch are
        // different builds and must not share a cached answer.
        val refType = project.providers.environmentVariable(REF_TYPE)
        val refName = project.providers.environmentVariable(REF_NAME)

        project.tasks.register(TASK) {
            group = "publishing"
            description = "Prints the identity this commit publishes under, as key=value lines."

            // Read at execution rather than at configuration, so a cached
            // configuration cannot serve a stamp minted before the last commit.
            doLast {
                val event = when (val decoded = Event.of(refType.orNull, refName.orNull)) {
                    is Decided.Refused -> throw GradleException(decoded.refusal.message)
                    is Decided.Accepted -> decoded.value
                }
                val gathered = facts.get()
                val sha = Sha.abbreviate(gathered.head)
                    ?: throw GradleException("git rev-parse HEAD did not answer with an object name")

                val identity = when (
                    val decided = identify(event, gathered.history, Stamp.at(Instant.now()), sha)
                ) {
                    is Decided.Refused -> throw GradleException(decided.refusal.message)
                    is Decided.Accepted -> decided.value
                }
                identity.lines().forEach(::println)
            }
        }
    }

    private companion object {
        const val TASK = "resolve"
        const val REF_TYPE = "GITHUB_REF_TYPE"
        const val REF_NAME = "GITHUB_REF_NAME"
    }
}

package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.RuleProfile
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.TunnelDraft
import dev.boreaslab.boreas.model.TunnelParse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The simulated host, which exists only so the control surface can be exercised
 * before the shared engine is linked.
 *
 * Two properties matter here and neither is about the numbers, which are generated
 * and describe nothing. A session must be distinguishable from the one before it,
 * because the controller drops a status update whose session does not match the one
 * it is holding, and identical ids defeat that check. And the host must not answer
 * for a session it never started, because inventing a stream from a default
 * configuration is the one thing this build promises not to do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedEngineHostTest {

    private val platform =
        (TunnelParse.of(TunnelDraft(), emptySet()) as TunnelParse.Valid).config

    /** A clock that advances by one on every read, so two starts cannot share a tick. */
    private fun countingClock(): () -> Long {
        var now = 0L
        return { now++ }
    }

    @Test
    fun `two sessions from the same host have different identities`() = runTest {
        val host = SimulatedEngineHost(clock = countingClock())

        val first = host.start(EngineConfig(), platform) as EngineStart.Started
        host.stop(first.session, StopReason.UserRequested)
        val second = host.start(EngineConfig(), platform) as EngineStart.Started

        assertNotEquals(first.session, second.session)
    }

    @Test
    fun `two hosts do not mint the same identity`() = runTest {
        // The service builds a host per start. An instance counter restarted at one
        // every time, so every session in the transition log was called sim-1.
        val clock = countingClock()
        val first = SimulatedEngineHost(clock).start(EngineConfig(), platform)
        val second = SimulatedEngineHost(clock).start(EngineConfig(), platform)

        assertNotEquals(
            (first as EngineStart.Started).session,
            (second as EngineStart.Started).session,
        )
    }

    @Test
    fun `a session that was never started reports nothing at all`() = runTest {
        val host = SimulatedEngineHost(clock = countingClock())

        assertEquals(emptyList<Any>(), host.status(SessionId("never-started")).toList())
    }

    @Test
    fun `a stopped session stops reporting`() = runTest {
        val host = SimulatedEngineHost(clock = countingClock())
        val started = host.start(EngineConfig(), platform) as EngineStart.Started

        host.stop(started.session, StopReason.UserRequested)

        assertEquals(emptyList<Any>(), host.status(started.session).toList())
    }

    @Test
    fun `the first snapshot carries the configuration the session was started with`() = runTest {
        val host = SimulatedEngineHost(clock = countingClock())
        val config = EngineConfig(profile = RuleProfile.Strict)

        val started = host.start(config, platform) as EngineStart.Started
        val first = host.status(started.session).first()

        assertEquals(config.upstream, first.upstream)
        assertTrue("a generated snapshot must say so", first.simulated)
    }
}

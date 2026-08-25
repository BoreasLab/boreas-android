package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.CoreEvent
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.RuleSetSize
import dev.boreaslab.boreas.model.Endpoint
import dev.boreaslab.boreas.model.Filtering
import dev.boreaslab.boreas.model.Parsed
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

/** Verifies simulated session identity and absence of invented status streams. */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedEngineHostTest {

    private val platform =
        (TunnelParse.of(TunnelDraft(), emptySet()) as TunnelParse.Valid).config

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
        // Per-start hosts would reset an instance counter and reuse sim-1.
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

        assertEquals(emptyList<Any>(), host.events(SessionId("never-started")).toList())
    }

    @Test
    fun `a stopped session stops reporting`() = runTest {
        val host = SimulatedEngineHost(clock = countingClock())
        val started = host.start(filtering(), platform) as EngineStart.Started

        host.stop(started.session, StopReason.UserRequested)

        assertEquals(emptyList<Any>(), host.events(started.session).toList())
    }

    @Test
    fun `a session that filters nothing invents no stream to filter`() = runTest {
        val host = SimulatedEngineHost(clock = countingClock())
        val started = host.start(EngineConfig(), platform) as EngineStart.Started

        assertEquals(emptyList<Any>(), host.events(started.session).toList())
    }

    @Test
    fun `the first event reports the rule set the session was started with`() = runTest {
        val host = SimulatedEngineHost(clock = countingClock())
        val config = filtering("||ads.example.net^")

        val started = host.start(config, platform) as EngineStart.Started
        val first = host.events(started.session).first()

        assertEquals(CoreEvent.Reloaded(RuleSetSize(0, 1, 0)), first)
    }

    @Test
    fun `a generated stream says it is generated`() {
        assertTrue("a host that invents numbers must say so", SimulatedEngineHost().simulated)
    }

    private fun filtering(vararg lists: String) = EngineConfig(
        Filtering.Names(
            upstream = (Endpoint.parse("9.9.9.9") as Parsed.Valid).value,
            lists = lists.toList(),
        ),
    )
}

package dev.boreaslab.boreas.service

import dev.boreaslab.boreas.engine.AuthorityRead
import dev.boreaslab.boreas.engine.EngineHost
import dev.boreaslab.boreas.engine.EngineStart
import dev.boreaslab.boreas.engine.StopReason
import dev.boreaslab.boreas.model.CoreCounters
import dev.boreaslab.boreas.model.CoreEvent
import dev.boreaslab.boreas.model.CoreStatus
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.Endpoint
import dev.boreaslab.boreas.model.Filtering
import dev.boreaslab.boreas.model.Ipv4Address
import dev.boreaslab.boreas.model.Mtu
import dev.boreaslab.boreas.model.NatBehavior
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.Parsed
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers lifecycle variants, command coalescing, cancellation, and failure transitions. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private val platform = PlatformConfig(
        address = (Ipv4Address.parse("10.24.0.2") as Parsed.Valid).value,
        mtu = (Mtu.parse("1500") as Parsed.Valid).value,
        dnsServers = emptyList(),
        excludedPackages = emptySet(),
    )

    private fun start() = SessionCommand.Start(EngineConfig(), platform)

    private class FakeEngine(
        override val isAvailable: Boolean = true,
        val startDelayMs: Long = 0,
        val refuseWith: TypedFailure? = null,
        val eventFlow: Flow<CoreEvent> = emptyFlow(),
        val reloadWith: CoreStatus = CoreStatus.Ok,
    ) : EngineHost {
        override val simulated: Boolean = true

        var startCount = 0
        var stopCount = 0
        var reloadCount = 0
        var lastStopReason: StopReason? = null
        var lastLists: List<String>? = null

        override suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart {
            startCount += 1
            if (startDelayMs > 0) delay(startDelayMs)
            refuseWith?.let { return EngineStart.Refused(it) }
            return EngineStart.Started(SessionId("s$startCount"))
        }

        override suspend fun stop(session: SessionId, reason: StopReason) {
            stopCount += 1
            lastStopReason = reason
        }

        override fun events(session: SessionId) = eventFlow

        override suspend fun reload(session: SessionId, lists: List<String>): CoreStatus {
            reloadCount += 1
            lastLists = lists
            return reloadWith
        }

        override suspend fun authority(session: SessionId) = AuthorityRead.None
    }

    private class FixedConsent(private val outcome: ConsentOutcome) : ConsentGate {
        var calls = 0
        override suspend fun request(): ConsentOutcome {
            calls += 1
            return outcome
        }
    }

    /** Blocks until the test releases it, so AwaitingConsent can be observed. */
    private class HeldConsent : ConsentGate {
        val reached = CompletableDeferred<Unit>()
        private val release = CompletableDeferred<ConsentOutcome>()
        override suspend fun request(): ConsentOutcome {
            reached.complete(Unit)
            return release.await()
        }

        fun release(outcome: ConsentOutcome) = release.complete(outcome)
    }

    @Test
    fun `starts from stopped and reaches running`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        assertEquals(VpnLifecycleState.Stopped, controller.state.value)
        controller.submit(start())
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue("expected Running, was $state", state is VpnLifecycleState.Running)
        assertEquals(1, engine.startCount)
        controller.shutdown()
    }

    @Test
    fun `running carries the configuration it was started with`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })
        val config = EngineConfig(nat = NatBehavior.EndpointIndependent)

        controller.submit(SessionCommand.Start(config, platform))
        advanceUntilIdle()

        assertEquals(config, (controller.state.value as VpnLifecycleState.Running).applied)
        controller.shutdown()
    }

    @Test
    fun `awaits consent before the engine is asked to start`() = runTest {
        val engine = FakeEngine()
        val consent = HeldConsent()
        val controller = SessionController({ engine }, consent, this, now = { 0L })

        controller.submit(start())
        consent.reached.await()

        assertEquals(VpnLifecycleState.AwaitingConsent, controller.state.value)
        assertEquals("no native start before consent succeeds", 0, engine.startCount)

        consent.release(ConsentOutcome.Granted)
        advanceUntilIdle()
        assertEquals(1, engine.startCount)
        controller.shutdown()
    }

    @Test
    fun `stop moves through stopping to stopped`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        controller.submit(start())
        advanceUntilIdle()
        controller.submit(SessionCommand.Stop)
        advanceUntilIdle()

        assertEquals(VpnLifecycleState.Stopped, controller.state.value)
        assertEquals(1, engine.stopCount)
        assertEquals(StopReason.UserRequested, engine.lastStopReason)
        controller.shutdown()
    }

    @Test
    fun `stop is idempotent after the first accepted stop`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        controller.submit(start())
        advanceUntilIdle()
        repeat(3) {
            controller.submit(SessionCommand.Stop)
            advanceUntilIdle()
        }

        assertEquals(VpnLifecycleState.Stopped, controller.state.value)
        assertEquals("the engine is stopped once, not once per tap", 1, engine.stopCount)
        controller.shutdown()
    }

    @Test
    fun `stop from stopped stays stopped and never reaches the engine`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        controller.submit(SessionCommand.Stop)
        advanceUntilIdle()

        assertEquals(VpnLifecycleState.Stopped, controller.state.value)
        assertEquals(0, engine.stopCount)
        controller.shutdown()
    }

    @Test
    fun `an unlinked engine fails before consent is requested`() = runTest {
        val engine = FakeEngine(isAvailable = false)
        val consent = FixedConsent(ConsentOutcome.Granted)
        val controller = SessionController({ engine }, consent, this, now = { 0L })

        controller.submit(start())
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Failed
        assertEquals(TypedFailure.EngineUnavailable, state.failure)
        assertEquals(Operation.Start, state.operation)
        assertEquals("no permission dialog for a build that cannot use it", 0, consent.calls)
        assertTrue("EngineUnavailable is not recoverable in this build", !state.recoverable)
        controller.shutdown()
    }

    @Test
    fun `refused consent becomes a recoverable failure`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Denied), this, now = { 0L })

        controller.submit(start())
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Failed
        assertEquals(TypedFailure.ConsentDenied, state.failure)
        assertTrue(state.recoverable)
        assertEquals(0, engine.startCount)
        controller.shutdown()
    }

    @Test
    fun `unavailable consent becomes an unrecoverable failure`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Unavailable), this, now = { 0L })

        controller.submit(start())
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Failed
        assertEquals(TypedFailure.ConsentUnavailable, state.failure)
        assertTrue(!state.recoverable)
        controller.shutdown()
    }

    @Test
    fun `an engine refusal is surfaced with its own typed reason`() = runTest {
        val engine = FakeEngine(refuseWith = TypedFailure.BypassDenied)
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        controller.submit(start())
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Failed
        assertEquals(TypedFailure.BypassDenied, state.failure)
        controller.shutdown()
    }

    @Test
    fun `a boundary rejection becomes a failed state without touching the engine`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        controller.submit(
            SessionCommand.Reject(Operation.Start, TypedFailure.InterfaceRejected),
        )
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Failed
        assertEquals(TypedFailure.InterfaceRejected, state.failure)
        assertEquals(Operation.Start, state.operation)
        assertEquals("a rejected configuration never reaches the engine", 0, engine.startCount)
        controller.shutdown()
    }

    // Command coalescing.

    @Test
    fun `a burst of starts produces one session`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        repeat(8) { controller.submit(start()) }
        advanceUntilIdle()

        assertTrue(controller.state.value is VpnLifecycleState.Running)
        assertEquals("repeated taps coalesce rather than spawning sessions", 1, engine.startCount)
        controller.shutdown()
    }

    @Test
    fun `start then stop settles on the reader's last intent`() = runTest {
        val engine = FakeEngine(startDelayMs = 500)
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        controller.submit(start())
        controller.submit(SessionCommand.Stop)
        advanceUntilIdle()

        assertEquals(VpnLifecycleState.Stopped, controller.state.value)
        controller.shutdown()
    }

    // Cancellation.

    @Test
    fun `a start cancelled mid-flight lands on stopped rather than failed`() = runTest {
        val engine = FakeEngine(startDelayMs = 10_000)
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        controller.submit(start())
        // Let the start reach the engine, then interrupt it.
        advanceTimeBy(100)
        controller.submit(SessionCommand.Stop)
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(
            "cancelling is the reader changing their mind, not an error",
            VpnLifecycleState.Stopped,
            state,
        )
        controller.shutdown()
    }

    @Test
    fun `a start cancelled while awaiting consent lands on stopped`() = runTest {
        val engine = FakeEngine()
        val consent = HeldConsent()
        val controller = SessionController({ engine }, consent, this, now = { 0L })

        controller.submit(start())
        consent.reached.await()
        controller.submit(SessionCommand.Stop)
        advanceUntilIdle()

        assertEquals(VpnLifecycleState.Stopped, controller.state.value)
        assertEquals("a cancelled start never reaches the engine", 0, engine.startCount)
        controller.shutdown()
    }

    // Events.

    @Test
    fun `events fold into the running state without changing its identity`() = runTest {
        val engine = FakeEngine(
            eventFlow = flow {
                emit(CoreEvent.Resolved("ads.example.net", "||ads.example.net^", blocked = true, truncated = false))
                emit(CoreEvent.Resolved("example.com", null, blocked = false, truncated = false))
                emit(CoreEvent.Counted(CoreCounters(quicSteered = 3)))
                emit(CoreEvent.Counted(CoreCounters(quicSteered = 4)))
            },
        )
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        controller.submit(start())
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Running
        assertEquals(1, state.status.namesBlocked)
        assertEquals(1, state.status.namesAllowed)
        // Counted reports occurrences since the previous one, so they sum.
        assertEquals(7, state.status.counters.quicSteered)
        assertEquals("the session identity does not change with an event", "s1", state.session.value)
        controller.shutdown()
    }

    @Test
    fun `a resolution reaches the bounded log newest first`() = runTest {
        val engine = FakeEngine(
            eventFlow = flow {
                emit(CoreEvent.Resolved("first.example.com", null, blocked = false, truncated = false))
                emit(CoreEvent.Resolved("second.example.com", null, blocked = true, truncated = false))
            },
        )
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })

        controller.submit(start())
        advanceUntilIdle()

        assertEquals(
            listOf("second.example.com", "first.example.com"),
            controller.resolutions.value.map { it.name },
        )
        controller.shutdown()
    }

    // Reconfigure.

    @Test
    fun `a rule change reaches a running session through reload`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })
        val before = EngineConfig(Filtering.Names(RESOLVER, listOf("||a.example.com^")))
        val after = EngineConfig(Filtering.Names(RESOLVER, listOf("||b.example.com^")))

        controller.submit(SessionCommand.Start(before, platform))
        advanceUntilIdle()
        controller.submit(SessionCommand.Reconfigure(after))
        advanceUntilIdle()

        assertEquals(1, engine.reloadCount)
        assertEquals(listOf("||b.example.com^"), engine.lastLists)
        assertEquals(after, (controller.state.value as VpnLifecycleState.Running).applied)
        controller.shutdown()
    }

    @Test
    fun `a change reload cannot carry is reported instead of silently ignored`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })
        val before = EngineConfig(Filtering.Names(RESOLVER, emptyList()))
        // The resolver is fixed at start; reload replaces the rules and nothing else.
        val after = EngineConfig(Filtering.Names(OTHER_RESOLVER, emptyList()))

        controller.submit(SessionCommand.Start(before, platform))
        advanceUntilIdle()
        controller.submit(SessionCommand.Reconfigure(after))
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Failed
        assertEquals(TypedFailure.RestartRequired, state.failure)
        assertEquals(0, engine.reloadCount)
        controller.shutdown()
    }

    @Test
    fun `a refused reload names the status the core returned`() = runTest {
        val engine = FakeEngine(reloadWith = CoreStatus.Stopped)
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this, now = { 0L })
        val config = EngineConfig(Filtering.Names(RESOLVER, emptyList()))

        controller.submit(SessionCommand.Start(config, platform))
        advanceUntilIdle()
        controller.submit(SessionCommand.Reconfigure(config.copy(filtering = Filtering.Names(RESOLVER, listOf("x")))))
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Failed
        assertEquals(TypedFailure.CoreRefused(Operation.Reload, CoreStatus.Stopped), state.failure)
        controller.shutdown()
    }

    private companion object {
        val RESOLVER = (Endpoint.parse("9.9.9.9") as Parsed.Valid).value
        val OTHER_RESOLVER = (Endpoint.parse("1.1.1.1") as Parsed.Valid).value
    }
}

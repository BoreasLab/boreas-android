package dev.boreaslab.boreas.service

import dev.boreaslab.boreas.engine.EngineHost
import dev.boreaslab.boreas.engine.EngineStart
import dev.boreaslab.boreas.engine.StopReason
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.Ipv4Address
import dev.boreaslab.boreas.model.Mtu
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.Parsed
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.model.TypedFailure
import dev.boreaslab.boreas.model.UpstreamRoute
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
        val statusFlow: Flow<SessionStatus> = emptyFlow(),
    ) : EngineHost {
        var startCount = 0
        var stopCount = 0
        var lastStopReason: StopReason? = null

        override suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart {
            startCount += 1
            if (startDelayMs > 0) delay(startDelayMs)
            refuseWith?.let { return EngineStart.Refused(it) }
            return EngineStart.Started(
                SessionId("s$startCount"),
                SessionStatus.initial(0, UpstreamRoute.Direct, simulated = true),
            )
        }

        override suspend fun stop(session: SessionId, reason: StopReason) {
            stopCount += 1
            lastStopReason = reason
        }

        override fun status(session: SessionId) = statusFlow
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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)
        val config = EngineConfig(inspectTls = true)

        controller.submit(SessionCommand.Start(config, platform))
        advanceUntilIdle()

        assertEquals(config, (controller.state.value as VpnLifecycleState.Running).applied)
        controller.shutdown()
    }

    @Test
    fun `awaits consent before the engine is asked to start`() = runTest {
        val engine = FakeEngine()
        val consent = HeldConsent()
        val controller = SessionController({ engine }, consent, this)

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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

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
        val controller = SessionController({ engine }, consent, this)

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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Denied), this)

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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Unavailable), this)

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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

        controller.submit(start())
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Failed
        assertEquals(TypedFailure.BypassDenied, state.failure)
        controller.shutdown()
    }

    @Test
    fun `a boundary rejection becomes a failed state without touching the engine`() = runTest {
        val engine = FakeEngine()
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

        repeat(8) { controller.submit(start()) }
        advanceUntilIdle()

        assertTrue(controller.state.value is VpnLifecycleState.Running)
        assertEquals("repeated taps coalesce rather than spawning sessions", 1, engine.startCount)
        controller.shutdown()
    }

    @Test
    fun `start then stop settles on the reader's last intent`() = runTest {
        val engine = FakeEngine(startDelayMs = 500)
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

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
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

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
        val controller = SessionController({ engine }, consent, this)

        controller.submit(start())
        consent.reached.await()
        controller.submit(SessionCommand.Stop)
        advanceUntilIdle()

        assertEquals(VpnLifecycleState.Stopped, controller.state.value)
        assertEquals("a cancelled start never reaches the engine", 0, engine.startCount)
        controller.shutdown()
    }

    // Status.

    @Test
    fun `status snapshots update the running state in place`() = runTest {
        val later = SessionStatus.initial(0, UpstreamRoute.Direct, simulated = true)
            .copy(flowsAccepted = 42)
        val engine = FakeEngine(statusFlow = flow { emit(later) })
        val controller = SessionController({ engine }, FixedConsent(ConsentOutcome.Granted), this)

        controller.submit(start())
        advanceUntilIdle()

        val state = controller.state.value as VpnLifecycleState.Running
        assertEquals(42, state.status.flowsAccepted)
        assertEquals("the session identity does not change with a snapshot", "s1", state.session.value)
        controller.shutdown()
    }
}

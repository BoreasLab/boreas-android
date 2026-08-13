package dev.boreaslab.boreas.service

import dev.boreaslab.boreas.engine.EngineHost
import dev.boreaslab.boreas.engine.EngineStart
import dev.boreaslab.boreas.engine.StopReason
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the surface can ask the session owner to do. A closed set. */
sealed interface SessionCommand {
    data class Start(val engine: EngineConfig, val platform: PlatformConfig) : SessionCommand
    data object Stop : SessionCommand
}

/**
 * The single owner of the session lifecycle.
 *
 * Three rules from docs/platform-integration.md are enforced here rather than left
 * to the discipline of call sites:
 *
 *  1. "At most one start or stop transition may own the session at a time."
 *     One consumer loop and one [current] job. A new command cancels the in-flight
 *     one and joins it before starting its own, so two transitions never overlap.
 *
 *  2. "Repeated Start and Stop commands coalesce at the service owner; they do not
 *     spawn independent coroutines." The command channel holds one slot and drops
 *     the older entry, so a burst of taps collapses to the reader's last intent.
 *
 *  3. "Cancellation remains inside the service scope and is never caught as an
 *     ordinary failure." Every handler rethrows CancellationException after
 *     releasing what it acquired, and a cancelled start lands on Stopped rather
 *     than Failed, because the reader changing their mind is not an error.
 *
 * The class holds no Android type, so the whole state machine is unit testable
 * against a fake [EngineHost] and a fake [ConsentGate].
 */
class SessionController(
    private val engineProvider: suspend () -> EngineHost,
    private val consent: ConsentGate,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<VpnLifecycleState>(VpnLifecycleState.Stopped)

    /** Bounded latest-state stream. A slow reader sees the newest state, not a backlog. */
    val state: StateFlow<VpnLifecycleState> = _state.asStateFlow()

    private val commands = Channel<SessionCommand>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var current: Job? = null
    private var statusJob: Job? = null

    /** The host the running session belongs to. Resolved once per start. */
    private var active: EngineHost? = null

    private val loop = scope.launch {
        for (command in commands) {
            current?.cancelAndJoin()
            current = launch {
                when (command) {
                    is SessionCommand.Start -> runStart(command)
                    SessionCommand.Stop -> runStop()
                }
            }
        }
    }

    fun submit(command: SessionCommand) {
        commands.trySend(command)
    }

    /**
     * Startup order from docs/core-contract.md: validate, obtain consent, then start.
     *
     * Validation includes whether an engine exists at all, so a build without one
     * never raises a VPN permission dialog it could not honor.
     */
    private suspend fun runStart(command: SessionCommand.Start) {
        try {
            val engine = engineProvider()
            active = engine

            if (!engine.isAvailable) {
                _state.value =
                    VpnLifecycleState.Failed(Operation.Start, TypedFailure.EngineUnavailable)
                return
            }

            _state.value = VpnLifecycleState.AwaitingConsent
            when (consent.request()) {
                ConsentOutcome.Granted -> Unit
                ConsentOutcome.Denied -> {
                    _state.value =
                        VpnLifecycleState.Failed(Operation.Start, TypedFailure.ConsentDenied)
                    return
                }
                ConsentOutcome.Unavailable -> {
                    _state.value =
                        VpnLifecycleState.Failed(Operation.Start, TypedFailure.ConsentUnavailable)
                    return
                }
            }

            _state.value = VpnLifecycleState.Starting

            // A3 establishes the TUN here and moves the descriptor across in one
            // step. Until a native owner exists there is nothing to detach, so no
            // descriptor is created and none can leak.
            when (val outcome = engine.start(command.engine, command.platform)) {
                is EngineStart.Started -> {
                    _state.value =
                        VpnLifecycleState.Running(outcome.session, outcome.status, command.engine)
                    followStatus(engine, outcome.session)
                }
                is EngineStart.Refused ->
                    _state.value = VpnLifecycleState.Failed(Operation.Start, outcome.failure)
            }
        } catch (cancellation: CancellationException) {
            // A cancelled start releases what it acquired and reports the resting
            // state, not a failure. Rethrown so the scope stays consistent.
            releaseStatus()
            active = null
            _state.value = VpnLifecycleState.Stopped
            throw cancellation
        }
    }

    private suspend fun runStop() {
        // Eliminated exhaustively: a new lifecycle variant must state here whether
        // it owns a session to stop, rather than falling into a catch-all that
        // would silently do nothing.
        val session = when (val now = _state.value) {
            is VpnLifecycleState.Running -> now.session
            is VpnLifecycleState.Stopping -> now.session
            // Nothing owns a session, so stopping is already true. The contract
            // makes stop idempotent after the first accepted stop.
            VpnLifecycleState.Stopped,
            VpnLifecycleState.AwaitingConsent,
            VpnLifecycleState.Starting,
            is VpnLifecycleState.Failed,
            -> {
                _state.value = VpnLifecycleState.Stopped
                return
            }
        }

        try {
            releaseStatus()
            _state.value = VpnLifecycleState.Stopping(session)
            active?.stop(session, StopReason.UserRequested)
            active = null
            _state.value = VpnLifecycleState.Stopped
        } catch (cancellation: CancellationException) {
            active = null
            _state.value = VpnLifecycleState.Stopped
            throw cancellation
        }
    }

    /**
     * Mirrors the running session's counters into the state.
     *
     * Scoped to the session it was started for: a snapshot arriving after that
     * session ended is dropped rather than applied to a newer one.
     */
    private fun followStatus(engine: EngineHost, session: SessionId) {
        releaseStatus()
        statusJob = scope.launch {
            engine.status(session).collect { status ->
                val now = _state.value
                if (now is VpnLifecycleState.Running && now.session == session) {
                    _state.value = now.copy(status = status)
                }
            }
        }
    }

    private fun releaseStatus() {
        statusJob?.cancel()
        statusJob = null
    }

    suspend fun shutdown() {
        releaseStatus()
        current?.cancelAndJoin()
        commands.close()
        loop.cancelAndJoin()
    }
}

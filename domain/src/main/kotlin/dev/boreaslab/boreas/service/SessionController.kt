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
import kotlinx.coroutines.launch

/** What the surface can ask the session owner to do. A closed set. */
public sealed interface SessionCommand {
    public data class Start(val engine: EngineConfig, val platform: PlatformConfig) : SessionCommand

    /** Platform-boundary parse failed before startup; controller remains sole state writer. */
    public data class Reject(val operation: Operation, val failure: TypedFailure) : SessionCommand

    public data object Stop : SessionCommand
}

/**
 * Owns session lifecycle.
 *
 * One command loop cancels and joins the prior transition before starting the next.
 * A one-slot channel drops stale commands. Cancellation releases resources, returns
 * to [VpnLifecycleState.Stopped], and is rethrown rather than treated as failure.
 */
public class SessionController(
    private val engineProvider: suspend () -> EngineHost,
    private val consent: ConsentGate,
    private val scope: CoroutineScope,
) {

    /** One bounded latest-state cell; callers see only a read-only [StateFlow]. */
    public val state: StateFlow<VpnLifecycleState>
        field = MutableStateFlow<VpnLifecycleState>(VpnLifecycleState.Stopped)

    private val commands = Channel<SessionCommand>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var current: Job? = null
    private var statusJob: Job? = null

    private var active: EngineHost? = null

    private val loop = scope.launch {
        for (command in commands) {
            current?.cancelAndJoin()
            current = launch {
                when (command) {
                    is SessionCommand.Start -> runStart(command)
                    is SessionCommand.Reject ->
                        state.value = VpnLifecycleState.Failed(command.operation, command.failure)
                    SessionCommand.Stop -> runStop()
                }
            }
        }
    }

    public fun submit(command: SessionCommand) {
        commands.trySend(command)
    }

    /** Validate, obtain consent, then start; unavailable engines never prompt for consent. */
    private suspend fun runStart(command: SessionCommand.Start) {
        try {
            val engine = engineProvider()
            active = engine

            if (!engine.isAvailable) {
                state.value =
                    VpnLifecycleState.Failed(Operation.Start, TypedFailure.EngineUnavailable)
                return
            }

            state.value = VpnLifecycleState.AwaitingConsent
            when (consent.request()) {
                ConsentOutcome.Granted -> Unit
                ConsentOutcome.Denied -> {
                    state.value =
                        VpnLifecycleState.Failed(Operation.Start, TypedFailure.ConsentDenied)
                    return
                }
                ConsentOutcome.Unavailable -> {
                    state.value =
                        VpnLifecycleState.Failed(Operation.Start, TypedFailure.ConsentUnavailable)
                    return
                }
            }

            state.value = VpnLifecycleState.Starting

            // A3 creates the TUN and transfers its descriptor to native in one step; until
            // then, create none.
            when (val outcome = engine.start(command.engine, command.platform)) {
                is EngineStart.Started -> {
                    state.value =
                        VpnLifecycleState.Running(outcome.session, outcome.status, command.engine)
                    followStatus(engine, outcome.session)
                }
                is EngineStart.Refused ->
                    state.value = VpnLifecycleState.Failed(Operation.Start, outcome.failure)
            }
        } catch (cancellation: CancellationException) {
            // Cancellation releases acquired resources, returns to Stopped, and is rethrown.
            releaseStatus()
            active = null
            state.value = VpnLifecycleState.Stopped
            throw cancellation
        }
    }

    private suspend fun runStop() {
        // Exhaustive match prevents a new lifecycle variant from silently ignoring Stop.
        val session = when (val now = state.value) {
            is VpnLifecycleState.Running -> now.session
            is VpnLifecycleState.Stopping -> now.session
            // No state owns a session, so Stop is already idempotent.
            VpnLifecycleState.Stopped,
            VpnLifecycleState.AwaitingConsent,
            VpnLifecycleState.Starting,
            is VpnLifecycleState.Failed,
            -> {
                state.value = VpnLifecycleState.Stopped
                return
            }
        }

        try {
            releaseStatus()
            state.value = VpnLifecycleState.Stopping(session)
            active?.stop(session, StopReason.UserRequested)
            active = null
            state.value = VpnLifecycleState.Stopped
        } catch (cancellation: CancellationException) {
            active = null
            state.value = VpnLifecycleState.Stopped
            throw cancellation
        }
    }

    /** Drops snapshots from ended sessions instead of applying them to newer ones. */
    private fun followStatus(engine: EngineHost, session: SessionId) {
        releaseStatus()
        statusJob = scope.launch {
            engine.status(session).collect { status ->
                val now = state.value
                if (now is VpnLifecycleState.Running && now.session == session) {
                    state.value = now.copy(status = status)
                }
            }
        }
    }

    private fun releaseStatus() {
        statusJob?.cancel()
        statusJob = null
    }

    public suspend fun shutdown() {
        releaseStatus()
        current?.cancelAndJoin()
        commands.close()
        loop.cancelAndJoin()
    }
}

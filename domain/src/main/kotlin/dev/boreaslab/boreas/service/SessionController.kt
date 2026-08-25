package dev.boreaslab.boreas.service

import dev.boreaslab.boreas.engine.EngineHost
import dev.boreaslab.boreas.engine.EngineStart
import dev.boreaslab.boreas.engine.StopReason
import dev.boreaslab.boreas.model.CoreEvent
import dev.boreaslab.boreas.model.CoreStatus
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.Filtering
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.ResolvedName
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.model.TypedFailure
import dev.boreaslab.boreas.model.reachesRunning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Commands accepted by the session owner. */
public sealed interface SessionCommand {
    public data class Start(val engine: EngineConfig, val platform: PlatformConfig) : SessionCommand

    /** Pushes a changed policy at a running session, or reports why it cannot. */
    public data class Reconfigure(val engine: EngineConfig) : SessionCommand

    /** Reports a platform-boundary parse failure before startup. */
    public data class Reject(val operation: Operation, val failure: TypedFailure) : SessionCommand

    public data object Stop : SessionCommand
}

/**
 * Owns session lifecycle. The command loop cancels and joins each prior transition
 * before starting the next; its one-slot channel drops stale commands. Cancellation
 * releases resources, returns to [VpnLifecycleState.Stopped], and is rethrown.
 * This is also the sole reader of the engine event stream because the ABI permits
 * one reader at a time.
 */
public class SessionController(
    private val engineProvider: suspend () -> EngineHost,
    private val consent: ConsentGate,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {

    /** Bounded latest-state cell exposed as a read-only [StateFlow]. */
    public val state: StateFlow<VpnLifecycleState>
        field = MutableStateFlow<VpnLifecycleState>(VpnLifecycleState.Stopped)

    /** Newest-first bounded resolutions, separate from lifecycle state. */
    public val resolutions: StateFlow<List<ResolvedName>>
        field = MutableStateFlow<List<ResolvedName>>(emptyList())

    private val commands = Channel<SessionCommand>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var current: Job? = null
    private var eventJob: Job? = null

    private var active: EngineHost? = null

    private val loop = scope.launch {
        for (command in commands) {
            current?.cancelAndJoin()
            current = launch {
                when (command) {
                    is SessionCommand.Start -> runStart(command)
                    is SessionCommand.Reconfigure -> runReconfigure(command)
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

    /** Validates, obtains consent, then starts; unavailable engines never prompt. */
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
            resolutions.value = emptyList()

            when (val outcome = engine.start(command.engine, command.platform)) {
                is EngineStart.Started -> {
                    state.value = VpnLifecycleState.Running(
                        session = outcome.session,
                        status = SessionStatus.initial(now(), simulated = engine.simulated),
                        applied = command.engine,
                    )
                    followEvents(engine, outcome.session)
                }
                is EngineStart.Refused ->
                    state.value = VpnLifecycleState.Failed(Operation.Start, outcome.failure)
            }
        } catch (cancellation: CancellationException) {
            releaseEvents()
            active = null
            state.value = VpnLifecycleState.Stopped
            throw cancellation
        }
    }

    /**
     * Pushes a policy change or reports that it needs a restart. Reload changes only
     * rules; [reachesRunning] enforces the fixed resolver, hosts, egress, and ceilings.
     */
    private suspend fun runReconfigure(command: SessionCommand.Reconfigure) {
        val running = state.value as? VpnLifecycleState.Running ?: return
        val engine = active ?: return

        if (!running.applied.reachesRunning(command.engine)) {
            state.value =
                VpnLifecycleState.Failed(Operation.Reconfigure, TypedFailure.RestartRequired)
            return
        }

        val lists = (command.engine.filtering as? Filtering.Names)?.lists.orEmpty()
        when (val status = engine.reload(running.session, lists)) {
            CoreStatus.Ok -> state.value = running.copy(applied = command.engine)
            else -> state.value = VpnLifecycleState.Failed(
                Operation.Reload,
                TypedFailure.CoreRefused(Operation.Reload, status),
            )
        }
    }

    private suspend fun runStop() {
        val session = when (val now = state.value) {
            is VpnLifecycleState.Running -> now.session
            is VpnLifecycleState.Stopping -> now.session
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
            releaseEvents()
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

    /** Folds events into the matching running session and ignores stale sessions. */
    private fun followEvents(engine: EngineHost, session: SessionId) {
        releaseEvents()
        eventJob = scope.launch {
            engine.events(session).collect { event ->
                val running = state.value
                if (running !is VpnLifecycleState.Running || running.session != session) return@collect

                state.value = running.copy(status = running.status.after(event))
                if (event is CoreEvent.Resolved) record(event)
            }
        }
    }

    /** Records a resolution in O(RESOLUTION_LIMIT) time. */
    private fun record(event: CoreEvent.Resolved) {
        val entry = ResolvedName(
            atMillis = now(),
            name = event.name,
            rule = event.rule,
            blocked = event.blocked,
            truncated = event.truncated,
        )
        resolutions.value = (listOf(entry) + resolutions.value).take(RESOLUTION_LIMIT)
    }

    private fun releaseEvents() {
        eventJob?.cancel()
        eventJob = null
    }

    /**
     * Ends the command loop before stopping the active engine. Native resources do
     * not release when the coroutine scope is cancelled, and closing the loop first
     * prevents [runStop] from racing a queued stop.
     */
    public suspend fun shutdown() {
        commands.close()
        loop.cancelAndJoin()
        current?.cancelAndJoin()
        releaseEvents()
        runStop()
    }

    private companion object {
        /** Bounds retained resolutions while keeping prepending cheap. */
        const val RESOLUTION_LIMIT = 200
    }
}

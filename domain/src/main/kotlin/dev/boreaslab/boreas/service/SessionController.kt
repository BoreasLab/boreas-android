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

/** What the surface can ask the session owner to do. A closed set. */
public sealed interface SessionCommand {
    public data class Start(val engine: EngineConfig, val platform: PlatformConfig) : SessionCommand

    /** Push a changed policy at a running session, or say why it cannot be pushed. */
    public data class Reconfigure(val engine: EngineConfig) : SessionCommand

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
 *
 * It is also the single reader of the engine's event stream. The ABI allows one
 * reader at a time and queues a second behind the first, so a second collector
 * elsewhere would not race, it would quietly take half the events. Everything the
 * UI knows about a session is therefore folded here and published from here.
 */
public class SessionController(
    private val engineProvider: suspend () -> EngineHost,
    private val consent: ConsentGate,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {

    /** One bounded latest-state cell; callers see only a read-only [StateFlow]. */
    public val state: StateFlow<VpnLifecycleState>
        field = MutableStateFlow<VpnLifecycleState>(VpnLifecycleState.Stopped)

    /**
     * Newest-first, bounded.
     *
     * Separate from [state] on purpose: a resolution arrives per DNS question, and
     * folding it into the lifecycle value would make every question a lifecycle
     * change for anything comparing them.
     */
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
            // Cancellation releases acquired resources, returns to Stopped, and is rethrown.
            releaseEvents()
            active = null
            state.value = VpnLifecycleState.Stopped
            throw cancellation
        }
    }

    /**
     * Push a policy change at the running session, or refuse it as needing a restart.
     *
     * Reload replaces the rules in force and nothing else: the resolver, the
     * intercepted host list, the egress, and the ceilings are fixed at start. That
     * is the core's rule, so the decision is [reachesRunning]'s and not this
     * method's, and a change that does not reach becomes a typed failure rather
     * than a silent no-op.
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

    /**
     * Folds the event stream into the running state, dropping anything from a
     * session that has since ended rather than applying it to a newer one.
     */
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

    /** O(RESOLUTION_LIMIT) per question, which is a bounded copy of a small list. */
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
     * Ends the loop, then stops whatever it was running.
     *
     * In that order. The engine's resources are not coroutines -- a native handle
     * and a real reader thread -- so cancelling the scope releases none of them,
     * and a stop submitted as a command would be cancelled along with everything
     * else. The loop is closed first so that [runStop] here cannot race one
     * running there.
     */
    public suspend fun shutdown() {
        commands.close()
        loop.cancelAndJoin()
        current?.cancelAndJoin()
        releaseEvents()
        runStop()
    }

    private companion object {
        /** Enough to read, small enough that prepending stays cheap under a flood. */
        const val RESOLUTION_LIMIT = 200
    }
}

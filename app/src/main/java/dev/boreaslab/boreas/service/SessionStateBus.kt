package dev.boreaslab.boreas.service

import dev.boreaslab.boreas.model.ResolvedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TransitionRecord(val atMillis: Long, val state: VpnLifecycleState)

/**
 * Service-owned latest-state bus for UI; commands and packets never cross it.
 * Mutable backing fields stay private while callers receive read-only flows.
 */
object SessionStateBus {

    private const val LOG_LIMIT = 50

    val state: StateFlow<VpnLifecycleState>
        field = MutableStateFlow<VpnLifecycleState>(VpnLifecycleState.Stopped)

    /** Unobserved until the service queries Android; distinct from [AlwaysOn.Off]. */
    val alwaysOn: StateFlow<AlwaysOn>
        field = MutableStateFlow<AlwaysOn>(AlwaysOn.Unobserved)

    /** Newest-first in-memory transition log. */
    val log: StateFlow<List<TransitionRecord>>
        field = MutableStateFlow<List<TransitionRecord>>(emptyList())

    /**
     * Newest-first answered questions, mirrored from the controller.
     *
     * Bounded there, not here: the controller is the single reader of the event
     * stream, so it is also the only thing that can decide what a full list means.
     */
    val resolutions: StateFlow<List<ResolvedName>>
        field = MutableStateFlow<List<ResolvedName>>(emptyList())

    internal fun publish(state: VpnLifecycleState, atMillis: Long) {
        this.state.value = state
        log.value = (listOf(TransitionRecord(atMillis, state)) + log.value).take(LOG_LIMIT)
    }

    /** Counter updates avoid adding log entries. */
    internal fun publishStatusOnly(state: VpnLifecycleState) {
        this.state.value = state
    }

    internal fun publishResolutions(entries: List<ResolvedName>) {
        resolutions.value = entries
    }

    internal fun publishAlwaysOn(state: AlwaysOn) {
        alwaysOn.value = state
    }

    internal fun clearLog() {
        log.value = emptyList()
    }

    internal fun restoreLog(records: List<TransitionRecord>) {
        log.value = records.take(LOG_LIMIT)
    }
}

package dev.boreaslab.boreas.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One recorded lifecycle transition, for the Diagnostics screen. */
data class TransitionRecord(val atMillis: Long, val state: VpnLifecycleState)

/**
 * How service state reaches the UI.
 *
 * "Service-to-UI state delivery with a bounded latest-state stream" is an Android
 * responsibility in docs/platform-integration.md. A StateFlow is exactly that: a
 * reader that falls behind sees the newest state rather than a queue of stale ones,
 * and no command or packet travels this way.
 *
 * Only [BoreasVpnService] writes here. The UI reads, which keeps the service the
 * single owner of the session even though the state outlives any one screen.
 *
 * Each cell is one property with an explicit backing field, so there is one name
 * per cell rather than a private mutable one shadowed by a public read-only one.
 * Writing is possible only from inside this object, where the compiler sees the
 * mutable type; every reader outside sees a [StateFlow] and cannot be handed a
 * setter. The mutators below are `internal`, so the writer set is closed at the
 * module boundary as well as by convention.
 */
object SessionStateBus {

    private const val LOG_LIMIT = 50

    val state: StateFlow<VpnLifecycleState>
        field = MutableStateFlow<VpnLifecycleState>(VpnLifecycleState.Stopped)

    /**
     * Whether Android is keeping this tunnel up on its own.
     *
     * Readable only from the running service, so this stays [AlwaysOn.Unobserved]
     * until the service has run once in this process. That is not the same as
     * "off", and the interface says so rather than guessing.
     */
    val alwaysOn: StateFlow<AlwaysOn>
        field = MutableStateFlow<AlwaysOn>(AlwaysOn.Unobserved)

    /** Newest first, bounded. Held in memory only and never written to disk. */
    val log: StateFlow<List<TransitionRecord>>
        field = MutableStateFlow<List<TransitionRecord>>(emptyList())

    internal fun publish(state: VpnLifecycleState, atMillis: Long) {
        this.state.value = state
        log.value = (listOf(TransitionRecord(atMillis, state)) + log.value).take(LOG_LIMIT)
    }

    /** Status snapshots update the running state without adding a log entry each second. */
    internal fun publishStatusOnly(state: VpnLifecycleState) {
        this.state.value = state
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

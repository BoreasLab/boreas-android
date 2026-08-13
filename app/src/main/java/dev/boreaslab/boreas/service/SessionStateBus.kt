package dev.boreaslab.boreas.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 */
object SessionStateBus {

    private const val LOG_LIMIT = 50

    private val _state = MutableStateFlow<VpnLifecycleState>(VpnLifecycleState.Stopped)
    val state: StateFlow<VpnLifecycleState> = _state.asStateFlow()

    private val _alwaysOn = MutableStateFlow<AlwaysOn>(AlwaysOn.Unobserved)

    /**
     * Whether Android is keeping this tunnel up on its own.
     *
     * Readable only from the running service, so this stays [AlwaysOn.Unobserved]
     * until the service has run once in this process. That is not the same as
     * "off", and the interface says so rather than guessing.
     */
    val alwaysOn: StateFlow<AlwaysOn> = _alwaysOn.asStateFlow()

    private val _log = MutableStateFlow<List<TransitionRecord>>(emptyList())

    /** Newest first, bounded. Held in memory only and never written to disk. */
    val log: StateFlow<List<TransitionRecord>> = _log.asStateFlow()

    internal fun publish(state: VpnLifecycleState, atMillis: Long) {
        _state.value = state
        _log.value = (listOf(TransitionRecord(atMillis, state)) + _log.value).take(LOG_LIMIT)
    }

    /** Status snapshots update the running state without adding a log entry each second. */
    internal fun publishStatusOnly(state: VpnLifecycleState) {
        _state.value = state
    }

    internal fun publishAlwaysOn(state: AlwaysOn) {
        _alwaysOn.value = state
    }

    internal fun clearLog() {
        _log.value = emptyList()
    }

    internal fun restoreLog(records: List<TransitionRecord>) {
        _log.value = records.take(LOG_LIMIT)
    }
}

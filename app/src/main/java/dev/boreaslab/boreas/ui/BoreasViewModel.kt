package dev.boreaslab.boreas.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boreaslab.boreas.BuildConfig
import dev.boreaslab.boreas.data.SettingsRepository
import dev.boreaslab.boreas.design.ThemeChoice
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.RuleProfile
import dev.boreaslab.boreas.model.TunnelDraft
import dev.boreaslab.boreas.model.TunnelParse
import dev.boreaslab.boreas.model.UpstreamRoute
import dev.boreaslab.boreas.service.AlwaysOn
import dev.boreaslab.boreas.service.BoreasVpnService
import dev.boreaslab.boreas.service.ConsentBroker
import dev.boreaslab.boreas.service.ConsentOutcome
import dev.boreaslab.boreas.service.SessionStateBus
import dev.boreaslab.boreas.service.TransitionRecord
import dev.boreaslab.boreas.service.VpnLifecycleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One installed app, as the Apps screen needs it.
 *
 * [searchKey] is folded in when the list loads rather than recomputed per
 * keystroke: lowercasing a label allocates a String, and doing that for every app
 * on every character typed is work proportional to list size times query length
 * for a result that never changes.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val searchKey: String,
) {
    companion object {
        fun of(packageName: String, label: String) =
            InstalledApp(packageName, label, "${label.lowercase()}\n$packageName")
    }
}

/**
 * The single state holder for the whole surface.
 *
 * Effects live here and nowhere below: sending a command to the service, reading
 * the package manager, and writing preferences. Everything under this is a pure
 * function of its inputs, which is what makes the screens previewable in isolation.
 *
 * Nothing here decides anything about packets. Commands go one way and typed state
 * comes back the other, exactly as docs/core-contract.md requires of the UI.
 */
class BoreasViewModel(private val app: Application) : ViewModel() {

    private val settings = SettingsRepository(app)

    val sessionState: StateFlow<VpnLifecycleState> = SessionStateBus.state
    val alwaysOn: StateFlow<AlwaysOn> = SessionStateBus.alwaysOn
    val transitions: StateFlow<List<TransitionRecord>> = SessionStateBus.log

    val themeChoice: StateFlow<ThemeChoice> =
        settings.themeChoice.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeChoice.System)

    val engineConfig: StateFlow<EngineConfig> =
        settings.engineConfig.stateIn(viewModelScope, SharingStarted.Eagerly, EngineConfig())

    val certificateInstalled: StateFlow<Boolean> =
        settings.certificateInstallCompleted.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val simulationEnabled: StateFlow<Boolean> =
        settings.simulationEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val simulationAvailable: Boolean = BuildConfig.SIMULATION_AVAILABLE

    val excludedPackages: StateFlow<Set<String>> =
        settings.excludedPackages.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * The tunnel form's live text.
     *
     * Seeded from storage once and edited in memory afterwards, so a keystroke does
     * not race a disk write. It is written back on every change, which is what makes
     * a half-typed entry survive the process being killed in the background.
     */
    val tunnelDraft: StateFlow<TunnelDraft?>
        field = MutableStateFlow<TunnelDraft?>(null)

    /**
     * Validation of the live draft.
     *
     * Derived during collection rather than stored, so it can never disagree with
     * the text it describes.
     */
    val tunnelParse: StateFlow<TunnelParse?> =
        combine(tunnelDraft, excludedPackages) { draft, excluded ->
            draft?.let { TunnelParse.of(it, excluded) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val installedApps: StateFlow<List<InstalledApp>?>
        field = MutableStateFlow<List<InstalledApp>?>(null)

    val appSearch: StateFlow<String>
        field = MutableStateFlow("")

    /** Apps matching the current search, indexed once per query rather than per row. */
    val visibleApps: StateFlow<List<InstalledApp>?> =
        combine(installedApps, appSearch) { apps, query ->
            if (apps == null) return@combine null
            if (query.isBlank()) return@combine apps
            val needle = query.trim().lowercase()
            apps.filter { it.searchKey.contains(needle) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { tunnelDraft.value = settings.tunnelDraft.first() }
    }

    // Session commands. Both are idempotent at the service, so a repeated tap
    // coalesces there rather than being guarded here with a flag that could drift.

    fun startTunnel() {
        app.startService(Intent(app, BoreasVpnService::class.java).setAction(BoreasVpnService.ACTION_START))
    }

    fun stopTunnel() {
        app.startService(Intent(app, BoreasVpnService::class.java).setAction(BoreasVpnService.ACTION_STOP))
    }

    /** Completing the pending slot cannot suspend, so this needs no coroutine. */
    fun deliverConsent(outcome: ConsentOutcome) = ConsentBroker.deliver(outcome)

    // Preferences.

    fun setTheme(choice: ThemeChoice) = viewModelScope.launch { settings.setTheme(choice) }

    fun setProfile(profile: RuleProfile) = updateEngineConfig { it.copy(profile = profile) }

    fun setInspectTls(enabled: Boolean) = updateEngineConfig { it.copy(inspectTls = enabled) }

    fun setUpstream(route: UpstreamRoute) = updateEngineConfig { it.copy(upstream = route) }

    private fun updateEngineConfig(change: (EngineConfig) -> EngineConfig) {
        viewModelScope.launch { settings.updateEngineConfig(change) }
    }

    fun setTunnelDraft(draft: TunnelDraft) {
        tunnelDraft.value = draft
        viewModelScope.launch { settings.setTunnelDraft(draft) }
    }

    /**
     * The Apps screen's search text.
     *
     * A method rather than a publicly mutable flow, so this holder is the only
     * writer of every cell it exposes and the screens stay pure functions of what
     * they are given.
     */
    fun setAppSearch(query: String) {
        appSearch.value = query
    }

    fun setAppExcluded(packageName: String, excluded: Boolean) {
        viewModelScope.launch {
            settings.updateExcludedPackages { current ->
                if (excluded) current + packageName else current - packageName
            }
        }
    }

    fun setSimulationEnabled(enabled: Boolean) =
        viewModelScope.launch { settings.setSimulationEnabled(enabled) }

    fun setCertificateInstalled(installed: Boolean) =
        viewModelScope.launch { settings.setCertificateInstallCompleted(installed) }

    fun clearTransitions() = SessionStateBus.clearLog()

    fun restoreTransitions(records: List<TransitionRecord>) = SessionStateBus.restoreLog(records)

    /**
     * Reads the launcher-visible app list off the main thread.
     *
     * One `queryIntentActivities` call answers "which apps does the reader see in
     * their launcher", instead of asking the package manager the same question once
     * per installed app: that inner call is a binder round trip, so the loop was
     * paying hundreds of IPCs to compute what a single query already returns.
     *
     * $O(n)$ label loads, which is irreducible because a name has to come from
     * somewhere, plus one IPC for the set itself. Sorted with a collator built
     * once rather than per comparison.
     */
    fun loadInstalledApps() {
        if (installedApps.value != null) return
        viewModelScope.launch {
            installedApps.value = withContext(Dispatchers.IO) {
                val manager = app.packageManager
                val collator = java.text.Collator.getInstance()
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                runCatching {
                    manager.queryIntentActivities(launcher, 0)
                        .asSequence()
                        .map { it.activityInfo.applicationInfo }
                        .distinctBy { it.packageName }
                        .filter { it.packageName != app.packageName }
                        .map { InstalledApp.of(it.packageName, manager.getApplicationLabel(it).toString()) }
                        .sortedWith { a, b -> collator.compare(a.label, b.label) }
                        .toList()
                }.getOrDefault(emptyList())
            }
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { BoreasViewModel(app) }
        }
    }
}

/** Whether the saved policy differs from the one the running session started with. */
fun VpnLifecycleState.pendingPolicyChange(saved: EngineConfig): Boolean =
    this is VpnLifecycleState.Running && applied != saved

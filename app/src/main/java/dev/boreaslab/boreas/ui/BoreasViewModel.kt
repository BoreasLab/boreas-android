package dev.boreaslab.boreas.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boreaslab.boreas.BuildConfig
import dev.boreaslab.boreas.data.SettingsRepository
import dev.boreaslab.boreas.design.ThemeChoice
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.RuleProfile
import dev.boreaslab.boreas.model.TunnelDraft
import dev.boreaslab.boreas.model.TunnelValidation
import dev.boreaslab.boreas.model.UpstreamRoute
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

/** One installed app, as the Apps screen needs it. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

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
    private val _tunnelDraft = MutableStateFlow<TunnelDraft?>(null)
    val tunnelDraft: StateFlow<TunnelDraft?> = _tunnelDraft

    /**
     * Validation of the live draft.
     *
     * Derived during collection rather than stored, so it can never disagree with
     * the text it describes.
     */
    val tunnelValidation: StateFlow<TunnelValidation?> =
        combine(_tunnelDraft, excludedPackages) { draft, excluded ->
            draft?.let { PlatformConfig.parse(it, excluded) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    val installedApps: StateFlow<List<InstalledApp>?> = _installedApps

    val appSearch = MutableStateFlow("")

    /** Apps matching the current search, indexed once per query rather than per row. */
    val visibleApps: StateFlow<List<InstalledApp>?> =
        combine(_installedApps, appSearch) { apps, query ->
            if (apps == null) return@combine null
            if (query.isBlank()) return@combine apps
            val needle = query.trim().lowercase()
            apps.filter { it.label.lowercase().contains(needle) || it.packageName.contains(needle) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { _tunnelDraft.value = settings.tunnelDraft.first() }
    }

    // Session commands. Both are idempotent at the service, so a repeated tap
    // coalesces there rather than being guarded here with a flag that could drift.

    fun startTunnel() {
        app.startService(Intent(app, BoreasVpnService::class.java).setAction(BoreasVpnService.ACTION_START))
    }

    fun stopTunnel() {
        app.startService(Intent(app, BoreasVpnService::class.java).setAction(BoreasVpnService.ACTION_STOP))
    }

    fun deliverConsent(outcome: ConsentOutcome) {
        viewModelScope.launch { ConsentBroker.deliver(outcome) }
    }

    // Preferences.

    fun setTheme(choice: ThemeChoice) = viewModelScope.launch { settings.setTheme(choice) }

    fun setProfile(profile: RuleProfile) = updateEngineConfig { it.copy(profile = profile) }

    fun setInspectTls(enabled: Boolean) = updateEngineConfig { it.copy(inspectTls = enabled) }

    fun setUpstream(route: UpstreamRoute) = updateEngineConfig { it.copy(upstream = route) }

    private fun updateEngineConfig(change: (EngineConfig) -> EngineConfig) {
        viewModelScope.launch { settings.setEngineConfig(change(engineConfig.value)) }
    }

    fun setTunnelDraft(draft: TunnelDraft) {
        _tunnelDraft.value = draft
        viewModelScope.launch { settings.setTunnelDraft(draft) }
    }

    fun setAppExcluded(packageName: String, excluded: Boolean) {
        viewModelScope.launch {
            val next = excludedPackages.value.toMutableSet()
            if (excluded) next.add(packageName) else next.remove(packageName)
            settings.setExcludedPackages(next)
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
     * Sorted by label with a locale-aware collator built once, not per comparison.
     */
    fun loadInstalledApps() {
        if (_installedApps.value != null) return
        viewModelScope.launch {
            _installedApps.value = withContext(Dispatchers.IO) {
                val manager = app.packageManager
                val collator = java.text.Collator.getInstance()
                runCatching {
                    manager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { it.packageName != app.packageName }
                        .filter { manager.getLaunchIntentForPackage(it.packageName) != null || it.isUserApp() }
                        .map { InstalledApp(it.packageName, manager.getApplicationLabel(it).toString()) }
                        .sortedWith { a, b -> collator.compare(a.label, b.label) }
                }.getOrDefault(emptyList())
            }
        }
    }

    private fun ApplicationInfo.isUserApp() = flags and ApplicationInfo.FLAG_SYSTEM == 0

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { BoreasViewModel(app) }
        }
    }
}

/** Whether the saved policy differs from the one the running session started with. */
fun VpnLifecycleState.pendingPolicyChange(saved: EngineConfig): Boolean =
    this is VpnLifecycleState.Running && applied != saved

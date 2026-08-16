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

/** Installed app with a precomputed lowercase search key. */
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

/** Owns UI effects; screens receive state and callbacks only. */
class BoreasViewModel(private val app: Application) : ViewModel() {

    private val settings = SettingsRepository(app)

    val sessionState: StateFlow<VpnLifecycleState> = SessionStateBus.state
    val alwaysOn: StateFlow<AlwaysOn> = SessionStateBus.alwaysOn
    val transitions: StateFlow<List<TransitionRecord>> = SessionStateBus.log

    val engineConfig: StateFlow<EngineConfig> =
        settings.engineConfig.stateIn(viewModelScope, SharingStarted.Eagerly, EngineConfig())

    val certificateInstalled: StateFlow<Boolean> =
        settings.certificateInstallCompleted.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val simulationEnabled: StateFlow<Boolean> =
        settings.simulationEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val simulationAvailable: Boolean = BuildConfig.SIMULATION_AVAILABLE

    val excludedPackages: StateFlow<Set<String>> =
        settings.excludedPackages.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Raw draft loads once, then writes asynchronously so typing survives process death. */
    val tunnelDraft: StateFlow<TunnelDraft?>
        field = MutableStateFlow<TunnelDraft?>(null)

    /** Derived from the current draft, never stored separately. */
    val tunnelParse: StateFlow<TunnelParse?> =
        combine(tunnelDraft, excludedPackages) { draft, excluded ->
            draft?.let { TunnelParse.of(it, excluded) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val installedApps: StateFlow<List<InstalledApp>?>
        field = MutableStateFlow<List<InstalledApp>?>(null)

    val appSearch: StateFlow<String>
        field = MutableStateFlow("")

    /** Filters pre-indexed app keys once per query. */
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

    // Service coalesces repeated commands; do not add a second guard here.

    fun startTunnel() {
        app.startService(Intent(app, BoreasVpnService::class.java).setAction(BoreasVpnService.ACTION_START))
    }

    fun stopTunnel() {
        app.startService(Intent(app, BoreasVpnService::class.java).setAction(BoreasVpnService.ACTION_STOP))
    }

    /** Deferred completion is non-suspending. */
    fun deliverConsent(outcome: ConsentOutcome) = ConsentBroker.deliver(outcome)

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

    /** Keeps app search state write-owned by this holder. */
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

    /** One launcher query avoids per-app package-manager IPC; sorting reuses one collator. */
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

fun VpnLifecycleState.pendingPolicyChange(saved: EngineConfig): Boolean =
    this is VpnLifecycleState.Running && applied != saved

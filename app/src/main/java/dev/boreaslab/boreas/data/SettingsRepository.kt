package dev.boreaslab.boreas.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.RuleProfile
import dev.boreaslab.boreas.model.TunnelDraft
import dev.boreaslab.boreas.model.UpstreamRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("boreas")

/** Persists raw drafts and choices across navigation and process death. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val profile = stringPreferencesKey("rule_profile")
        val inspectTls = booleanPreferencesKey("inspect_tls")
        val upstream = stringPreferencesKey("upstream")
        val address = stringPreferencesKey("tunnel_address")
        val mtu = stringPreferencesKey("tunnel_mtu")
        val dns = stringPreferencesKey("tunnel_dns")
        val excluded = stringSetPreferencesKey("excluded_packages")
        val simulation = booleanPreferencesKey("simulation_enabled")
        val certificateInstalled = booleanPreferencesKey("certificate_install_completed")
    }

    val engineConfig: Flow<EngineConfig> = context.dataStore.data.map(::decodeEngineConfig)

    private fun decodeEngineConfig(prefs: Preferences) = EngineConfig(
        profile = prefs[Keys.profile].toEnum(RuleProfile.entries, RuleProfile.Standard),
        inspectTls = prefs[Keys.inspectTls] ?: false,
        upstream = prefs[Keys.upstream].toEnum(UpstreamRoute.entries, UpstreamRoute.Direct),
    )

    val tunnelDraft: Flow<TunnelDraft> = context.dataStore.data.map { prefs ->
        val fallback = TunnelDraft()
        TunnelDraft(
            address = prefs[Keys.address] ?: fallback.address,
            mtu = prefs[Keys.mtu] ?: fallback.mtu,
            dns = prefs[Keys.dns] ?: fallback.dns,
        )
    }

    val excludedPackages: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.excluded] ?: emptySet() }

    val simulationEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.simulation] ?: false }

    /** Records completion only; Android does not expose personal-store contents. */
    val certificateInstallCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.certificateInstalled] ?: false }

    /** Keeps read-modify-write inside DataStore's transaction to avoid stale overwrites. */
    suspend fun updateEngineConfig(transform: (EngineConfig) -> EngineConfig) {
        context.dataStore.edit { prefs ->
            val next = transform(decodeEngineConfig(prefs))
            prefs[Keys.profile] = next.profile.name
            prefs[Keys.inspectTls] = next.inspectTls
            prefs[Keys.upstream] = next.upstream.name
        }
    }

    suspend fun setTunnelDraft(draft: TunnelDraft) {
        context.dataStore.edit { prefs ->
            prefs[Keys.address] = draft.address
            prefs[Keys.mtu] = draft.mtu
            prefs[Keys.dns] = draft.dns
        }
    }

    suspend fun updateExcludedPackages(transform: (Set<String>) -> Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.excluded] = transform(prefs[Keys.excluded] ?: emptySet())
        }
    }

    suspend fun setSimulationEnabled(enabled: Boolean) = put(Keys.simulation, enabled)

    suspend fun setCertificateInstallCompleted(completed: Boolean) =
        put(Keys.certificateInstalled, completed)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}

/** Unknown stored names resolve to the default rather than throwing. */
private fun <T : Enum<T>> String?.toEnum(values: List<T>, fallback: T): T =
    values.firstOrNull { it.name == this } ?: fallback

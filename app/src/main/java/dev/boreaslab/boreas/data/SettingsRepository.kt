package dev.boreaslab.boreas.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.boreaslab.boreas.design.ThemeChoice
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.RuleProfile
import dev.boreaslab.boreas.model.TunnelDraft
import dev.boreaslab.boreas.model.UpstreamRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("boreas")

/**
 * Everything the reader has chosen, persisted.
 *
 * Preferences survive navigation, process death, and failure, which is why the
 * tunnel form stores its raw draft text rather than only a parsed value: a
 * half-typed entry is the reader's work and must not be destroyed by a validation
 * pass or by the process being killed while they are looking at another app.
 *
 * An enum is stored by name and read back through a total lookup, so an unknown or
 * corrupted value falls back to the default instead of throwing.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
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

    val themeChoice: Flow<ThemeChoice> = context.dataStore.data.map { prefs ->
        prefs[Keys.theme].toEnum(ThemeChoice.entries, ThemeChoice.System)
    }

    val engineConfig: Flow<EngineConfig> = context.dataStore.data.map { prefs ->
        EngineConfig(
            profile = prefs[Keys.profile].toEnum(RuleProfile.entries, RuleProfile.Standard),
            inspectTls = prefs[Keys.inspectTls] ?: false,
            upstream = prefs[Keys.upstream].toEnum(UpstreamRoute.entries, UpstreamRoute.Direct),
        )
    }

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

    /**
     * Whether the certificate install flow was completed.
     *
     * Android offers no way to read back which certificates sit in the personal
     * store, so this records that the reader finished the install screen and
     * nothing stronger. The Certificate screen says as much rather than presenting
     * it as a verified fact.
     */
    val certificateInstallCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.certificateInstalled] ?: false }

    suspend fun setTheme(choice: ThemeChoice) = put(Keys.theme, choice.name)

    suspend fun setEngineConfig(config: EngineConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.profile] = config.profile.name
            prefs[Keys.inspectTls] = config.inspectTls
            prefs[Keys.upstream] = config.upstream.name
        }
    }

    suspend fun setTunnelDraft(draft: TunnelDraft) {
        context.dataStore.edit { prefs ->
            prefs[Keys.address] = draft.address
            prefs[Keys.mtu] = draft.mtu
            prefs[Keys.dns] = draft.dns
        }
    }

    suspend fun setExcludedPackages(packages: Set<String>) = put(Keys.excluded, packages)

    suspend fun setSimulationEnabled(enabled: Boolean) = put(Keys.simulation, enabled)

    suspend fun setCertificateInstallCompleted(completed: Boolean) =
        put(Keys.certificateInstalled, completed)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}

/** Total lookup: an unknown stored name resolves to the default rather than throwing. */
private fun <T : Enum<T>> String?.toEnum(values: List<T>, fallback: T): T =
    values.firstOrNull { it.name == this } ?: fallback

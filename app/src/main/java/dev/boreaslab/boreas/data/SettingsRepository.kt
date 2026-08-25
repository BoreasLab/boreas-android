package dev.boreaslab.boreas.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.boreaslab.boreas.model.NatBehavior
import dev.boreaslab.boreas.model.PolicyDraft
import dev.boreaslab.boreas.model.TunnelDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("boreas")

/** Persists raw drafts and choices across navigation and process death. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val filterNames = booleanPreferencesKey("filter_names")
        val resolver = stringPreferencesKey("resolver")
        val rules = stringPreferencesKey("rules")
        val intercept = booleanPreferencesKey("intercept")
        val interceptHosts = stringPreferencesKey("intercept_hosts")
        val rewriteDocuments = booleanPreferencesKey("rewrite_documents")
        val nat = stringPreferencesKey("nat_behavior")
        val address = stringPreferencesKey("tunnel_address")
        val mtu = stringPreferencesKey("tunnel_mtu")
        val dns = stringPreferencesKey("tunnel_dns")
        val excluded = stringSetPreferencesKey("excluded_packages")
        val simulation = booleanPreferencesKey("simulation_enabled")
        val certificateInstalled = booleanPreferencesKey("certificate_install_completed")
    }

    /**
     * The raw policy, as typed.
     *
     * Stored as a draft rather than as a parsed [dev.boreaslab.boreas.model.EngineConfig]
     * on purpose. Half-typed text has to survive process death, and a type whose
     * whole point is to be unconstructible when wrong cannot hold it. Parsing
     * happens at one boundary, and the one that starts the tunnel reads the same
     * draft this does.
     */
    val policyDraft: Flow<PolicyDraft> = context.dataStore.data.map(::decodePolicyDraft)

    private fun decodePolicyDraft(prefs: Preferences): PolicyDraft {
        val fallback = PolicyDraft()
        return PolicyDraft(
            filterNames = prefs[Keys.filterNames] ?: fallback.filterNames,
            resolver = prefs[Keys.resolver] ?: fallback.resolver,
            rules = prefs[Keys.rules] ?: fallback.rules,
            intercept = prefs[Keys.intercept] ?: fallback.intercept,
            interceptHosts = prefs[Keys.interceptHosts] ?: fallback.interceptHosts,
            rewriteDocuments = prefs[Keys.rewriteDocuments] ?: fallback.rewriteDocuments,
            nat = prefs[Keys.nat].toEnum(NatBehavior.entries, fallback.nat),
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

    /** Records completion only; Android does not expose personal-store contents. */
    val certificateInstallCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.certificateInstalled] ?: false }

    /** Keeps read-modify-write inside DataStore's transaction to avoid stale overwrites. */
    suspend fun updatePolicyDraft(transform: (PolicyDraft) -> PolicyDraft) {
        context.dataStore.edit { prefs ->
            val next = transform(decodePolicyDraft(prefs))
            prefs[Keys.filterNames] = next.filterNames
            prefs[Keys.resolver] = next.resolver
            prefs[Keys.rules] = next.rules
            prefs[Keys.intercept] = next.intercept
            prefs[Keys.interceptHosts] = next.interceptHosts
            prefs[Keys.rewriteDocuments] = next.rewriteDocuments
            prefs[Keys.nat] = next.nat.name
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

package dev.boreaslab.boreas.service

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import dev.boreaslab.boreas.BuildConfig
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.core.Establishment
import dev.boreaslab.boreas.core.NativeEngineHost
import dev.boreaslab.boreas.core.VpnBypass
import dev.boreaslab.boreas.core.VpnPlatform
import dev.boreaslab.boreas.data.KeystoreAuthorityStore
import dev.boreaslab.boreas.data.SettingsRepository
import dev.boreaslab.boreas.engine.EngineHost
import dev.boreaslab.boreas.engine.SimulatedEngineHost
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.PolicyParse
import dev.boreaslab.boreas.model.TunnelParse
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns Android VPN consent, interface creation, foreground-service compliance,
 * routes, and lifecycle effects. Packet policy stays native; this service creates
 * and closes the descriptor after the core's `release` callback.
 *
 * `internal` is sufficient because the manifest resolves the class from its JVM
 * name even though [VpnPlatform] is module-local.
 */
internal class BoreasVpnService : VpnService(), VpnPlatform {

    /** Default dispatcher keeps bounded native teardown off the main thread. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var settings: SettingsRepository
    private lateinit var controller: SessionController

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(applicationContext)
        controller = SessionController(
            engineProvider = ::selectEngine,
            consent = AndroidConsentGate(this),
            scope = scope,
            now = System::currentTimeMillis,
        )
        mirrorStateToBus()
    }

    /** Selects the simulator only when this debug build explicitly enables it. */
    private suspend fun selectEngine(): EngineHost {
        if (BuildConfig.SIMULATION_AVAILABLE && settings.simulationEnabled.first()) {
            return SimulatedEngineHost()
        }
        return NativeEngineHost(
            vpn = this,
            authority = KeystoreAuthorityStore(applicationContext),
            clock = System::currentTimeMillis,
        )
    }

    // ---------------------------------------------------------------- platform

    /**
     * Builds the interface from [PlatformConfig]. `setMtu` and
     * `BoreasConfig.mtu` must agree; see api/obligations.md. A null result from
     * `establish()` is a refusal, not an exception.
     */
    override fun establish(config: PlatformConfig): Establishment = try {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(config.mtu.bytes)
            .addAddress(config.address.text, HOST_PREFIX)
            .addRoute(IPV4_ANY, 0)
            // Route IPv6 even without a local IPv6 address: omitting it would let
            // dual-stack traffic bypass the tunnel.
            // See docs/verified-inputs.md; this needs a dual-stack device to confirm.
            .addRoute(IPV6_ANY, 0)
            // The device vtable polls with a bounded timeout and reads only when
            // data is available, so `recv` can answer "ask again".
            .setBlocking(false)
            // This tunnel is not a metered data connection.
            .setMetered(false)

        config.dnsServers.forEach { builder.addDnsServer(it.text) }
        config.excludedPackages.forEach { name ->
            // An uninstalled package no longer has a meaningful exclusion.
            try {
                builder.addDisallowedApplication(name)
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        builder.establish()?.let(Establishment::Established) ?: Establishment.Refused
    } catch (error: IllegalArgumentException) {
        Establishment.Rejected(error)
    } catch (error: IllegalStateException) {
        Establishment.Rejected(error)
    }

    override fun bypass(): VpnBypass = VpnBypass(this)

    // --------------------------------------------------------------- lifecycle

    /** Distinguishes explicit commands from always-on and sticky-restart intents. */
    private sealed interface ServiceRequest {
        data object Start : ServiceRequest
        data object Reconfigure : ServiceRequest
        data object Stop : ServiceRequest
        data object Ignore : ServiceRequest
    }

    private fun parseRequest(intent: Intent?): ServiceRequest = when (intent?.action) {
        ACTION_START -> ServiceRequest.Start
        ACTION_RECONFIGURE -> ServiceRequest.Reconfigure
        ACTION_STOP -> ServiceRequest.Stop
        // Null action is an Android start or sticky restart; honor it only for always-on.
        null -> if (isAlwaysOn) ServiceRequest.Start else ServiceRequest.Ignore
        else -> ServiceRequest.Ignore
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SessionStateBus.publishAlwaysOn(readAlwaysOn())

        when (parseRequest(intent)) {
            ServiceRequest.Start -> scope.launch { startRequested() }
            ServiceRequest.Reconfigure -> scope.launch { reconfigureRequested() }
            ServiceRequest.Stop -> controller.submit(SessionCommand.Stop)
            ServiceRequest.Ignore -> Unit
        }

        // Always-on services restart after process death; ordinary starts must not.
        return if (isAlwaysOn) START_STICKY else START_NOT_STICKY
    }

    private fun readAlwaysOn(): AlwaysOn =
        if (isAlwaysOn) AlwaysOn.On(lockdown = isLockdownEnabled) else AlwaysOn.Off

    /**
     * Both drafts cross their parse boundary here, and nowhere else.
     *
     * A rejection is submitted as a command rather than written to the state
     * directly, so the controller stays the only writer of the lifecycle cell.
     */
    private suspend fun startRequested() {
        val policy = settings.policyDraft.first()
        val draft = settings.tunnelDraft.first()
        val excluded = settings.excludedPackages.first()

        val interfaceParse = TunnelParse.of(draft, excluded)
        val policyParse = PolicyParse.of(policy)

        val command = when {
            interfaceParse !is TunnelParse.Valid ->
                SessionCommand.Reject(Operation.Start, TypedFailure.InterfaceRejected)
            policyParse !is PolicyParse.Valid ->
                SessionCommand.Reject(Operation.Start, TypedFailure.InterfaceRejected)
            else -> SessionCommand.Start(policyParse.config, interfaceParse.config)
        }
        controller.submit(command)
    }

    /**
     * Pushes the stored policy at the running session.
     *
     * Whether it reaches is the controller's decision, made against the core's own
     * rule about what a reload covers. A policy that no longer parses is a
     * rejection rather than a silent no-op.
     */
    private suspend fun reconfigureRequested() {
        val command = when (val parse = PolicyParse.of(settings.policyDraft.first())) {
            is PolicyParse.Valid -> SessionCommand.Reconfigure(parse.config)
            is PolicyParse.Invalid ->
                SessionCommand.Reject(Operation.Reconfigure, TypedFailure.InterfaceRejected)
        }
        controller.submit(command)
    }

    private fun mirrorStateToBus() {
        scope.launch {
            var previous: VpnLifecycleState? = null
            controller.state.collect { state ->
                val last = previous
                val statusOnly = last is VpnLifecycleState.Running &&
                    state is VpnLifecycleState.Running &&
                    last.session == state.session

                if (statusOnly) {
                    SessionStateBus.publishStatusOnly(state)
                } else {
                    SessionStateBus.publish(state, System.currentTimeMillis())
                    SessionStateBus.publishAlwaysOn(readAlwaysOn())
                    applyForeground(state)
                }
                previous = state
            }
        }
        scope.launch {
            controller.resolutions.collect(SessionStateBus::publishResolutions)
        }
    }

    /** Applies the notification decision carried by state. */
    private fun applyForeground(state: VpnLifecycleState) {
        val notifications = getSystemService(NotificationManager::class.java)
        when (val intent = SessionNotifications.forState(this, state)) {
            is ForegroundIntent.Promote ->
                startForeground(SessionNotifications.NOTIFICATION_ID, intent.notification)

            is ForegroundIntent.Post ->
                notifications.notify(SessionNotifications.NOTIFICATION_ID, intent.notification)

            ForegroundIntent.Dismiss -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                // stopForeground cannot remove notifications posted with notify().
                notifications.cancel(SessionNotifications.NOTIFICATION_ID)
                // Always-on owns the service lifetime.
                if (!isAlwaysOn) stopSelf()
            }
        }
    }

    /** VPN slot revocation is an ordinary stop, not a service failure. */
    override fun onRevoke() {
        controller.submit(SessionCommand.Stop)
        super.onRevoke()
    }

    /**
     * Tears the session down before cancelling the scope. The timeout prevents a
     * non-terminating native shutdown from turning service teardown into an ANR.
     */
    override fun onDestroy() {
        runBlocking { withTimeoutOrNull(TEARDOWN_TIMEOUT_MS) { controller.shutdown() } }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        // Derive actions from application ID so package renames cannot leave stale filters.
        const val ACTION_START = BuildConfig.APPLICATION_ID + ".START"
        const val ACTION_STOP = BuildConfig.APPLICATION_ID + ".STOP"
        const val ACTION_RECONFIGURE = BuildConfig.APPLICATION_ID + ".RECONFIGURE"

        /** One host address on the tunnel interface. */
        private const val HOST_PREFIX = 32
        private const val IPV4_ANY = "0.0.0.0"
        private const val IPV6_ANY = "::"

        /** Bounds service teardown before Android can report an ANR. */
        private const val TEARDOWN_TIMEOUT_MS = 3_000L
    }
}

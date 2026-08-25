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
 * routes, and lifecycle effects.
 *
 * It is also the platform seam the engine reaches back through, and both halves of
 * that are here for the same reason: `VpnService.Builder` appears in exactly one
 * place in this program, and `protect` is a method on this object.
 *
 * Packet policy stays native. The descriptor is created here, handed to the device
 * vtable once, and closed here after the core's `release` callback has run.
 *
 * `internal` because [VpnPlatform] is: the seam is an implementation detail of this
 * module, and widening it to publish a service nothing outside the module names
 * would be the wrong half of that pair to move. Kotlin's `internal` is public in
 * the class file, so the manifest still resolves it.
 */
internal class BoreasVpnService : VpnService(), VpnPlatform {

    /**
     * Deliberately not the main dispatcher.
     *
     * Nothing this service does needs it: state reaches the UI through flows, and
     * `startForeground`, the notification manager, and `VpnService.Builder` are all
     * callable from any thread. Keeping the loop off the main thread is what lets
     * [onDestroy] wait a bounded moment for the native teardown without the wait
     * and the work it is waiting for needing the same thread.
     */
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

    /** The linked engine, unless this debug build was explicitly asked to simulate. */
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
     * Builds the interface, once, from one trusted configuration value.
     *
     * `setMtu` is called with the same number that reaches `BoreasConfig.mtu`,
     * because both read [PlatformConfig.mtu]. api/obligations.md names disagreement
     * here as one of the two silent mistakes: the tunnel works and spends its life
     * answering Packet Too Big to senders that never converge, and `paths_reported`
     * is the only symptom.
     *
     * `establish()` returning null is a documented path, not a theoretical one, so
     * it is a variant rather than an exception.
     */
    override fun establish(config: PlatformConfig): Establishment = try {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(config.mtu.bytes)
            .addAddress(config.address.text, HOST_PREFIX)
            .addRoute(IPV4_ANY, 0)
            // IPv6 is routed in even though this app configures no IPv6 address of
            // its own. Leaving it out is the fail-open choice: on a dual-stack
            // network every IPv6 flow would leave beside the tunnel, unfiltered,
            // while the interface reported itself up. Routing it in is fail-closed.
            // See docs/verified-inputs.md; this needs a dual-stack device to confirm.
            .addRoute(IPV6_ANY, 0)
            // Non-blocking, which is also the platform default. The device vtable
            // polls with a bounded timeout and reads only when the poll says there
            // is something, which is what lets `recv` answer "ask again" instead of
            // parking inside a core callback.
            .setBlocking(false)
            // The tunnel is not itself a data plan, so it does not claim to be one.
            .setMetered(false)

        config.dnsServers.forEach { builder.addDnsServer(it.text) }
        config.excludedPackages.forEach { name ->
            // An excluded app that has since been uninstalled must not cost the user
            // their tunnel. The exclusion is simply no longer meaningful.
            try {
                builder.addDisallowedApplication(name)
            } catch (_: PackageManager.NameNotFoundException) {
                // Nothing to do: the exclusion is simply no longer meaningful.
            }
        }

        builder.establish()?.let(Establishment::Established) ?: Establishment.Refused
    } catch (error: IllegalArgumentException) {
        Establishment.Rejected(error)
    } catch (error: IllegalStateException) {
        Establishment.Rejected(error)
    }

    /** A bypass over this service. The core releases it exactly once, however start ends. */
    override fun bypass(): VpnBypass = VpnBypass(this)

    // --------------------------------------------------------------- lifecycle

    /** Distinguishes explicit commands from Android always-on and sticky-restart intents. */
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
        // Null action means Android start or sticky restart; honor it only when always-on.
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

        // Always-on services restart after process death; ordinary starts must not reappear.
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

    /** Publishes lifecycle transitions and counter updates without logging every tick. */
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

    /** Applies the state-carried notification decision without re-deriving eligibility. */
    private fun applyForeground(state: VpnLifecycleState) {
        val notifications = getSystemService(NotificationManager::class.java)
        when (val intent = SessionNotifications.forState(this, state)) {
            is ForegroundIntent.Promote ->
                startForeground(SessionNotifications.NOTIFICATION_ID, intent.notification)

            is ForegroundIntent.Post ->
                notifications.notify(SessionNotifications.NOTIFICATION_ID, intent.notification)

            ForegroundIntent.Dismiss -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                // stopForeground cannot remove notify()'d notifications; cancel both paths.
                notifications.cancel(SessionNotifications.NOTIFICATION_ID)
                // Always-on owns service lifetime; ordinary sessions may stop themselves.
                if (!isAlwaysOn) stopSelf()
            }
        }
    }

    /** VPN slot was revoked; report a stop because the service did not fail. */
    override fun onRevoke() {
        controller.submit(SessionCommand.Stop)
        super.onRevoke()
    }

    /**
     * Tears the session down before letting the scope go.
     *
     * Bounded and best effort. The handle is native and the reader is a real
     * thread, so neither ends because a coroutine scope was cancelled; freeing
     * them is worth a moment on the way out. The bound is what keeps a core that
     * will not stop from turning a service teardown into an ANR, and the process
     * is usually going with us anyway.
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

        /** The tunnel address names one host, so the interface carries one address. */
        private const val HOST_PREFIX = 32
        private const val IPV4_ANY = "0.0.0.0"
        private const val IPV6_ANY = "::"

        /** Long enough for an ordered shutdown, short enough not to be an ANR. */
        private const val TEARDOWN_TIMEOUT_MS = 3_000L
    }
}

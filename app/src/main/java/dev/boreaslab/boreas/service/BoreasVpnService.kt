package dev.boreaslab.boreas.service

import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import dev.boreaslab.boreas.BuildConfig
import dev.boreaslab.boreas.data.SettingsRepository
import dev.boreaslab.boreas.engine.EngineHost
import dev.boreaslab.boreas.engine.SimulatedEngineHost
import dev.boreaslab.boreas.engine.UnlinkedEngineHost
import dev.boreaslab.boreas.model.Operation
import dev.boreaslab.boreas.model.TunnelParse
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The only owner of Android VPN consent, interface creation, foreground-service
 * compliance, routes, and lifecycle callbacks.
 *
 * A thin effect interpreter, as docs/platform-integration.md requires. It holds no
 * policy, parses nothing about packets, and never sees one. It turns typed
 * commands into Android effects and publishes typed state back.
 *
 * What this class deliberately does not do yet, and why:
 *
 *  - It does not call VpnService.Builder.establish(). Building the interface is A3
 *    work, gated on a real-device loopback and DNS fixture.
 *  - It does not call ParcelFileDescriptor.detachFd(). A descriptor has exactly one
 *    owner at every instant, and detaching one with no native owner on the far side
 *    would create a descriptor nothing is responsible for closing.
 *  - It does not implement the protect(fd) callback. That seam belongs with the
 *    native bridge that needs it.
 */
class BoreasVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var settings: SettingsRepository
    private lateinit var controller: SessionController

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(applicationContext)
        controller = SessionController(
            engineProvider = ::selectEngine,
            consent = AndroidConsentGate(this),
            scope = scope,
        )
        mirrorStateToBus()
    }

    /**
     * The unlinked host is the only one a release build can construct.
     *
     * The simulated host exists so this surface can be reviewed before A2 links the
     * real engine. The BuildConfig constant keeps it out of release, and even in
     * debug it stays off until the reader turns it on under Diagnostics.
     *
     * Resolved inside the start coroutine, so reading the preference never blocks
     * the main thread.
     */
    private suspend fun selectEngine(): EngineHost {
        if (!BuildConfig.SIMULATION_AVAILABLE) return UnlinkedEngineHost
        return if (settings.simulationEnabled.first()) SimulatedEngineHost() else UnlinkedEngineHost
    }

    /**
     * What an incoming Intent means. Parsed once, at the boundary.
     *
     * `onStartCommand` receives Intents from three sources with different
     * authority, and telling them apart is a correctness matter rather than
     * tidiness: Android starts an always-on VPN by calling `startService()` with
     * no action of ours, and a sticky restart redelivers a null Intent entirely.
     * Matching only on our own actions silently ignores both, which is exactly how
     * an always-on VPN ends up never starting.
     */
    private sealed interface ServiceRequest {
        data object Start : ServiceRequest
        data object Stop : ServiceRequest
        data object Ignore : ServiceRequest
    }

    private fun parseRequest(intent: Intent?): ServiceRequest = when (intent?.action) {
        ACTION_START -> ServiceRequest.Start
        ACTION_STOP -> ServiceRequest.Stop
        // No action of ours: Android starting an always-on VPN, or a sticky
        // restart of one. Honored only while always-on is actually on, so a stray
        // Intent can never raise a tunnel nobody asked for.
        null -> if (isAlwaysOn) ServiceRequest.Start else ServiceRequest.Ignore
        else -> ServiceRequest.Ignore
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SessionStateBus.publishAlwaysOn(readAlwaysOn())

        when (parseRequest(intent)) {
            ServiceRequest.Start -> scope.launch { startRequested() }
            ServiceRequest.Stop -> controller.submit(SessionCommand.Stop)
            ServiceRequest.Ignore -> Unit
        }

        // Under always-on, Android's contract is that this tunnel stays up, so a
        // process death should bring it back. Otherwise it must not: a tunnel that
        // silently reappears is one nobody asked for.
        return if (isAlwaysOn) START_STICKY else START_NOT_STICKY
    }

    /**
     * Reads always-on state from the running service.
     *
     * Both queries arrive at API 29, which is this app's minimum, so there is no
     * version branch here and no unknown case to represent.
     */
    private fun readAlwaysOn(): AlwaysOn =
        if (isAlwaysOn) AlwaysOn.On(lockdown = isLockdownEnabled) else AlwaysOn.Off

    private suspend fun startRequested() {
        val engineConfig = settings.engineConfig.first()
        val draft = settings.tunnelDraft.first()
        val excluded = settings.excludedPackages.first()

        // Parsed once, at the untrusted entry. A session starts only with a value
        // already refined into an immutable trusted type. A rejection travels back
        // through the controller rather than being written to the state stream
        // from here, so the observable state keeps exactly one writer.
        val command = when (val parse = TunnelParse.of(draft, excluded)) {
            is TunnelParse.Valid -> SessionCommand.Start(engineConfig, parse.config)
            is TunnelParse.Invalid ->
                SessionCommand.Reject(Operation.Start, TypedFailure.InterfaceRejected)
        }
        controller.submit(command)
    }

    /**
     * Publishes controller state to the UI and drives the foreground notification.
     *
     * A new counter snapshot for the session already running is not a transition,
     * so it updates the state without adding a diagnostics entry every second.
     */
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
    }

    private fun applyForeground(state: VpnLifecycleState) {
        when (val intent = SessionNotifications.forState(this, state)) {
            is ForegroundIntent.Show ->
                if (state.isTransitional || state is VpnLifecycleState.Running) {
                    startForeground(SessionNotifications.NOTIFICATION_ID, intent.notification)
                } else {
                    getSystemService(NotificationManager::class.java)
                        .notify(SessionNotifications.NOTIFICATION_ID, intent.notification)
                }

            ForegroundIntent.Dismiss -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                // Under always-on, Android owns this service's lifetime and will
                // start it again; stopping ourselves would only fight it.
                if (!isAlwaysOn) stopSelf()
            }
        }
    }

    /**
     * Android revoked the interface, usually because another VPN took the slot.
     *
     * Treated as a stop rather than a failure: nothing went wrong here.
     */
    override fun onRevoke() {
        controller.submit(SessionCommand.Stop)
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        // Derived from the application id rather than written out, so the two
        // cannot drift apart and a rename cannot leave a dead intent filter.
        const val ACTION_START = BuildConfig.APPLICATION_ID + ".START"
        const val ACTION_STOP = BuildConfig.APPLICATION_ID + ".STOP"
    }
}

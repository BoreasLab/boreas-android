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
 * Owns Android VPN consent, interface, foreground-service, route, and lifecycle effects.
 *
 * Packet policy and descriptor handoff remain native. TUN creation, `detachFd()`, and
 * `protect(fd)` wait for the native bridge and its descriptor owner.
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

    /** Release uses the unlinked host; simulation is a debug-only explicit opt-in. */
    private suspend fun selectEngine(): EngineHost {
        if (!BuildConfig.SIMULATION_AVAILABLE) return UnlinkedEngineHost
        return if (settings.simulationEnabled.first()) SimulatedEngineHost() else UnlinkedEngineHost
    }

    /** Distinguishes explicit commands from Android always-on and sticky-restart intents. */
    private sealed interface ServiceRequest {
        data object Start : ServiceRequest
        data object Stop : ServiceRequest
        data object Ignore : ServiceRequest
    }

    private fun parseRequest(intent: Intent?): ServiceRequest = when (intent?.action) {
        ACTION_START -> ServiceRequest.Start
        ACTION_STOP -> ServiceRequest.Stop
        // Null action means Android start or sticky restart; honor it only when always-on.
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

        // Always-on services restart after process death; ordinary starts must not reappear.
        return if (isAlwaysOn) START_STICKY else START_NOT_STICKY
    }

    private fun readAlwaysOn(): AlwaysOn =
        if (isAlwaysOn) AlwaysOn.On(lockdown = isLockdownEnabled) else AlwaysOn.Off

    private suspend fun startRequested() {
        val engineConfig = settings.engineConfig.first()
        val draft = settings.tunnelDraft.first()
        val excluded = settings.excludedPackages.first()

        // Parse at the untrusted boundary; return rejection through the controller so it
        // remains the sole state writer.
        val command = when (val parse = TunnelParse.of(draft, excluded)) {
            is TunnelParse.Valid -> SessionCommand.Start(engineConfig, parse.config)
            is TunnelParse.Invalid ->
                SessionCommand.Reject(Operation.Start, TypedFailure.InterfaceRejected)
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        // Derive actions from application ID so package renames cannot leave stale filters.
        const val ACTION_START = BuildConfig.APPLICATION_ID + ".START"
        const val ACTION_STOP = BuildConfig.APPLICATION_ID + ".STOP"
    }
}

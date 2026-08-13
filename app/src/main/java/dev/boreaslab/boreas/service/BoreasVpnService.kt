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
import dev.boreaslab.boreas.model.PlatformConfig
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
 * policy, parses nothing, and never sees a packet. It turns typed commands into
 * Android effects and publishes typed state back.
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
 *
 * Each has a matching gate in docs/implementation-plan.md. Adding any of them here
 * ahead of the core would be the Android-specific datapath the invariants forbid.
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
                    applyForeground(state)
                }
                previous = state
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> scope.launch { startRequested() }
            ACTION_STOP -> controller.submit(SessionCommand.Stop)
        }
        // Do not recreate with a stale intent. A restart must come from the reader,
        // because a tunnel that silently reappears is one nobody asked for.
        return START_NOT_STICKY
    }

    private suspend fun startRequested() {
        val engineConfig = settings.engineConfig.first()
        val draft = settings.tunnelDraft.first()
        val excluded = settings.excludedPackages.first()

        // Parsed once, at the untrusted entry. The session starts only with a value
        // already validated into an immutable trusted type.
        val platform: PlatformConfig? = PlatformConfig.parse(draft, excluded).config
        if (platform == null) {
            SessionStateBus.publish(
                VpnLifecycleState.Failed(Operation.Start, TypedFailure.InterfaceRejected),
                System.currentTimeMillis(),
            )
            return
        }
        controller.submit(SessionCommand.Start(engineConfig, platform))
    }

    private fun applyForeground(state: VpnLifecycleState) {
        val notification = SessionNotifications.build(this, state)
        if (notification == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (state.isTransitional || state is VpnLifecycleState.Running) {
            startForeground(SessionNotifications.NOTIFICATION_ID, notification)
        } else {
            getSystemService(NotificationManager::class.java)
                .notify(SessionNotifications.NOTIFICATION_ID, notification)
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
        const val ACTION_START = "dev.boreaslab.boreas.START"
        const val ACTION_STOP = "dev.boreaslab.boreas.STOP"
    }
}

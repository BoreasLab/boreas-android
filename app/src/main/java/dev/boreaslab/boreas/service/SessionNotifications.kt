package dev.boreaslab.boreas.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.boreaslab.boreas.MainActivity
import dev.boreaslab.boreas.R

/** What the foreground notification should be doing. A closed set. */
sealed interface ForegroundIntent {
    data class Show(val notification: Notification) : ForegroundIntent
    data object Dismiss : ForegroundIntent
}

/**
 * The foreground notification.
 *
 * Android requires a VPN to be a foreground service, so this is not a marketing
 * surface: it says which state the tunnel is in and offers the one action that
 * matters from outside the app. When the session is simulated it says so, because
 * a notification claiming a tunnel is running when no packet is being carried
 * would be the most damaging thing this build could tell someone.
 */
object SessionNotifications {

    const val CHANNEL_ID = "session"
    const val NOTIFICATION_ID = 1

    fun forState(context: Context, state: VpnLifecycleState): ForegroundIntent {
        // A guard separates the simulated session from the real one, so the two
        // labels sit at the same level as every other state rather than one being
        // reached through a conditional inside the other.
        val (title, showStop) = when (state) {
            VpnLifecycleState.Starting,
            VpnLifecycleState.AwaitingConsent,
            -> context.getString(R.string.notification_starting) to false
            is VpnLifecycleState.Stopping -> context.getString(R.string.notification_stopping) to false
            is VpnLifecycleState.Running if state.status.simulated ->
                context.getString(R.string.notification_running_simulated) to true
            is VpnLifecycleState.Running -> context.getString(R.string.notification_running) to true
            VpnLifecycleState.Stopped, is VpnLifecycleState.Failed -> return ForegroundIntent.Dismiss
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)

        if (showStop) {
            val stop = PendingIntent.getService(
                context,
                1,
                Intent(context, BoreasVpnService::class.java).setAction(BoreasVpnService.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.action_stop),
                    stop,
                ).build(),
            )
        }

        return ForegroundIntent.Show(builder.build())
    }
}

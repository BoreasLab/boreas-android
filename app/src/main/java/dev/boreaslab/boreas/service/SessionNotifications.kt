package dev.boreaslab.boreas.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.boreaslab.boreas.MainActivity
import dev.boreaslab.boreas.R

/** Notification action; [Promote] is reserved for states eligible for foreground service. */
sealed interface ForegroundIntent {

    data class Promote(val notification: Notification) : ForegroundIntent

    data class Post(val notification: Notification) : ForegroundIntent

    data object Dismiss : ForegroundIntent
}

/** Builds state-specific notifications; simulated sessions are labeled as such. */
object SessionNotifications {

    const val CHANNEL_ID = "session"
    const val NOTIFICATION_ID = 1

    fun forState(context: Context, state: VpnLifecycleState): ForegroundIntent {
        val (title, promote, showStop) = when (state) {
            // Consent dialog is app-foreground and not VPN-eligible; do not promote before grant.
            VpnLifecycleState.AwaitingConsent ->
                Triple(context.getString(R.string.notification_starting), false, false)
            VpnLifecycleState.Starting ->
                Triple(context.getString(R.string.notification_starting), true, false)
            is VpnLifecycleState.Stopping ->
                Triple(context.getString(R.string.notification_stopping), true, false)
            is VpnLifecycleState.Running if state.status.simulated ->
                Triple(context.getString(R.string.notification_running_simulated), true, true)
            is VpnLifecycleState.Running ->
                Triple(context.getString(R.string.notification_running), true, true)
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

        val notification = builder.build()
        return if (promote) {
            ForegroundIntent.Promote(notification)
        } else {
            ForegroundIntent.Post(notification)
        }
    }
}

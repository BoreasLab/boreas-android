package dev.boreaslab.boreas.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.boreaslab.boreas.MainActivity
import dev.boreaslab.boreas.R

/**
 * What the service should do about its notification. A closed set.
 *
 * [Promote] and [Post] are separated because promoting is not free at API 34 and
 * above: `startForeground` on a service typed `systemExempted` succeeds only while
 * the app satisfies one of that type's eligibility criteria, and the one this app
 * relies on is being the configured VPN. Deciding that here, where the states are
 * known, keeps the caller from re-deriving it from a lifecycle predicate that was
 * never about permissions.
 */
sealed interface ForegroundIntent {

    /** The session owns, or is about to own, a tunnel. Foreground is warranted. */
    data class Promote(val notification: Notification) : ForegroundIntent

    /** Something to say, but no tunnel yet, so no promotion to go with it. */
    data class Post(val notification: Notification) : ForegroundIntent

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
        // Each state names its own copy, whether it warrants foreground, and whether
        // stopping is offered. A guard separates the simulated session from the real
        // one, so the two labels sit at the same level as every other state rather
        // than one being reached through a conditional inside the other.
        val (title, promote, showStop) = when (state) {
            // Consent has not been given, so the app is not the configured VPN and
            // does not yet satisfy any systemExempted criterion. Promoting here is
            // what would raise SecurityException, and it buys nothing: the reader is
            // looking at the system's own permission dialog. The service was started
            // while the app was in the foreground, so no promotion deadline is
            // running against it either.
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

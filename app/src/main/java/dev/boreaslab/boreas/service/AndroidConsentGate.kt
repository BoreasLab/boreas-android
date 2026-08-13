package dev.boreaslab.boreas.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries a consent request from the service to whichever Activity is on screen,
 * and the answer back.
 *
 * Consent needs an Activity result and the service has no window, so exactly one
 * object bridges them. The request flow has no replay: a request nobody was on
 * screen to answer is not queued for later, because a permission dialog appearing
 * minutes afterwards with no context is worse than none at all.
 */
object ConsentBroker {

    private val _requests = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val requests: SharedFlow<Intent> = _requests.asSharedFlow()

    private val outcomes = Channel<ConsentOutcome>(Channel.RENDEZVOUS)

    /** Called by the gate. Returns false when no Activity was listening. */
    internal fun offer(intent: Intent): Boolean = _requests.tryEmit(intent)

    internal suspend fun awaitOutcome(): ConsentOutcome = outcomes.receive()

    /** Called by the Activity once the consent Activity result arrives. */
    suspend fun deliver(outcome: ConsentOutcome) = outcomes.send(outcome)
}

/**
 * The real gate. The one place VpnService.prepare is called.
 *
 * prepare returns null when permission is already granted, an Intent when the
 * dialog must be shown, and can be unavailable when device policy or another
 * always-on VPN holds the slot.
 */
class AndroidConsentGate(private val context: Context) : ConsentGate {

    override suspend fun request(): ConsentOutcome {
        val intent = try {
            VpnService.prepare(context)
        } catch (_: NullPointerException) {
            // Some vendor images throw rather than return when VPN is unavailable.
            return ConsentOutcome.Unavailable
        } ?: return ConsentOutcome.Granted

        if (!ConsentBroker.offer(intent)) return ConsentOutcome.Unavailable
        return ConsentBroker.awaitOutcome()
    }
}

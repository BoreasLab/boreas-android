package dev.boreaslab.boreas.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
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
 *
 * The answer travels through a one-shot slot rather than a channel. A rendezvous
 * channel would make [deliver] suspend until someone received, so an answer
 * arriving after its start was cancelled would park a coroutine that nothing ever
 * wakes. Completing a [CompletableDeferred] cannot suspend and cannot leak: an
 * answer with no waiter is simply dropped.
 */
object ConsentBroker {

    private val _requests = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val requests: SharedFlow<Intent> = _requests.asSharedFlow()

    /** At most one consent request is outstanding; a newer one supersedes it. */
    private val pending = AtomicReference<CompletableDeferred<ConsentOutcome>?>(null)

    /**
     * Publishes the request and awaits its answer.
     *
     * Returns [ConsentOutcome.Unavailable] rather than waiting when no Activity is
     * listening, so a start begun with no UI on screen fails fast instead of
     * hanging until something happens to appear.
     */
    internal suspend fun request(intent: Intent): ConsentOutcome {
        val slot = CompletableDeferred<ConsentOutcome>()
        pending.getAndSet(slot)?.complete(ConsentOutcome.Unavailable)

        if (!_requests.tryEmit(intent)) {
            pending.compareAndSet(slot, null)
            return ConsentOutcome.Unavailable
        }
        return try {
            slot.await()
        } finally {
            pending.compareAndSet(slot, null)
        }
    }

    /** Called by the Activity once the consent Activity result arrives. */
    fun deliver(outcome: ConsentOutcome) {
        pending.getAndSet(null)?.complete(outcome)
    }
}

/**
 * The real gate. The one place VpnService.prepare is called.
 *
 * prepare returns null when permission is already granted, an Intent when the
 * dialog must be shown, and can be unavailable when device policy or another
 * always-on VPN holds the slot. Under always-on it returns null, because enabling
 * always-on in system settings is itself the grant.
 */
class AndroidConsentGate(private val context: Context) : ConsentGate {

    override suspend fun request(): ConsentOutcome {
        val intent = try {
            VpnService.prepare(context)
        } catch (_: NullPointerException) {
            // Some vendor images throw rather than return when VPN is unavailable.
            return ConsentOutcome.Unavailable
        } ?: return ConsentOutcome.Granted

        return ConsentBroker.request(intent)
    }
}

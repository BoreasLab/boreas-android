package dev.boreaslab.boreas.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Bridges service consent to the foreground Activity without replaying stale requests. */
object ConsentBroker {

    val requests: SharedFlow<Intent>
        field = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    /** At most one consent request is outstanding; a newer one supersedes it. */
    private val pending = AtomicReference<CompletableDeferred<ConsentOutcome>?>(null)

    /** Fails fast when no Activity is listening instead of waiting indefinitely. */
    internal suspend fun request(intent: Intent): ConsentOutcome {
        val slot = CompletableDeferred<ConsentOutcome>()
        pending.getAndSet(slot)?.complete(ConsentOutcome.Unavailable)

        if (!requests.tryEmit(intent)) {
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

/** Sole [VpnService.prepare] call; null means granted, an Intent means prompt. */
class AndroidConsentGate(private val context: Context) : ConsentGate {

    override suspend fun request(): ConsentOutcome {
        val intent = try {
            VpnService.prepare(context)
        } catch (_: NullPointerException) {
            // Some vendor images throw instead of returning when VPN is unavailable.
            return ConsentOutcome.Unavailable
        } ?: return ConsentOutcome.Granted

        return ConsentBroker.request(intent)
    }
}

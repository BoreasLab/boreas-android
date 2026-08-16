package dev.boreaslab.boreas.model

/** Opaque identity of one engine session. */
@JvmInline
public value class SessionId(public val value: String)

public enum class UpstreamRoute { Direct, Proxy }

/**
 * Immutable counters and status; [simulated] prevents generated values being read
 * as measured traffic.
 */
public data class SessionStatus(
    val startedAtMillis: Long,
    val flowsActive: Long,
    val flowsAccepted: Long,
    val flowsDenied: Long,
    val bytesIn: Long,
    val bytesOut: Long,
    val socketsProtected: Long,
    val upstream: UpstreamRoute,
    val simulated: Boolean,
) {
    public companion object {
        public fun initial(
            startedAtMillis: Long,
            upstream: UpstreamRoute,
            simulated: Boolean,
        ): SessionStatus =
            SessionStatus(
                startedAtMillis = startedAtMillis,
                flowsActive = 0,
                flowsAccepted = 0,
                flowsDenied = 0,
                bytesIn = 0,
                bytesOut = 0,
                socketsProtected = 0,
                upstream = upstream,
                simulated = simulated,
            )
    }
}

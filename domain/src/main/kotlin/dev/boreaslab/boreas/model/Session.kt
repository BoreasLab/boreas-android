package dev.boreaslab.boreas.model

/** Identity of one engine session. Opaque to the Android shell. */
@JvmInline
value class SessionId(val value: String)

/** Where the engine sends upstream traffic. Part of EngineConfig, shown as status. */
enum class UpstreamRoute { Direct, Proxy }

/**
 * An immutable status snapshot.
 *
 * The core contract says a snapshot returns "immutable status and bounded counters"
 * and "never returns packet payloads". Nothing here is a packet, a hostname, or a
 * flow record; these are counts and one enum.
 *
 * [simulated] is true when the values were generated in the app rather than
 * reported by an engine. Every screen that shows a counter must show that marker
 * too, so a generated number can never be read as a measured one.
 */
data class SessionStatus(
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
    companion object {
        fun initial(startedAtMillis: Long, upstream: UpstreamRoute, simulated: Boolean) =
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

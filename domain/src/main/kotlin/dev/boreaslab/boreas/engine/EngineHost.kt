package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.CoreEvent
import dev.boreaslab.boreas.model.CoreStatus
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.flow.Flow

/**
 * One running tunnel, as the shell sees it.
 *
 * This is the shape of the C ABI's five verbs, not a layer invented over them:
 * start, read events, reload, read the authority, stop. Nothing else is required
 * and nothing else is supported. Packets never cross this boundary; the descriptor
 * does, once, inside the implementation.
 *
 * Every method here blocks in C, so every one of them suspends here, and the
 * implementation is responsible for choosing the dispatcher. In particular
 * [events] parks indefinitely: a healthy idle tunnel says nothing, for hours.
 */
public interface EngineHost {

    /** Checked before consent so an unavailable engine never prompts. */
    public val isAvailable: Boolean

    /**
     * Whether what this host reports is generated rather than measured.
     *
     * Declared rather than inferred from the implementing type: `is
     * SimulatedEngineHost` would be a test on an open hierarchy, which is not
     * totality, and a host added later would answer it silently and wrongly.
     */
    public val simulated: Boolean

    public suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart

    /**
     * Every event until the tunnel stops, then completion.
     *
     * One collector only. The ABI allows one reader at a time and queues a second
     * behind the first, which would silently split the stream between them.
     */
    public fun events(session: SessionId): Flow<CoreEvent>

    /**
     * Replaces the rules in force, without restarting or dropping a connection.
     *
     * A whole list set, never a delta: a rebuild publishes one index in a single
     * write, so every query is decided against exactly one version. The resulting
     * `Reloaded` also arrives on [events], which is where the UI should read it,
     * because a reload triggered elsewhere arrives there too.
     */
    public suspend fun reload(session: SessionId, lists: List<String>): CoreStatus

    /** The authority's material, or absent for a tunnel that does not intercept. */
    public suspend fun authority(session: SessionId): AuthorityRead

    public suspend fun stop(session: SessionId, reason: StopReason)
}

/** The result of asking the engine to start. A closed set. */
public sealed interface EngineStart {
    public data class Started(val session: SessionId) : EngineStart
    public data class Refused(val failure: TypedFailure) : EngineStart
}

/**
 * The one thing worth persisting, and only if the tunnel intercepts.
 *
 * Both halves or neither: supplying one is a configuration error, and two halves
 * of different authorities is a state nothing downstream can detect, because every
 * parse succeeds and the session then mints leaves the installed root cannot vouch
 * for. Keeping them in one value is what stops this program writing one without
 * the other.
 */
public class CaMaterial(
    /** Public, DER. Goes to the platform's trust installer. */
    public val certificate: ByteArray,
    /** Secret. Opaque and self-describing; never looked inside. */
    public val keys: ByteArray,
) {
    init {
        require(certificate.isNotEmpty() && keys.isNotEmpty()) {
            "an authority is two non-empty halves or it is absent"
        }
    }
}

/** Reading the authority out. Absence is an answer, not a failure. */
public sealed interface AuthorityRead {
    public data class Present(val material: CaMaterial) : AuthorityRead
    /** This tunnel does not intercept, so there is no authority to keep. */
    public data object None : AuthorityRead
    public data class Failed(val status: CoreStatus) : AuthorityRead
}

/** Why a session is being stopped. The contract requires a typed reason. */
public enum class StopReason { UserRequested, ConfigurationChanged, NetworkLost, ServiceDestroyed }

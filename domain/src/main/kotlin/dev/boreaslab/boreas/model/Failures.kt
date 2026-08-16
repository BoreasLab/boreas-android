package dev.boreaslab.boreas.model

public enum class Operation { Start, Stop, Reconfigure, NetworkChange }

/** Closed, typed failures mapped to user-facing recovery copy. */
public sealed interface TypedFailure {

    /** Shared engine is absent from this Kotlin-only shell. */
    public data object EngineUnavailable : TypedFailure

    public data object ConsentDenied : TypedFailure

    /** VpnService.prepare could not provide consent. */
    public data object ConsentUnavailable : TypedFailure

    /** VpnService.protect refused a socket; engine fails closed rather than loop. */
    public data object BypassDenied : TypedFailure

    /** Android rejected the interface configuration built from PlatformConfig. */
    public data object InterfaceRejected : TypedFailure

    /** A configuration change cannot be applied to the running session. */
    public data object RestartRequired : TypedFailure
}

/** Whether the user can act on a failure from the screen that shows it. */
public val TypedFailure.isRecoverable: Boolean
    get() = when (this) {
        TypedFailure.EngineUnavailable -> false
        TypedFailure.ConsentUnavailable -> false
        TypedFailure.ConsentDenied -> true
        TypedFailure.BypassDenied -> true
        TypedFailure.InterfaceRejected -> true
        TypedFailure.RestartRequired -> true
    }

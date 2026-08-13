package dev.boreaslab.boreas.model

/**
 * The operations a failure can be attached to.
 *
 * The service state carries the operation separately from the reason, because
 * "bypass denied while starting" and "bypass denied while following a network
 * change" need different copy and offer different next steps.
 */
public enum class Operation { Start, Stop, Reconfigure, NetworkChange }

/**
 * Every failure this surface can show.
 *
 * A closed set: the screens eliminate it exhaustively, so adding a reason fails
 * the build at every site that must present it rather than rendering a blank
 * region. Nothing here is a raw fault code. Each maps to copy that states what
 * happened, what it means, and the next action.
 */
public sealed interface TypedFailure {

    /**
     * The shared engine is not part of this build.
     *
     * This is the honest terminal state for the Kotlin-only shell: the control
     * surface can reach every state up to the point where packets would be handed
     * over, and stops there.
     */
    public data object EngineUnavailable : TypedFailure

    /** The consent dialog was shown and the user declined. Recoverable. */
    public data object ConsentDenied : TypedFailure

    /** VpnService.prepare returned no intent and no permission. Not recoverable here. */
    public data object ConsentUnavailable : TypedFailure

    /** VpnService.protect refused a socket. The engine fails closed rather than loop. */
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

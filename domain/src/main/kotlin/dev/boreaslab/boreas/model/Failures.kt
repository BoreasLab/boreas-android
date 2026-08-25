package dev.boreaslab.boreas.model

public enum class Operation { Start, Stop, Reload, Reconfigure, NetworkChange }

/** Closed, typed failures mapped to user-facing recovery copy. */
public sealed interface TypedFailure {

    /** No engine in this build: the simulated host, or a shell with nothing linked. */
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

    /**
     * The shared library is not on this device, or the dynamic linker refused it.
     *
     * Distinct from a tunnel the core declined to build: nothing was asked of the
     * core, because nothing could be called.
     */
    public data class CoreNotLoaded(val detail: String) : TypedFailure

    /**
     * The header this app compiled against and the library it loaded disagree.
     *
     * Checked before anything else and refused rather than worked around: a stale
     * library reads every field at the wrong offset and behaves inexplicably, and
     * there is no later moment at which that is cheap to notice.
     */
    public data class CoreAbiMismatch(val compiled: Int, val loaded: Int) : TypedFailure

    /** The core answered, and the answer was not success. */
    public data class CoreRefused(val operation: Operation, val status: CoreStatus) : TypedFailure
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
        // Nothing the user can do from a screen: the install is wrong, not the input.
        is TypedFailure.CoreNotLoaded -> false
        is TypedFailure.CoreAbiMismatch -> false
        is TypedFailure.CoreRefused -> status.recoverable
    }

/**
 * Whether the user can act on a refusal, as opposed to it being a defect to report.
 *
 * The split is by who can fix it: a configuration the user typed, versus a null
 * pointer this program passed.
 */
private val CoreStatus.recoverable: Boolean
    get() = when (this) {
        CoreStatus.Config, CoreStatus.Egress, CoreStatus.Termination,
        CoreStatus.Authority, CoreStatus.Io, CoreStatus.NotUtf8,
        -> true
        // Ok never reaches a failure; the rest are defects or teardown states.
        CoreStatus.Ok, CoreStatus.NullArgument, CoreStatus.Datapath,
        CoreStatus.Stopped, CoreStatus.BufferTooSmall, CoreStatus.Panic,
        CoreStatus.Unrecognised,
        -> false
    }

package dev.boreaslab.boreas.model

/**
 * Every answer the core can return, decoded once at the boundary.
 *
 * Zero is success, so the C reads `if (boreas_...()) { failed; }`. Here it is a
 * closed sum eliminated exhaustively instead, and [of] is total: a constant this
 * build predates decodes to [Unrecognised] rather than throwing. That is what
 * api/stability.md asks for, and it is why adding a constant is a minor change
 * upstream rather than a break here.
 *
 * This lives in the pure module so that failure copy can be written against it
 * without the shell's binding library coming with it.
 */
public enum class CoreStatus(public val code: Int) {

    Ok(0),

    /** A required pointer was null. Always a defect in this program. */
    NullArgument(1),

    /** A string argument was not valid UTF-8. */
    NotUtf8(2),

    /** The configuration describes a tunnel that cannot exist. Nothing was built. */
    Config(3),

    /** Stored authority material was lost, corrupted, or is not two halves of one authority. */
    Authority(4),

    /** An egress could not be built from its configuration. */
    Egress(5),

    /** The connection ceiling cannot hold a listening backlog for every inspected port. */
    Termination(6),

    /** The datapath refused the combination it was handed. Close to a defect. */
    Datapath(7),

    /** A socket the tunnel needs could not be opened through the bypass. */
    Io(8),

    /** The tunnel has stopped. Expected during teardown; the handle is still valid to free. */
    Stopped(9),

    /** An output buffer was too small. The length out-parameter says how large. */
    BufferTooSmall(10),

    /** A defect in the core. Free the handle, do not retry on it, and report it. */
    Panic(11),

    /** A failure this build predates. */
    Unrecognised(12),
    ;

    public val succeeded: Boolean get() = this == Ok

    public companion object {
        private val BY_CODE: Map<Int, CoreStatus> = entries.associateBy(CoreStatus::code)

        /** Total over every `int` the ABI could return. */
        public fun of(code: Int): CoreStatus = BY_CODE[code] ?: Unrecognised
    }
}

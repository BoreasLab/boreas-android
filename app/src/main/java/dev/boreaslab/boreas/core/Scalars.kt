package dev.boreaslab.boreas.core

import com.sun.jna.IntegerType
import com.sun.jna.Native
import com.sun.jna.ptr.ByReference

/**
 * The pointer-width scalars, declared once.
 *
 * api/abi.md's type table is explicit that getting these wrong does not produce a
 * link error, it produces a field read from the middle of another field. JNA has
 * no `size_t`, and `NativeLong` is only pointer-width where the platform's `long`
 * is, which is a coincidence that happens to hold on every Android ABI and is not
 * a thing to rely on. Both types below take their width from JNA's own measurement
 * of the running ABI instead, so the same code is right on all three.
 */

/**
 * `size_t`. Unsigned, pointer-width.
 *
 * The parameter has a default so that Kotlin emits the no-argument constructor
 * JNA reflects on when it materialises one of these out of native memory. The two
 * narrowing conversions are Kotlin's to require; `IntegerType` already carries the
 * value at full width and both would truncate a real length, so neither is used
 * anywhere and both are exact about what they do.
 */
internal class SizeT(value: Long = 0) : IntegerType(Native.SIZE_T_SIZE, value, true) {

    override fun toShort(): Short = toLong().toShort()

    override fun toByte(): Byte = toLong().toByte()

    internal companion object {
        val ZERO: SizeT = SizeT(0)
        fun of(value: Int): SizeT = SizeT(value.toLong())
    }
}

/** `intptr_t`. Signed, pointer-width. Callback returns are byte counts or negative errnos. */
internal class SSizeT(value: Long = 0) : IntegerType(Native.POINTER_SIZE, value, false) {

    override fun toShort(): Short = toLong().toShort()

    override fun toByte(): Byte = toLong().toByte()
}

/**
 * `size_t *`, for the two-call sizing protocol on `boreas_tunnel_authority`.
 *
 * Reads at the platform's width rather than assuming eight bytes, and widens
 * without sign extension, because a length is never negative and a 32-bit ABI
 * would otherwise turn a large one into one.
 */
internal class SizeTByReference : ByReference(Native.SIZE_T_SIZE) {
    var value: Long
        get() = when (Native.SIZE_T_SIZE) {
            Long.SIZE_BYTES -> pointer.getLong(0)
            else -> pointer.getInt(0).toLong() and 0xFFFF_FFFFL
        }
        set(next) = when (Native.SIZE_T_SIZE) {
            Long.SIZE_BYTES -> pointer.setLong(0, next)
            else -> pointer.setInt(0, next.toInt())
        }
}

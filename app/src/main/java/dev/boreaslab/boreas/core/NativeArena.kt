package dev.boreaslab.boreas.core

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * Every native allocation one `boreas_tunnel_start` borrows, released together.
 *
 * The contract is precise about the lifetime: every string and byte array in the
 * configuration is copied before the call returns, and nothing may be freed
 * before it. One arena around one call expresses exactly that, and `use` makes the
 * release structural rather than something the last edit has to remember.
 *
 * JNA would allocate a `String` field's bytes for us, in the platform's default
 * encoding, tied to the struct's lifetime. Every string here is UTF-8 by contract,
 * so the encoding is named instead of inherited.
 */
internal class NativeArena : AutoCloseable {

    private val blocks = ArrayList<Memory>()

    /** A NUL-terminated UTF-8 copy. */
    fun utf8(text: String): Pointer {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val block = allocate(bytes.size + 1L)
        block.write(0, bytes, 0, bytes.size)
        block.setByte(bytes.size.toLong(), 0)
        return block
    }

    /**
     * A `const char *const *`, or null for the empty set.
     *
     * Null rather than a zero-length table because the ABI reads the pointer only
     * when the count is non-zero, and a table nobody reads is a table to get wrong.
     *
     * O(total bytes) in time and space.
     */
    fun utf8Array(values: List<String>): Pointer? {
        if (values.isEmpty()) return null
        val table = allocate(values.size.toLong() * Native.POINTER_SIZE)
        values.forEachIndexed { index, value ->
            table.setPointer(index.toLong() * Native.POINTER_SIZE, utf8(value))
        }
        return table
    }

    /** A copy of [source], or null when there is nothing to copy. */
    fun bytes(source: ByteArray): Pointer? {
        if (source.isEmpty()) return null
        val block = allocate(source.size.toLong())
        block.write(0, source, 0, source.size)
        return block
    }

    private fun allocate(size: Long): Memory {
        val block = Memory(size)
        blocks += block
        return block
    }

    override fun close() {
        // Freed in reverse, so a table is released before the strings it points at.
        // Nothing reads them at this point either way; the order costs nothing and
        // keeps the invariant true of the arena rather than of the call site.
        for (index in blocks.indices.reversed()) blocks[index].close()
        blocks.clear()
    }
}

package dev.boreaslab.boreas.core

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.sun.jna.Pointer
import java.io.FileDescriptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The TUN, as the core sees it.
 *
 * Three obligations, and each one is a way to get this wrong quietly:
 *
 *  - **`recv` never parks indefinitely.** It polls with a bounded timeout and
 *    returns `0`, which the ABI reserves for "nothing yet, ask again" because
 *    there is no zero-length IP packet. That is also what makes `close` cheap:
 *    the flag it sets is seen within one poll interval.
 *
 *  - **The descriptor is never closed to unblock a read.** `close(2)`'s own
 *    CAVEATS call that unwise, and on Linux the blocked read holds a reference to
 *    the open file description, so it may not return at all while the descriptor
 *    *number* is already free for another thread to reuse. Nothing here closes
 *    anything; [awaitRelease] is how the owner learns it is safe to.
 *
 *  - **`send` is all-or-nothing.** The unit is the packet, and the remainder of a
 *    short write carries no header, so there is no second packet to send it as.
 *
 * Every callback runs on an arbitrary core thread, never the one that started the
 * tunnel and not always the same one, so every field it touches is atomic or
 * immutable. The callback objects are held here, in fields, for the whole life of
 * the tunnel: JNA collects a trampoline with the object it belongs to, and a
 * callback that went out of scope would be a call through freed memory.
 */
internal class TunDevice(private val descriptor: ParcelFileDescriptor) {

    private val fd: FileDescriptor = descriptor.fileDescriptor
    private val closed = AtomicBoolean(false)
    private val released = CountDownLatch(1)

    /**
     * Reads one packet, or reports that there is not one yet.
     *
     * O(1) syscalls per call and no copy: the core's own buffer is wrapped as a
     * direct `ByteBuffer` and the kernel writes into it. The wrapper is a small
     * object per call rather than a cached one, because `recv` is called from one
     * thread at a time but not always the same one, and a cache would need to be
     * visible across them to be worth having.
     */
    val recv = BoreasDevice.Recv { _, buffer, capacity ->
        if (closed.get()) return@Recv SSizeT(-OsConstants.EIO.toLong())

        val room = minOf(capacity.toLong(), Int.MAX_VALUE.toLong()).toInt()
        if (room <= 0) return@Recv SSizeT(-OsConstants.EINVAL.toLong())

        try {
            val poll = StructPollfd().apply {
                fd = this@TunDevice.fd
                events = OsConstants.POLLIN.toShort()
            }
            // Zero means the interval elapsed with nothing to read, which is the
            // answer the ABI has a value for.
            if (Os.poll(arrayOf(poll), POLL_TIMEOUT_MS) == 0) return@Recv SSizeT(0)
            if (closed.get()) return@Recv SSizeT(-OsConstants.EIO.toLong())

            val read = Os.read(fd, buffer.getByteBuffer(0, room.toLong()))
            // A read of zero on a character device is not a packet either.
            SSizeT(if (read > 0) read.toLong() else 0)
        } catch (error: ErrnoException) {
            // Interrupted or momentarily unready is "ask again", not a failure.
            if (error.errno == OsConstants.EINTR || error.errno == OsConstants.EAGAIN) {
                SSizeT(0)
            } else {
                SSizeT(-error.errno.toLong())
            }
        } catch (error: Throwable) {
            // Nothing may unwind into a C frame. A defect here becomes an errno.
            failed(error)
        }
    }

    /** Writes one packet, whole. A partial write is reported as an error. */
    val send = BoreasDevice.Send { _, buffer, length ->
        if (closed.get()) return@Send SSizeT(-OsConstants.EIO.toLong())

        val size = minOf(length.toLong(), Int.MAX_VALUE.toLong()).toInt()
        if (size <= 0) return@Send SSizeT(-OsConstants.EINVAL.toLong())

        try {
            val written = Os.write(fd, buffer.getByteBuffer(0, size.toLong()))
            if (written == size) SSizeT(0) else SSizeT(-OsConstants.EIO.toLong())
        } catch (error: ErrnoException) {
            SSizeT(-error.errno.toLong())
        } catch (error: Throwable) {
            failed(error)
        }
    }

    /**
     * Called before `release`, and possibly while a `recv` is inside `poll`.
     *
     * Safe to call concurrently with one because all it does is set a flag the next
     * pass reads, and the next pass is at most [POLL_TIMEOUT_MS] away.
     */
    val close = BoreasDevice.Close { closed.set(true) }

    /**
     * Runs once, after every other callback has returned.
     *
     * This is the only signal that nothing is inside the callbacks any more. It can
     * arrive after `boreas_tunnel_free` has returned, if a `recv` was still in
     * flight when the tunnel stopped, so the owner waits for it rather than for
     * `free`.
     */
    val release = BoreasDevice.Release {
        closed.set(true)
        released.countDown()
    }

    /**
     * Blocks until the core has let go, then reports whether it did.
     *
     * A bounded wait rather than an indefinite one: this runs on the teardown path,
     * and a core that never released would otherwise hold the service's shutdown
     * open forever. The descriptor is left alone on a timeout, which leaks one fd
     * for the life of the process and is the safer of the two mistakes.
     */
    fun awaitRelease(): Boolean = released.await(RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS)

    /**
     * Closes the descriptor. The one owner, called once, only after [awaitRelease].
     *
     * `getFd` rather than `detachFd` on purpose: the `ParcelFileDescriptor` keeps
     * ownership and closes through its own API, which makes a double close
     * structurally impossible instead of a rule to follow.
     */
    fun dispose() {
        runCatching { descriptor.close() }
    }

    /** Fills in the vtable. Every field the ABI marks required is set here. */
    fun vtable(mtu: Int): BoreasDevice = BoreasDevice().also { table ->
        table.recv = recv
        table.send = send
        table.close = close
        table.release = release
        table.mtu = mtu.toShort()
    }

    private fun failed(error: Throwable): SSizeT {
        // Deliberately not rethrown: a Kotlin exception crossing into C is a call
        // through a frame that cannot handle it.
        closed.set(true)
        lastDefect = error
        return SSizeT(-OsConstants.EIO.toLong())
    }

    /** The first defect a callback swallowed, for the diagnostics screen. */
    @Volatile
    var lastDefect: Throwable? = null
        private set

    private companion object {
        /**
         * Short enough that `close` is felt promptly, long enough that an idle
         * tunnel is not a spin loop. Ten polls a second on an interface with no
         * traffic is a cost nothing notices.
         */
        const val POLL_TIMEOUT_MS = 100

        /** `boreas_tunnel_free` itself waits up to 250 ms; this is that, with room. */
        const val RELEASE_TIMEOUT_MS = 2_000L
    }
}

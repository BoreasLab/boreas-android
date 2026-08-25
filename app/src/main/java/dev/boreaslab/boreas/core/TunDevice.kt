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
 * TUN adapter for the core. `recv` polls with a bounded timeout and returns `0`
 * for "ask again"; `send` accepts only whole packets. The descriptor is never
 * closed to unblock a read: `close(2)` CAVEATS warn against it, and
 * [awaitRelease] identifies when callbacks have ended.
 *
 * Callbacks run on arbitrary core threads, so their shared state is atomic or
 * immutable. Fields retain the callback objects for the tunnel lifetime because
 * JNA's trampoline must not outlive its callback.
 */
internal class TunDevice(private val descriptor: ParcelFileDescriptor) {

    private val fd: FileDescriptor = descriptor.fileDescriptor
    private val closed = AtomicBoolean(false)
    private val released = CountDownLatch(1)

    /** Reads one packet, or reports that there is not one yet. */
    val recv = BoreasDevice.Recv { _, buffer, capacity ->
        if (closed.get()) return@Recv SSizeT(-OsConstants.EIO.toLong())

        val room = minOf(capacity.toLong(), Int.MAX_VALUE.toLong()).toInt()
        if (room <= 0) return@Recv SSizeT(-OsConstants.EINVAL.toLong())

        try {
            val poll = StructPollfd().apply {
                fd = this@TunDevice.fd
                events = OsConstants.POLLIN.toShort()
            }
            // Zero means that the poll interval elapsed without a packet.
            if (Os.poll(arrayOf(poll), POLL_TIMEOUT_MS) == 0) return@Recv SSizeT(0)
            if (closed.get()) return@Recv SSizeT(-OsConstants.EIO.toLong())

            val read = Os.read(fd, buffer.getByteBuffer(0, room.toLong()))
            // A zero-length character-device read is not a packet.
            SSizeT(if (read > 0) read.toLong() else 0)
        } catch (error: ErrnoException) {
            // Interrupted or temporarily unready means "ask again".
            if (error.errno == OsConstants.EINTR || error.errno == OsConstants.EAGAIN) {
                SSizeT(0)
            } else {
                SSizeT(-error.errno.toLong())
            }
        } catch (error: Throwable) {
            // Exceptions must not unwind into a C frame.
            failed(error)
        }
    }

    /** Writes one whole packet; a partial write is an error. */
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

    /** Sets the stop flag before `release`, possibly while `recv` is in `poll`. */
    val close = BoreasDevice.Close { closed.set(true) }

    /**
     * Runs once after every other callback returns. It can arrive after
     * `boreas_tunnel_free` if a `recv` was still in flight, so the owner waits for
     * this signal rather than for `free`.
     */
    val release = BoreasDevice.Release {
        closed.set(true)
        released.countDown()
    }

    /**
     * Waits for callback release with a bound. On timeout the descriptor remains
     * open, avoiding a close while a callback may still be using it.
     */
    fun awaitRelease(): Boolean = released.await(RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS)

    /**
     * Closes the descriptor after [awaitRelease]. `getFd` retains ownership in
     * [ParcelFileDescriptor], unlike `detachFd`, so disposal uses this API once.
     */
    fun dispose() {
        runCatching { descriptor.close() }
    }

    fun vtable(mtu: Int): BoreasDevice = BoreasDevice().also { table ->
        table.recv = recv
        table.send = send
        table.close = close
        table.release = release
        table.mtu = mtu.toShort()
    }

    private fun failed(error: Throwable): SSizeT {
        // A Kotlin exception cannot cross into C.
        closed.set(true)
        lastDefect = error
        return SSizeT(-OsConstants.EIO.toLong())
    }

    /** First callback defect retained for diagnostics. */
    @Volatile
    var lastDefect: Throwable? = null
        private set

    private companion object {
        /** Bounds stop latency without busy-spinning on an idle tunnel. */
        const val POLL_TIMEOUT_MS = 100

        /** Bounds the wait for the core's release callback. */
        const val RELEASE_TIMEOUT_MS = 2_000L
    }
}

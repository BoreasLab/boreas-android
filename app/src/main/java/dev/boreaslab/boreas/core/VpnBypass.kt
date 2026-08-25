package dev.boreaslab.boreas.core

import android.net.VpnService

/**
 * Protects core-owned sockets from re-entering the tunnel. The native API describes
 * a JNI entry point, but this app reaches the same callback through JNA: JNA attaches
 * arbitrary native callback threads to the JVM. See api/android.md#the-bypass.
 */
internal class VpnBypass(private val service: VpnService) {

    /** Protects one socket, or refuses. A false result fails the dial. */
    val protect = BoreasBypass.Protect { _, socket ->
        // Protect accepts an int; truncating a wider value could protect another fd.
        if (socket !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return@Protect OUT_OF_RANGE

        try {
            if (service.protect(socket.toInt())) 0 else REFUSED
        } catch (error: Throwable) {
            // Exceptions must not unwind into a C frame.
            lastDefect = error
            REFUSED
        }
    }

    val release = BoreasBypass.Release { }

    fun vtable(): BoreasBypass = BoreasBypass().also { table ->
        table.protect = protect
        table.release = release
    }

    /** First callback defect retained for diagnostics. */
    @Volatile
    var lastDefect: Throwable? = null
        private set

    private companion object {
        /** VpnService refused or threw. */
        const val REFUSED = -1

        /** Socket was outside the Java `int` range. */
        const val OUT_OF_RANGE = -3
    }
}

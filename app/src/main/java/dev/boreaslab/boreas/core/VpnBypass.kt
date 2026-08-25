package dev.boreaslab.boreas.core

import android.net.VpnService

/**
 * Sockets the core opens for itself, kept out of the tunnel it is serving.
 *
 * Every socket the core opens goes through here: the egress's, the resolver's, and
 * any datagram relay's. Skipping it produces a tunnel that starts, reports itself
 * healthy, and hangs, because each packet the resolver sends re-enters the tunnel
 * it was answering for.
 *
 * ## Why this is written by hand
 *
 * api/android.md#the-bypass says not to: `boreas_android_bypass(void *env, void
 * *service, BoreasBypass *out)` builds this vtable, because `protect` has to be
 * called from a worker thread the JVM never created and a plain C function pointer
 * has no way to reach Java from one.
 *
 * That entry point is not reachable from the route this app takes. It needs a
 * `JNIEnv *` and a `jobject`, and JNA can supply the first (`JNIEnv.CURRENT`) but
 * not the second: its argument marshaller handles primitives, `Pointer`,
 * `Structure`, `Buffer`, arrays, `String`, `Callback`, `NativeMapped` and
 * `JNIEnv`, and has no case that passes an arbitrary Java object as a `jobject`.
 * So calling it needs the `Java_...` shim the page describes, in C, which needs
 * the NDK. This is reported upstream as a gap in that page rather than worked
 * around silently.
 *
 * What is written here is not a reimplementation of the JNI the core would do. It
 * is the same mechanism the same page prescribes for the *device* vtable, applied
 * to one more callback: JNA attaches the calling native thread to the JVM before
 * invoking a callback, which is precisely the obligation `boreas_android_bypass`
 * exists to discharge. If `recv` may be a JNA callback called from an arbitrary
 * core thread, so may this.
 *
 * The refusal codes match the ones the page documents, so a capture reads the same
 * either way.
 */
internal class VpnBypass(private val service: VpnService) {

    /**
     * Excludes one socket, or refuses.
     *
     * `VpnService.protect` returns false when the app is not prepared or its
     * permission was revoked. That is a refusal and it fails the dial; it is never
     * downgraded to success, because an unprotected socket is the silent mistake
     * this whole vtable exists to prevent.
     */
    val protect = BoreasBypass.Protect { _, socket ->
        // A file descriptor is an int. Anything outside that range did not come
        // from this platform, and protect(int) would truncate it into one that did.
        if (socket !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return@Protect OUT_OF_RANGE

        try {
            if (service.protect(socket.toInt())) 0 else REFUSED
        } catch (error: Throwable) {
            // Nothing may unwind into a C frame.
            lastDefect = error
            REFUSED
        }
    }

    /** The context is null, so there is nothing to release; the callback records the call. */
    val release = BoreasBypass.Release { }

    fun vtable(): BoreasBypass = BoreasBypass().also { table ->
        table.protect = protect
        table.release = release
    }

    /** The first defect the callback swallowed, for the diagnostics screen. */
    @Volatile
    var lastDefect: Throwable? = null
        private set

    private companion object {
        /** The VpnService refused or threw. */
        const val REFUSED = -1

        /** The socket was outside the Java `int` range. */
        const val OUT_OF_RANGE = -3
    }
}

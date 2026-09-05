package dev.boreaslab.boreas.device

import android.os.ParcelFileDescriptor.AutoCloseInputStream
import android.os.SystemClock
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

// What an instrumented test can do that the app cannot. Everything here reads
// or writes state outside the app: the shell, the app-op table, /proc. The
// assertions live in the test classes.
//
// Test names are camelCase rather than the backtick sentences :domain uses: DEX
// below version 040 forbids a space in a member name, and 040 needs minSdk 30.

/** Runs a command as the shell UID and returns its combined output. */
internal fun shell(command: String): String {
    val output = InstrumentationRegistry.getInstrumentation()
        .uiAutomation
        .executeShellCommand(command)
    return AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
}

/** Android's record of whether this package may become the VPN. */
internal enum class Consent(val mode: String) {
    Granted("allow"),
    Withheld("deny"),
}

/**
 * Sets consent without a dialog, and waits for the table to say so.
 *
 * `VpnService.prepare` reads this app-op, so writing it is the grant the system
 * dialog performs. Nothing else can: the dialog is a system Activity, and a
 * headless emulator has nobody to tap it.
 *
 * The write returns before the read shows it. Left unchecked, the next line of a
 * test reads the old answer, and on a slow API 29 image that is what happened.
 */
internal fun setConsent(consent: Consent) {
    val target = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    shell("appops set $target ACTIVATE_VPN ${consent.mode}")

    val deadline = SystemClock.uptimeMillis() + SETTLE_MILLIS
    do {
        if (shell("appops get $target ACTIVATE_VPN").contains(consent.mode)) return
        SystemClock.sleep(POLL_MILLIS)
    } while (SystemClock.uptimeMillis() < deadline)

    throw AssertionError("ACTIVATE_VPN did not become ${consent.mode} within ${SETTLE_MILLIS}ms")
}

/** How long an app-op write may take to become readable, and how often to look. */
private const val SETTLE_MILLIS = 5_000L
private const val POLL_MILLIS = 50L

/**
 * Descriptors of this process that name the TUN device.
 *
 * Instrumentation runs inside the app process, so these are the service's own.
 * A leaked tunnel descriptor is otherwise unreported: the core owns it after
 * start, and a stopped session still holding one looks like a stopped session.
 */
internal fun tunDescriptors(): List<String> =
    (File("/proc/self/fd").list() ?: emptyArray())
        .mapNotNull { entry -> runCatching { Os.readlink("/proc/self/fd/$entry") }.getOrNull() }
        .filter { link -> link.startsWith("/dev/tun") }

package dev.boreaslab.boreas.device

import android.os.ParcelFileDescriptor.AutoCloseInputStream
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import dev.boreaslab.boreas.service.SessionStateBus
import dev.boreaslab.boreas.service.VpnLifecycleState
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

// What an instrumented test can do that the app cannot, and the waits it needs.
// Test names here are camelCase rather than the backtick sentences :domain uses.
// DEX below version 040 forbids a space in a member name, and 040 needs minSdk 30.
// Everything here reads or writes state outside the app: the shell, the app-op
// table, /proc. The assertions live in the test classes.

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
 * Sets consent without a dialog.
 *
 * `VpnService.prepare` reads this app-op, so writing it is the grant the system
 * dialog performs. Nothing else can: the dialog is a system Activity, and a
 * headless emulator has nobody to tap it.
 */
internal fun setConsent(consent: Consent) {
    val target = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    shell("appops set $target ACTIVATE_VPN ${consent.mode}")
}

/**
 * Waits for a state the session actually passed through, and names them all if
 * it never did.
 *
 * Reads the transition log rather than `SessionStateBus.state`. That cell is a
 * StateFlow, so it conflates: a session that reached Running and stopped again
 * between two resumptions of the collector shows only the last of them, and the
 * test then waits sixty seconds for something that already happened. The log
 * only grows, so conflating it loses nothing.
 */
internal fun awaitTransition(
    timeoutMillis: Long,
    predicate: (VpnLifecycleState) -> Boolean,
): VpnLifecycleState = runBlocking {
    val seen = withTimeoutOrNull(timeoutMillis) {
        SessionStateBus.log.first { entries -> entries.any { entry -> predicate(entry.state) } }
    } ?: throw AssertionError("waited ${timeoutMillis}ms; transitions were ${transitions()}")
    seen.first { entry -> predicate(entry.state) }.state
}

/** Newest first, for a failure message. */
internal fun transitions(): List<VpnLifecycleState> =
    SessionStateBus.log.value.map { entry -> entry.state }

/** The log outlives a test method, so a test that reads it starts by emptying it. */
internal fun forgetTransitions() = SessionStateBus.clearLog()

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

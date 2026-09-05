package dev.boreaslab.boreas.device

import android.content.Intent
import android.net.VpnService
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boreaslab.boreas.MainActivity
import dev.boreaslab.boreas.service.BoreasVpnService
import dev.boreaslab.boreas.service.VpnLifecycleState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A whole session, on a real interface, driven by the intents the UI sends.
 *
 * An Activity stays on screen for the run because a backgrounded app may not
 * start a service, and the shell cannot start this one either: it is not
 * exported and asks for BIND_VPN_SERVICE, which the shell UID does not hold.
 *
 * Consent is granted throughout. The withheld case belongs to ConsentTest, where
 * nothing is listening for a consent request; here it would reach MainActivity
 * and put the system dialog on screen with nobody to answer it.
 *
 * In the release source set, not because release is the interesting build but
 * because a debug one never reaches Running: see docs/platform-integration.md.
 * A source set says so at compile time, where an assumption would report a
 * skipped test as a failed one on every debug run.
 */
@RunWith(AndroidJUnit4::class)
class TunnelLifecycleTest {

    @get:Rule
    val activity: ActivityScenarioRule<MainActivity> = ActivityScenarioRule(MainActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun forget() = forgetTransitions()

    @After
    fun stopAndWithhold() {
        command(BoreasVpnService.ACTION_STOP)
        // Cleanup, not an assertion. A wait that threw here would replace the
        // test body's message with this one.
        runCatching { awaitTransition(TEARDOWN_MILLIS) { state -> state is VpnLifecycleState.Stopped } }
        setConsent(Consent.Withheld)
    }

    /**
     * Twice, because a descriptor leaked once per cycle is invisible in one run:
     * the count after a single stop is equally consistent with releasing the
     * descriptor and with never having opened it.
     */
    @Test
    fun aSessionStartsRunsAndLeavesNoDescriptorBehind() {
        setConsent(Consent.Granted)
        // Without this the next sixty seconds are spent in AwaitingConsent, and
        // the timeout blames the tunnel for a grant that never arrived.
        assertNull("consent did not take effect", VpnService.prepare(context))

        val before = tunDescriptors()

        repeat(2) {
            command(BoreasVpnService.ACTION_START)
            val settled = awaitTransition(STARTUP_MILLIS) { state ->
                state is VpnLifecycleState.Running || state is VpnLifecycleState.Failed
            }
            assertTrue("start ended at $settled", settled is VpnLifecycleState.Running)

            command(BoreasVpnService.ACTION_STOP)
            awaitTransition(TEARDOWN_MILLIS) { state -> state is VpnLifecycleState.Stopped }
            forgetTransitions()
        }

        assertEquals(before, tunDescriptors())
    }

    /** A null return is the system saying it resolved no such service, in silence. */
    private fun command(action: String) {
        val started = context.startService(Intent(context, BoreasVpnService::class.java).setAction(action))
        checkNotNull(started) { "no service resolved for $action" }
    }

    private companion object {
        /** dlopen, the interface, and the core's own start, on a cold emulator. */
        const val STARTUP_MILLIS = 60_000L
        const val TEARDOWN_MILLIS = 15_000L
    }
}

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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A session, on a real interface, driven by the intents the UI sends.
 *
 * An Activity stays on screen for the run because a backgrounded app may not
 * start a service, and the shell cannot start this one either: it is not
 * exported and asks for BIND_VPN_SERVICE, which the shell UID does not hold.
 *
 * Consent is granted throughout. The withheld case belongs to ConsentTest, where
 * nothing is listening for a consent request; here it would reach MainActivity
 * and put the system dialog on screen with nobody to answer it.
 *
 * What this asserts is where the session stops today, not where it should stop.
 * See docs/platform-integration.md: the tunnel comes up and the session never
 * leaves Starting. The test says so out loud so the run stays honest, and it
 * fails the day that changes, which is the day to restore the real assertion:
 * Running, a stop, and no descriptor left open.
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

    @Test
    fun aSessionStopsAtStarting() {
        setConsent(Consent.Granted)
        // Without this the wait below is spent in AwaitingConsent, and the
        // timeout blames the tunnel for a grant that never arrived.
        assertNull("consent did not take effect", VpnService.prepare(context))

        command(BoreasVpnService.ACTION_START)
        awaitTransition(STARTUP_MILLIS) { state -> state is VpnLifecycleState.Starting }

        val settled = transitionWithin(SETTLE_MILLIS) { state ->
            state is VpnLifecycleState.Running || state is VpnLifecycleState.Failed
        }
        assertNull(
            "the session reached $settled, so the finding in docs/platform-integration.md is " +
                "fixed: assert Running here and drop this line",
            settled,
        )

        command(BoreasVpnService.ACTION_STOP)
        awaitTransition(TEARDOWN_MILLIS) { state -> state is VpnLifecycleState.Stopped }
    }

    /** A null return is the system saying it resolved no such service, in silence. */
    private fun command(action: String) {
        val started = context.startService(Intent(context, BoreasVpnService::class.java).setAction(action))
        checkNotNull(started) { "no service resolved for $action" }
    }

    private companion object {
        /**
         * dlopen, the interface, and the core's own start.
         *
         * Generous because the emulator is still compiling the app it just
         * installed: a minute of dexopt has been seen between the start command
         * and the service acting on it, and a run that gave up inside that
         * window reported no transitions at all.
         */
        const val STARTUP_MILLIS = 150_000L

        /** Long enough that a session which was going to settle would have. */
        const val SETTLE_MILLIS = 20_000L
        const val TEARDOWN_MILLIS = 15_000L
    }
}

package dev.boreaslab.boreas.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boreaslab.boreas.service.BoreasVpnService
import dev.boreaslab.boreas.service.VpnLifecycleState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A whole session, on a real interface, driven by the intents the UI sends.
 *
 * The service is started through the shell rather than `Context.startService`,
 * so no Activity has to be on screen. That also leaves ConsentBroker without a
 * subscriber, which is what makes the withheld case terminate: with nobody to
 * show the dialog, the session stays where it should and the test can say so.
 */
@RunWith(AndroidJUnit4::class)
class TunnelLifecycleTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun stopAndWithhold() {
        command(BoreasVpnService.ACTION_STOP)
        awaitState(TEARDOWN_MILLIS) { state -> state is VpnLifecycleState.Stopped }
        setConsent(Consent.Withheld)
    }

    /**
     * Twice, because a descriptor leaked once per cycle is invisible in one run:
     * the count after a single stop is equally consistent with releasing the
     * descriptor and with never having opened it.
     */
    @Test
    fun `a session starts, runs, and leaves no descriptor behind`() {
        setConsent(Consent.Granted)
        val before = tunDescriptors()

        repeat(2) {
            command(BoreasVpnService.ACTION_START)
            val settled = awaitState(STARTUP_MILLIS) { state ->
                state is VpnLifecycleState.Running || state is VpnLifecycleState.Failed
            }
            assertTrue("start ended at $settled", settled is VpnLifecycleState.Running)

            command(BoreasVpnService.ACTION_STOP)
            awaitState(TEARDOWN_MILLIS) { state -> state is VpnLifecycleState.Stopped }
        }

        assertEquals(before, tunDescriptors())
    }

    /** The core is handed a descriptor only after Android has said yes. */
    @Test
    fun `a withheld grant stops short of establishing`() {
        setConsent(Consent.Withheld)

        command(BoreasVpnService.ACTION_START)
        awaitState(STARTUP_MILLIS) { state -> state is VpnLifecycleState.AwaitingConsent }

        assertEquals(emptyList<String>(), tunDescriptors())
    }

    /** `am` reports a refusal on stdout and still exits zero, so read the reply. */
    private fun command(action: String) {
        val component = "${context.packageName}/${BoreasVpnService::class.java.name}"
        val reply = shell("am startservice -n $component -a $action")
        check(!reply.contains("Error")) { "am refused $action: ${reply.trim()}" }
    }

    private companion object {
        /** dlopen, the interface, and the core's own start, on a cold emulator. */
        const val STARTUP_MILLIS = 60_000L
        const val TEARDOWN_MILLIS = 15_000L
    }
}

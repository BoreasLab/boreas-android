package dev.boreaslab.boreas.device

import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boreaslab.boreas.service.AndroidConsentGate
import dev.boreaslab.boreas.service.ConsentOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** `VpnService.prepare` is the only thing that decides consent; this pins both answers. */
@RunWith(AndroidJUnit4::class)
class ConsentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Later tests must not inherit a grant this one made. */
    @After
    fun withhold() = setConsent(Consent.Withheld)

    @Test
    fun `a granted app-op prepares with no prompt`() {
        setConsent(Consent.Granted)
        assertNull("prepare returned an Intent although the app-op allows it", VpnService.prepare(context))
        assertEquals(ConsentOutcome.Granted, runBlocking { AndroidConsentGate(context).request() })
    }

    @Test
    fun `a withheld app-op asks rather than grants`() {
        setConsent(Consent.Withheld)
        assertNotNull("prepare granted the VPN although the app-op denies it", VpnService.prepare(context))
    }
}

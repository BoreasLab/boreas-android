package dev.boreaslab.boreas.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.boreaslab.boreas.BuildConfig
import dev.boreaslab.boreas.core.BoreasCore
import dev.boreaslab.boreas.core.EngineLoad
import dev.boreaslab.boreas.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** The claims that need the shipped .so and nothing else. */
@RunWith(AndroidJUnit4::class)
class CoreLinkTest {

    /**
     * dlopen of libboreas.so, and the version the loaded object reports.
     *
     * One assertion on purpose. A missing `libc++_shared.so`, a missing symbol,
     * and a stale pin are all [EngineLoad] variants, so the message names which
     * of them happened instead of only that something did.
     */
    @Test
    fun `the library links and reports the pinned abi`() {
        val load = runBlocking { BoreasCore.describe() }
        assertEquals(EngineLoad.Linked(BuildConfig.BOREAS_ABI_VERSION), load)
    }

    /**
     * Every other claim in this source set is about the native engine, and a
     * debug build can be told to answer with a generated one instead.
     */
    @Test
    fun `this run would select the native engine`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val simulated = runBlocking { SettingsRepository(context).simulationEnabled.first() }
        assertFalse("a simulated session proves nothing about the core", simulated)
    }
}

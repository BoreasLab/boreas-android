package dev.boreaslab.boreas.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status decoder is total, which is what lets the ABI grow.
 *
 * api/stability.md permits adding an enum constant using the next unused value,
 * and asks callers to handle one they do not recognise rather than asserting
 * exhaustiveness. This is the check that this build does.
 */
class CoreStatusTest {

    @Test
    fun `every documented code decodes to itself`() {
        for (status in CoreStatus.entries) {
            assertEquals(status, CoreStatus.of(status.code))
        }
    }

    @Test
    fun `the codes are the ones the header assigns`() {
        // Written out rather than derived, so a renumbering is a failing test here
        // rather than a field read from the wrong offset on a device.
        assertEquals(0, CoreStatus.Ok.code)
        assertEquals(3, CoreStatus.Config.code)
        assertEquals(9, CoreStatus.Stopped.code)
        assertEquals(10, CoreStatus.BufferTooSmall.code)
        assertEquals(11, CoreStatus.Panic.code)
        assertEquals(12, CoreStatus.Unrecognised.code)
    }

    @Test
    fun `a constant this build predates decodes rather than throwing`() {
        assertEquals(CoreStatus.Unrecognised, CoreStatus.of(13))
        assertEquals(CoreStatus.Unrecognised, CoreStatus.of(9_999))
        assertEquals(CoreStatus.Unrecognised, CoreStatus.of(-1))
        assertEquals(CoreStatus.Unrecognised, CoreStatus.of(Int.MIN_VALUE))
    }

    @Test
    fun `only success succeeds`() {
        assertTrue(CoreStatus.Ok.succeeded)
        assertEquals(
            emptyList<CoreStatus>(),
            CoreStatus.entries.filter { it != CoreStatus.Ok && it.succeeded },
        )
    }

    @Test
    fun `a failure the user cannot act on is not offered as recoverable`() {
        assertTrue(!TypedFailure.CoreNotLoaded("no libc++").isRecoverable)
        assertTrue(!TypedFailure.CoreAbiMismatch(compiled = 1, loaded = 2).isRecoverable)
        assertTrue(!TypedFailure.CoreRefused(Operation.Start, CoreStatus.Panic).isRecoverable)
        assertTrue(TypedFailure.CoreRefused(Operation.Start, CoreStatus.Config).isRecoverable)
    }
}

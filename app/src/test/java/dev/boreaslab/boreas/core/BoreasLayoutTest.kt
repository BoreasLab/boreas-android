package dev.boreaslab.boreas.core

import com.sun.jna.Native
import com.sun.jna.Structure
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks JNA layouts against the widths and offsets pinned by `boreas.h`. The
 * tests use `Native.POINTER_SIZE`, matching `sizeof(void *)` across shipped ABIs.
 */
class BoreasLayoutTest {

    private val pointer = Native.POINTER_SIZE
    private val sizeT = Native.SIZE_T_SIZE

    private fun sizeOf(structure: Structure): Int = structure.size()
    private fun offsetOf(structure: BoreasStruct, field: String): Int = structure.offsetOf(field)

    // Scalar widths that affect every following field.

    @Test
    fun `size_t and intptr_t are pointer-width, which is what a wrong choice would break`() {
        assertEquals("size_t must be pointer-width", pointer, sizeT)
        // The marshalled width must match the platform pointer width.
        val marshalled = if (SizeT().nativeType() == Long::class.javaObjectType) 8 else 4
        assertEquals(pointer, marshalled)
    }

    @Test
    fun `a size_t out-parameter round-trips at the platform's width`() {
        val reference = SizeTByReference()
        reference.value = 0x0000_0001_0000_0000L.takeIf { pointer == 8 } ?: 0x7FFF_FFFFL
        assertEquals(
            if (pointer == 8) 0x0000_0001_0000_0000L else 0x7FFF_FFFFL,
            reference.value,
        )
    }

    // Vtable offsets: a shift would call the wrong function pointer.

    @Test
    fun `BoreasBypass is three pointers with context first`() {
        val bypass = BoreasBypass()
        assertEquals(0, offsetOf(bypass, "context"))
        assertEquals(3 * pointer, sizeOf(bypass))
    }

    // Read-back structs. `blocked` shifts under `-fshort-enums`.

    @Test
    fun `BoreasEvent puts kind first and blocked at offset four`() {
        val event = BoreasEvent()
        assertEquals(0, offsetOf(event, "kind"))
        assertEquals(4, offsetOf(event, "blocked"))
    }

    @Test
    fun `BoreasCounters is six 64-bit counters and nothing else`() {
        assertEquals(6 * Long.SIZE_BYTES, sizeOf(BoreasCounters()))
    }

    @Test
    fun `BoreasCeilings is six size_t and nothing else`() {
        assertEquals(6 * sizeT, sizeOf(BoreasCeilings()))
    }

    @Test
    fun `BoreasConfig puts egress first`() {
        assertEquals(0, offsetOf(BoreasConfig(), "egress"))
    }

    /**
     * The nested structs are embedded, not pointed at.
     *
     * A `Structure.ByReference` field would be one pointer wide and would compile,
     * link, and read every ceiling from an address the core never wrote.
     */
    @Test
    fun `BoreasConfig embeds wireguard and ceilings by value`() {
        val config = BoreasConfig()
        val wireguard = sizeOf(BoreasWireGuard())
        assertEquals(pointer, offsetOf(config, "wireguard"))
        assertEquals(
            "the field after wireguard must sit past the whole of it",
            true,
            offsetOf(config, "natBehavior") >= pointer + wireguard,
        )
        assertEquals(
            "ceilings is the last field, so the struct ends where it does",
            sizeOf(config),
            offsetOf(config, "ceilings") + sizeOf(BoreasCeilings()),
        )
    }

    @Test
    fun `BoreasWireGuard carries three raw 32-byte keys and a separate flag`() {
        val wireguard = BoreasWireGuard()
        assertEquals(pointer, offsetOf(wireguard, "privateKey"))
        assertEquals(pointer + 32, offsetOf(wireguard, "peerPublicKey"))
        assertEquals(pointer + 64, offsetOf(wireguard, "presharedKey"))
        // Zero bytes can be a configured key, so presence is a separate field.
        assertEquals(pointer + 96, offsetOf(wireguard, "hasPresharedKey"))
    }

    // Boolean fields are one byte.

    @Test
    fun `every bool field is one byte, not four`() {
        val event = BoreasEvent()
        // A four-byte `blocked` would shift the trailing counters.
        assertEquals(
            sizeOf(event),
            offsetOf(event, "counters") + 6 * Long.SIZE_BYTES,
        )

        // The one-byte boolean plus alignment gives a two-byte gap before `mtu`.
        val config = BoreasConfig()
        assertEquals(2, offsetOf(config, "mtu") - offsetOf(config, "rewriteDocuments"))
    }
}

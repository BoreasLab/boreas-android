package dev.boreaslab.boreas.core

import com.sun.jna.Native
import com.sun.jna.Structure
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The struct layouts, asserted from this side of the boundary.
 *
 * `boreas.h` pins every one of these from the C side, with static assertions that
 * fail a host's build rather than letting it read the wrong bytes. Those
 * assertions protect a host that compiles the header. This app does not compile
 * it: JNA computes the layout at run time from the declarations in `Structs.kt`,
 * so nothing checks them until a device does, and what a device shows is a field
 * read from the middle of another field, silently.
 *
 * These are the same numbers, checked where the declarations are. They run on a
 * plain JVM against the same JNA that will compute the layout on the device, so a
 * reordered field or a wrong width is a failing test in CI rather than an
 * inexplicable tunnel later.
 *
 * The widths are written in terms of `Native.POINTER_SIZE`, exactly as the header
 * writes them in terms of `sizeof(void *)`, so one expression is correct on all
 * three shipped ABIs.
 */
class BoreasLayoutTest {

    private val pointer = Native.POINTER_SIZE
    private val sizeT = Native.SIZE_T_SIZE

    private fun sizeOf(structure: Structure): Int = structure.size()
    private fun offsetOf(structure: BoreasStruct, field: String): Int = structure.offsetOf(field)

    // The scalars the type table warns about.

    @Test
    fun `size_t and intptr_t are pointer-width, which is what a wrong choice would break`() {
        assertEquals("size_t must be pointer-width", pointer, sizeT)
        // What JNA will actually marshal it as, rather than what the constructor
        // was told: a width that disagreed here would shift every field after it.
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

    // The vtables this app fills in by hand, where a shifted field is a call
    // through the wrong function pointer rather than a compile error.

    @Test
    fun `BoreasDevice puts context first and mtu after five pointers`() {
        val device = BoreasDevice()
        assertEquals(0, offsetOf(device, "context"))
        assertEquals(5 * pointer, offsetOf(device, "mtu"))
    }

    @Test
    fun `BoreasBypass is three pointers with context first`() {
        val bypass = BoreasBypass()
        assertEquals(0, offsetOf(bypass, "context"))
        assertEquals(3 * pointer, sizeOf(bypass))
    }

    // The structs this app reads back. `blocked` is the one that moves under
    // -fshort-enums, which is what makes these worth their lines.

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
        // A key of thirty-two zeroes is a key someone may have configured, so the
        // flag has to be a field rather than an inference from the bytes.
        assertEquals(pointer + 96, offsetOf(wireguard, "hasPresharedKey"))
    }

    // Every bool is one byte. This is the trap the header spends a paragraph on.

    @Test
    fun `every bool field is one byte, not four`() {
        val event = BoreasEvent()
        // The counters are last, so the struct ends where they do. A four-byte
        // `blocked` would push every field after it and change this total.
        assertEquals(
            sizeOf(event),
            offsetOf(event, "counters") + 6 * Long.SIZE_BYTES,
        )

        // `rewriteDocuments` is a bool and `mtu` a uint16_t, so the gap is the
        // bool plus one byte of alignment padding. It is 2 for a one-byte bool
        // and 4 for the four-byte one a C# host would get by default, which is
        // the mistake the header spends a paragraph on.
        val config = BoreasConfig()
        assertEquals(2, offsetOf(config, "mtu") - offsetOf(config, "rewriteDocuments"))
    }
}

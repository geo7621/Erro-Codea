package com.obdstudio.app.core

import org.junit.Assert.*
import org.junit.Test

class ObdCodecTest {
    @Test fun rpmWithHeadersAndSpaces() {
        assertEquals(1726.0, ObdCodec.pidValue("7E8 04 41 0C 1A F8\r>", 0x0C)!!, 0.01)
    }
    @Test fun speedWithoutSpaces() {
        assertEquals(40.0, ObdCodec.pidValue("410D28\r>", 0x0D)!!, 0.01)
    }
    @Test fun supportedBitMask() {
        assertTrue(0x0C in ObdCodec.supportedPids("SEARCHING...\r41 00 00 10 00 00\r>"))
    }
    @Test fun dtcDecoding() {
        assertEquals(listOf("P0133", "P0300"), ObdCodec.troubleCodes("43 01 33 03 00 00 00\r>", 0x43))
    }
    @Test fun noDataDoesNotCreateCodes() {
        assertTrue(ObdCodec.troubleCodes("NO DATA\r>", 0x43).isEmpty())
        assertNull(ObdCodec.pidValue("NO DATA\r>", 0x0C))
    }
    @Test fun adapterVoltage() {
        assertEquals(13.7, ObdCodec.adapterVoltage("13.7V\r>")!!, 0.01)
    }
}

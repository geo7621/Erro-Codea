package com.obdstudio.app.core

import org.junit.Assert.*
import org.junit.Test

class AdvancedDiagnosticsTest {
    @Test fun milStatusWithWarningLightAndCount() {
        val mil = ObdCodec.milStatus("41 01 82 00 00 00\r>")!!
        assertTrue(mil.illuminated)
        assertEquals(2, mil.confirmedCodeCount)
    }

    @Test fun milOffZeroCodes() {
        val mil = ObdCodec.milStatus("41 01 00 00 00 00\r>")!!
        assertFalse(mil.illuminated)
        assertEquals(0, mil.confirmedCodeCount)
    }

    @Test fun milAbsent() {
        assertNull(ObdCodec.milStatus("NO DATA\r>"))
    }

    @Test fun airflow() {
        assertEquals(27.5, ObdCodec.pidValue("41 10 0A BE\r>", 0x10)!!, .01)
    }

    @Test fun oilTemperature() {
        assertEquals(90.0, ObdCodec.pidValue("41 5C 82\r>", 0x5C)!!, .01)
    }

    @Test fun fuelRate() {
        assertEquals(10.0, ObdCodec.pidValue("41 5E 00 C8\r>", 0x5E)!!, .01)
    }

    @Test fun canResponderIds() {
        val raw = "SEARCHING...\r7E8 06 41 00 BE 1F A8 13\r7E9 06 41 00 98 18 00 01\r>"
        assertEquals(listOf("7E8", "7E9"), CanResponderCodec.responseIds(raw))
    }

    @Test fun can29BitHeaderSplitOrCompact() {
        val raw = "18 DA F1 10 06 41 00 BE 1F A8 13\r18DAF110 06 41 00 BE 1F A8 13\r>"
        assertEquals(listOf("18DAF110"), CanResponderCodec.responseIds(raw))
    }

    @Test fun rejectsEngineCodeAndUnrelatedAddresses() {
        val raw = "7E8 06 43 01 33 03 00 00\r7E0 06 41 00 BE 1F A8 13\r>"
        assertTrue(CanResponderCodec.responseIds(raw).isEmpty())
    }
}

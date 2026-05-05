package com.sza.fastmediasorter.data.network.datasource

import org.junit.Assert.assertEquals
import org.junit.Test

class TsPacketFormatDetectorTest {

    @Test
    fun `detect returns BD_192 for synthetic BD-TS probe`() {
        val probe = ByteArray(TsPacketFormatDetector.PROBE_BYTES)
        probe[4] = 0x47.toByte()
        probe[196] = 0x47.toByte()
        probe[388] = 0x47.toByte()
        // bytes 0, 192, 384 remain 0x00
        assertEquals(TsPacketFormat.BD_192, TsPacketFormatDetector.detect(probe))
    }

    @Test
    fun `detect returns STANDARD_188 for synthetic standard-TS probe`() {
        val probe = ByteArray(TsPacketFormatDetector.PROBE_BYTES)
        probe[0] = 0x47.toByte()
        probe[188] = 0x47.toByte()
        probe[376] = 0x47.toByte()
        assertEquals(TsPacketFormat.STANDARD_188, TsPacketFormatDetector.detect(probe))
    }

    @Test
    fun `detect returns UNKNOWN for short probe less than 576 bytes`() {
        val probe = ByteArray(100)
        assertEquals(TsPacketFormat.UNKNOWN, TsPacketFormatDetector.detect(probe))
    }

    @Test
    fun `detect returns UNKNOWN for all-zero probe`() {
        val probe = ByteArray(TsPacketFormatDetector.PROBE_BYTES)
        assertEquals(TsPacketFormat.UNKNOWN, TsPacketFormatDetector.detect(probe))
    }

    @Test
    fun `detect returns UNKNOWN when sync bytes match BD but fewer than 3 packets`() {
        val probe = ByteArray(395)
        probe[4] = 0x47.toByte()
        probe[196] = 0x47.toByte()
        assertEquals(TsPacketFormat.UNKNOWN, TsPacketFormatDetector.detect(probe))
    }
}

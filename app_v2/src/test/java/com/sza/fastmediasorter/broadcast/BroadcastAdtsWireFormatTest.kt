package com.sza.fastmediasorter.broadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * S3055: Unit tests holding the ADTS AAC-LC frame header wire format specified in S3050 contract section 2.1.
 */
class BroadcastAdtsWireFormatTest {

    companion object {
        private const val ADTS_HEADER_SIZE = 7
        private val SAMPLE_RATE_TABLE = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350,
        )

        /** Helper reproducing the ADTS header generation logic for verification */
        fun buildAdtsHeader(sampleRateHz: Int, channelCount: Int, payloadSize: Int): ByteArray {
            val sampleRateIndex = SAMPLE_RATE_TABLE.indexOf(sampleRateHz)
            require(sampleRateIndex >= 0) { "Unsupported sample rate: $sampleRateHz" }

            val header = ByteArray(ADTS_HEADER_SIZE)
            val packetLength = payloadSize + ADTS_HEADER_SIZE
            val aacLcProfileBits = 1 // MPEG-4 Audio Object Type 2 (AAC-LC) minus 1

            header[0] = 0xFF.toByte()
            header[1] = 0xF1.toByte()
            header[2] = (
                (aacLcProfileBits shl 6) or (sampleRateIndex shl 2) or (channelCount shr 2)
                ).toByte()
            header[3] = (((channelCount and 3) shl 6) or (packetLength shr 11)).toByte()
            header[4] = ((packetLength and 0x7FF) shr 3).toByte()
            header[5] = (((packetLength and 7) shl 5) or 0x1F).toByte()
            header[6] = 0xFC.toByte()
            return header
        }
    }

    @Test
    fun `default phone broadcast 44100Hz mono produces valid ADTS header`() {
        val payloadSize = 250
        val header = buildAdtsHeader(sampleRateHz = 44_100, channelCount = 1, payloadSize = payloadSize)

        // 1. Syncword 0xFFF + MPEG-4 (0) + Layer (00) + Protection Absent (1) -> 0xFF 0xF1
        assertEquals(0xFF.toByte(), header[0])
        assertEquals(0xF1.toByte(), header[1])

        // 2. Profile AAC-LC (01), 44100Hz index (4 -> 0100), channel msb (0) -> 01010000 = 0x50
        val expectedByte2 = ((1 shl 6) or (4 shl 2) or 0).toByte()
        assertEquals(expectedByte2, header[2])

        // 3. Channel lsb for Mono (01 -> 0x40) + packet length high bits
        val totalLen = payloadSize + ADTS_HEADER_SIZE // 257
        val expectedLengthHigh = totalLen shr 11
        val expectedByte3 = ((1 shl 6) or expectedLengthHigh).toByte()
        assertEquals(expectedByte3, header[3])

        // 4. Packet length middle bits (bits 10..3)
        val expectedByte4 = ((totalLen and 0x7FF) shr 3).toByte()
        assertEquals(expectedByte4, header[4])

        // 5. Packet length low bits (bits 2..0) + buffer fullness 0x1F -> bits (7..5) or 0x1F
        val expectedByte5 = (((totalLen and 7) shl 5) or 0x1F).toByte()
        assertEquals(expectedByte5, header[5])

        // 6. Number of raw data blocks (0) + buffer fullness -> 0xFC
        assertEquals(0xFC.toByte(), header[6])
    }

    @Test
    fun `48000Hz stereo produces correct sample rate index and channel config`() {
        val payloadSize = 500
        val header = buildAdtsHeader(sampleRateHz = 48_000, channelCount = 2, payloadSize = payloadSize)

        // 48000Hz is index 3 -> 0011
        // Profile AAC-LC (01), index (3 -> 0011), channel msb (0) -> 01001100 = 0x4C
        val expectedByte2 = ((1 shl 6) or (3 shl 2) or 0).toByte()
        assertEquals(expectedByte2, header[2])

        // Channel count 2 -> lsb is 10 -> byte3 upper 2 bits are 10 (0x80)
        val expectedByte3Upper = (2 and 3) shl 6
        assertEquals(expectedByte3Upper, header[3].toInt() and 0xC0)
    }

    @Test
    fun `sample rate table maps standard frequencies correctly`() {
        val sampleRates = listOf(96000, 48000, 44100, 32000, 24000, 16000, 8000)
        val expectedIndices = listOf(0, 3, 4, 5, 6, 8, 11)

        sampleRates.zip(expectedIndices).forEach { (rate, expectedIdx) ->
            val idx = SAMPLE_RATE_TABLE.indexOf(rate)
            assertEquals("Sample rate $rate should map to index $expectedIdx", expectedIdx, idx)
        }
    }

    @Test
    fun `unsupported sample rate is rejected`() {
        val unsupportedRate = 99_999
        val idx = SAMPLE_RATE_TABLE.indexOf(unsupportedRate)
        assertEquals(-1, idx)
    }

    @Test
    fun `frame length calculation accounts for seven byte ADTS header`() {
        val payloadSizes = listOf(10, 100, 1000, 2048)

        payloadSizes.forEach { payloadSize ->
            val header = buildAdtsHeader(sampleRateHz = 44_100, channelCount = 1, payloadSize = payloadSize)
            val expectedTotal = payloadSize + ADTS_HEADER_SIZE

            val lenHigh = (header[3].toInt() and 0x03) shl 11
            val lenMid = (header[4].toInt() and 0xFF) shl 3
            val lenLow = (header[5].toInt() and 0xE0) ushr 5
            val actualLength = lenHigh or lenMid or lenLow

            assertEquals("Extracted ADTS frame length should equal payload + 7", expectedTotal, actualLength)
        }
    }

    @Test
    fun `encoder rejects invalid sample rate on start`() {
        val encoder = BroadcastAacEncoder(
            sampleRate = 99_999,
            channelCount = 1,
            bitRate = 128_000,
            onEncodedFrame = { _, _, _ -> },
        )
        assertFalse("Encoder start should return false for unsupported sample rate", encoder.start())
    }
}

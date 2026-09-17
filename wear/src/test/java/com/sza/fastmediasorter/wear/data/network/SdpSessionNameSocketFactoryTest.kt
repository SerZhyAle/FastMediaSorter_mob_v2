package com.sza.fastmediasorter.wear.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class SdpSessionNameSocketFactoryTest {

    @Test
    fun normalizesRfcFallbackSessionNameWithoutChangingResponseLength() {
        val input = "v=0\r\ns= \r\nt=0 0\r\n".encodeToByteArray()

        val output = SdpSessionNameNormalizingInputStream(ByteArrayInputStream(input)).readBytes()

        assertEquals(input.size, output.size)
        assertEquals("v=0\r\ns=-\r\nt=0 0\r\n", output.decodeToString())
    }

    @Test
    fun preservesARegularSessionNameAndOtherEmptyLines() {
        val input = "i= \r\ns=Camera\r\na=recvonly\r\n".encodeToByteArray()

        val output = SdpSessionNameNormalizingInputStream(ByteArrayInputStream(input)).readBytes()

        assertEquals(input.decodeToString(), output.decodeToString())
    }
}

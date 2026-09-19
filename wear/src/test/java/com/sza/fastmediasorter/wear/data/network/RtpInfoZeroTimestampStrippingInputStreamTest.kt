package com.sza.fastmediasorter.wear.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RtpInfoZeroTimestampStrippingInputStreamTest {

    @Test
    fun stripsZeroTimestampFromEveryTrackOfThePedroPlayResponse() {
        val input = "RTSP/1.0 200 OK\r\nCSeq: 5\r\nContent-Length: 0\r\n" +
            "RTP-Info: url=rtsp://10.0.0.2:8554/streamid=0;seq=1;rtptime=0," +
            "url=rtsp://10.0.0.2:8554/streamid=1;seq=1;rtptime=0\r\nSession: 1\r\n\r\n"

        val expected = "RTSP/1.0 200 OK\r\nCSeq: 5\r\nContent-Length: 0\r\n" +
            "RTP-Info: url=rtsp://10.0.0.2:8554/streamid=0;seq=1," +
            "url=rtsp://10.0.0.2:8554/streamid=1;seq=1\r\nSession: 1\r\n\r\n"
        assertEquals(expected, readInChunks(input, CHUNK_ANY))
    }

    @Test
    fun keepsARealServersNonZeroTimestamp() {
        val input = "RTP-Info: url=rtsp://h/v;seq=232433;rtptime=0972948234,url=rtsp://h/a;seq=5;rtptime=01\r\n"

        assertEquals(input, readInChunks(input, CHUNK_ANY))
    }

    @Test
    fun leavesZeroTimestampOutsideAnRtpInfoLineUntouched() {
        val input = "X-RTP-Info: a;rtptime=0\r\nSession: 1;rtptime=0\r\n"

        assertEquals(input, readInChunks(input, CHUNK_ANY))
    }

    @Test
    fun givesTheSameResultForEveryReadSize() {
        val input = "RTP-Info: url=rtsp://h/0;seq=1;rtptime=0,url=rtsp://h/1;seq=1;rtptime=0\r\n$;rtptime"
        val reference = readInChunks(input, CHUNK_ANY)

        for (size in 1..input.length) {
            assertEquals("chunk $size", reference, readInChunks(input, size))
        }
        assertEquals(reference, readByteByByte(input))
    }

    private fun readInChunks(input: String, chunkSize: Int): String {
        val stream = RtpInfoZeroTimestampStrippingInputStream(ByteArrayInputStream(input.encodeToByteArray()))
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(chunkSize)
        while (true) {
            val count = stream.read(buffer, 0, buffer.size)
            if (count < 0) break
            out.write(buffer, 0, count)
        }
        return out.toByteArray().decodeToString()
    }

    private fun readByteByByte(input: String): String {
        val stream = RtpInfoZeroTimestampStrippingInputStream(ByteArrayInputStream(input.encodeToByteArray()))
        val out = ByteArrayOutputStream()
        while (true) {
            val value = stream.read()
            if (value < 0) break
            out.write(value)
        }
        return out.toByteArray().decodeToString()
    }

    private companion object {
        const val CHUNK_ANY = 4096
    }
}

package com.sza.fastmediasorter.broadcast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class RtspDownstreamFramerTest {

    @Test
    fun `units split across single-byte reads come out whole`() {
        val stream = setupReply + rtp(channel = 0, ssrc = SSRC) + rtp(channel = 0, ssrc = SSRC)
        val framer = RtspDownstreamFramer(OneByteAtATime(stream))

        assertArrayEquals(setupReply, framer.next())
        assertArrayEquals(rtp(channel = 0, ssrc = SSRC), framer.next())
        assertArrayEquals(rtp(channel = 0, ssrc = SSRC), framer.next())
        assertNull(framer.next())
    }

    @Test
    fun `a response with a body is one unit`() {
        val body = "v=0\r\n".toByteArray(Charsets.ISO_8859_1)
        val response = "RTSP/1.0 200 OK\r\nCSeq: 2\r\nContent-Length: ${body.size}\r\n\r\n"
            .toByteArray(Charsets.ISO_8859_1) + body
        val framer = RtspDownstreamFramer(ByteArrayInputStream(response + rtp(channel = 0, ssrc = SSRC)))

        assertArrayEquals(response, framer.next())
        assertArrayEquals(rtp(channel = 0, ssrc = SSRC), framer.next())
    }

    @Test
    fun `the BYE names the RTP stream's SSRC on the RTCP channel of its SETUP reply`() {
        val framer = RtspDownstreamFramer(ByteArrayInputStream(setupReply + rtp(channel = 0, ssrc = SSRC)))
        framer.next()
        framer.next()

        val bye = framer.byeFrames().single()

        val ssrc = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val expected = byteArrayOf('$'.code.toByte(), 1, 0, BYE_LENGTH) +
            byteArrayOf(RR_FIRST, RR_TYPE, 0, 1) + ssrc +
            byteArrayOf(BYE_FIRST, BYE_TYPE, 0, 1) + ssrc
        assertArrayEquals(expected, bye)
    }

    @Test
    fun `a sender report names the SSRC until an RTP packet arrives`() {
        val framer = RtspDownstreamFramer(ByteArrayInputStream(setupReply + senderReport(channel = 1, ssrc = SR_SSRC)))
        framer.next()
        framer.next()

        val bye = framer.byeFrames().single()

        assertEquals(1, bye[1].toInt())
        assertArrayEquals(byteArrayOf(0x55, 0x66, 0x77, 0x08), bye.copyOfRange(SSRC_AT, SSRC_AT + 4))
    }

    @Test
    fun `no SETUP reply means no BYE`() {
        val framer = RtspDownstreamFramer(ByteArrayInputStream(rtp(channel = 0, ssrc = SSRC)))
        framer.next()

        assertTrue(framer.byeFrames().isEmpty())
    }

    @Test(expected = IOException::class)
    fun `a truncated frame ends the stream with an error`() {
        RtspDownstreamFramer(ByteArrayInputStream(rtp(channel = 0, ssrc = SSRC).copyOf(TRUNCATED_BYTES))).next()
    }

    private val setupReply = (
        "RTSP/1.0 200 OK\r\nCSeq: 3\r\n" +
            "Transport: RTP/AVP/TCP;unicast;interleaved=0-1;mode=play\r\nSession: 1185d20035702ca\r\n\r\n"
        ).toByteArray(Charsets.ISO_8859_1)

    private fun rtp(channel: Int, ssrc: Int): ByteArray {
        val body = ByteArray(RTP_BODY_BYTES).also {
            it[0] = RTP_FIRST
            it[1] = RTP_PAYLOAD_TYPE
            writeInt(it, RTP_SSRC_AT, ssrc)
        }
        return byteArrayOf('$'.code.toByte(), channel.toByte(), 0, RTP_BODY_BYTES.toByte()) + body
    }

    private fun senderReport(channel: Int, ssrc: Int): ByteArray {
        val body = ByteArray(SR_BODY_BYTES).also {
            it[0] = RR_FIRST
            it[1] = SR_TYPE
            writeInt(it, SR_SSRC_AT, ssrc)
        }
        return byteArrayOf('$'.code.toByte(), channel.toByte(), 0, SR_BODY_BYTES.toByte()) + body
    }

    private fun writeInt(into: ByteArray, at: Int, value: Int) {
        for (index in 0 until INT_BYTES) {
            into[at + index] = (value ushr ((INT_BYTES - 1 - index) * BITS)).toByte()
        }
    }

    private class OneByteAtATime(bytes: ByteArray) : InputStream() {
        private val inner = ByteArrayInputStream(bytes)

        override fun read(): Int = inner.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = if (len == 0) 0 else inner.read(b, off, 1)
    }

    private companion object {
        const val SSRC = 0x11223344
        const val SR_SSRC = 0x55667708
        const val RTP_BODY_BYTES = 20
        const val SR_BODY_BYTES = 28
        const val RTP_SSRC_AT = 8
        const val SR_SSRC_AT = 4
        const val SSRC_AT = 8
        const val INT_BYTES = 4
        const val BITS = 8
        const val TRUNCATED_BYTES = 10
        const val RTP_FIRST: Byte = 0x80.toByte()
        const val RTP_PAYLOAD_TYPE: Byte = 96
        const val RR_FIRST: Byte = 0x80.toByte()
        const val BYE_FIRST: Byte = 0x81.toByte()
        const val RR_TYPE: Byte = 201.toByte()
        const val BYE_TYPE: Byte = 203.toByte()
        const val SR_TYPE: Byte = 200.toByte()
        const val BYE_LENGTH: Byte = 16
    }
}

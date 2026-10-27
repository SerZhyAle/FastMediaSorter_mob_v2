package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.broadcast.RtspAuthGate.Companion.readNBytesCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * What the library's RTSP server sends one client, read as whole units: an interleaved frame (`$`, channel, 16-bit
 * length, body) or an RTSP response (its head up to the empty line, then the body its `Content-Length` names). The
 * gate writes each unit whole, so anything else it writes to the client lands between two units, never inside one.
 *
 * On the way it learns what an RTCP BYE needs (contract LIVE-BROADCAST item T, RFC 3550 section 6.6): the RTCP
 * channel of every RTP stream from the `interleaved=a-b` of each `SETUP` reply, and that stream's SSRC from its
 * first RTP packet, or from a sender report on the RTCP channel until one arrives. The library sends no BYE itself.
 */
internal class RtspDownstreamFramer(private val input: InputStream) {

    private val lock = Any()

    // RTCP channel -> its RTP channel, and RTCP channel -> the SSRC a BYE on it names.
    private val rtpChannelOf = mutableMapOf<Int, Int>()
    private val ssrcOf = mutableMapOf<Int, Int>()
    private val fromRtp = mutableSetOf<Int>()

    // next() has one reader, the gate's downstream pipe, so one buffer stands in for a header array per packet.
    private val frameHeader = ByteArray(INTERLEAVED_REST_BYTES)

    /** The next whole unit, or null at the end of the stream; a truncated or oversized unit throws. */
    fun next(): ByteArray? {
        val first = input.read()
        return when {
            first < 0 -> null
            first == RtspAuthGate.INTERLEAVED_MARKER -> interleaved()
            else -> response(first)
        }
    }

    /**
     * One interleaved frame per RTCP channel whose SSRC is known, each a compound RTCP packet: an empty receiver
     * report, which RFC 3550 section 6.1 puts first in every compound packet, then the BYE. A channel whose SSRC
     * never showed gets none, since a consumer cannot match a BYE to a stream it did not name.
     */
    fun byeFrames(): List<ByteArray> = synchronized(lock) {
        ssrcOf.map { (channel, ssrc) ->
            ByteArrayOutputStream(INTERLEAVED_REST_BYTES + 1 + BYE_COMPOUND_BYTES).apply {
                write(RtspAuthGate.INTERLEAVED_MARKER)
                write(channel)
                write(0)
                write(BYE_COMPOUND_BYTES)
                writeRtcp(RTCP_RR_FIRST_BYTE, RTCP_PT_RR, ssrc)
                writeRtcp(RTCP_BYE_FIRST_BYTE, RTCP_PT_BYE, ssrc)
            }.toByteArray()
        }
    }

    // One two-word RTCP packet: its first byte, the payload type, a length of one word past the first, the SSRC.
    private fun ByteArrayOutputStream.writeRtcp(firstByte: Int, payloadType: Int, ssrc: Int) {
        write(firstByte)
        write(payloadType)
        write(0)
        write(RTCP_ONE_WORD_LENGTH)
        for (index in INT_BYTES - 1 downTo 0) write(ssrc ushr (index * BITS_PER_BYTE))
    }

    // Runs once per RTP/RTCP packet of a live relay: the unit is allocated once and the body is read straight into it.
    private fun interleaved(): ByteArray {
        if (!input.readFully(frameHeader, 0, INTERLEAVED_REST_BYTES)) malformed("truncated frame")
        val channel = frameHeader[0].toInt() and BYTE_MASK
        val length = ((frameHeader[1].toInt() and BYTE_MASK) shl BITS_PER_BYTE) or
            (frameHeader[2].toInt() and BYTE_MASK)
        val frame = ByteArray(FRAME_PREFIX_BYTES + length)
        frame[0] = RtspAuthGate.INTERLEAVED_MARKER.toByte()
        frameHeader.copyInto(frame, 1)
        if (!input.readFully(frame, FRAME_PREFIX_BYTES, length)) malformed("truncated frame")
        learnSsrc(channel, frame)
        return frame
    }

    private fun InputStream.readFully(into: ByteArray, offset: Int, count: Int): Boolean {
        var filled = 0
        while (filled < count) {
            val read = read(into, offset + filled, count - filled)
            if (read < 0) return false
            filled += read
        }
        return true
    }

    private fun response(first: Int): ByteArray {
        val head = ByteArrayOutputStream().apply { write(first) }
        var line = ByteArrayOutputStream().apply { write(first) }
        var contentLength = 0
        var ended = false
        while (!ended) {
            val next = input.read()
            if (next < 0) malformed("truncated response head")
            head.write(next)
            if (head.size() > RtspAuthGate.MAX_HEAD_BYTES) malformed("response head too large")
            if (next == RtspAuthGate.LF) {
                val text = line.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
                ended = text.isEmpty()
                contentLength = headerValue(text, CONTENT_LENGTH)?.toIntOrNull() ?: contentLength
                headerValue(text, TRANSPORT)?.let(::learnChannels)
                line = ByteArrayOutputStream()
            } else {
                line.write(next)
            }
        }
        if (contentLength !in 0..RtspAuthGate.MAX_HEAD_BYTES) malformed("bad content length")
        val body = input.readNBytesCompat(contentLength) ?: malformed("truncated response body")
        return head.toByteArray() + body
    }

    private fun malformed(reason: String): Nothing = throw IOException(reason)

    private fun learnChannels(transport: String) {
        val match = INTERLEAVED_PAIR.find(transport) ?: return
        val rtp = match.groupValues[1].toInt()
        val rtcp = match.groupValues[2].toInt()
        synchronized(lock) { rtpChannelOf[rtcp] = rtp }
    }

    // The body is the part of [frame] after its prefix; it is read in place so no second copy of it exists.
    private fun learnSsrc(channel: Int, frame: ByteArray) = synchronized(lock) {
        val bodySize = frame.size - FRAME_PREFIX_BYTES
        val rtcpOfRtp = rtpChannelOf.entries.firstOrNull { it.value == channel }?.key
        when {
            rtcpOfRtp != null && rtcpOfRtp !in fromRtp && bodySize >= RTP_HEADER_BYTES -> {
                ssrcOf[rtcpOfRtp] = readInt(frame, FRAME_PREFIX_BYTES + RTP_SSRC_OFFSET)
                fromRtp += rtcpOfRtp
            }
            channel in rtpChannelOf && channel !in ssrcOf && bodySize >= RTCP_SR_MIN_BYTES &&
                (frame[FRAME_PREFIX_BYTES + 1].toInt() and BYTE_MASK) == RTCP_PT_SR ->
                ssrcOf[channel] = readInt(frame, FRAME_PREFIX_BYTES + RTCP_SSRC_OFFSET)
        }
    }

    private fun headerValue(line: String, name: String): String? {
        val colon = line.indexOf(':')
        val named = colon > 0 && line.substring(0, colon).trim().equals(name, ignoreCase = true)
        return if (named) line.substring(colon + 1).trim() else null
    }

    private fun readInt(bytes: ByteArray, at: Int): Int = (0 until INT_BYTES).fold(0) { value, index ->
        (value shl BITS_PER_BYTE) or (bytes[at + index].toInt() and BYTE_MASK)
    }

    internal companion object {
        const val CONTENT_LENGTH = "content-length"
        const val TRANSPORT = "transport"
        val INTERLEAVED_PAIR = Regex("""interleaved=(\d+)-(\d+)""")
        const val INTERLEAVED_REST_BYTES = 3

        // The marker byte plus channel and 16-bit length: the body starts here in an interleaved unit.
        const val FRAME_PREFIX_BYTES = 1 + INTERLEAVED_REST_BYTES
        const val RTP_HEADER_BYTES = 12
        const val RTP_SSRC_OFFSET = 8
        const val RTCP_SR_MIN_BYTES = 8
        const val RTCP_SSRC_OFFSET = 4
        const val RTCP_PT_SR = 200
        const val RTCP_PT_RR = 201
        const val RTCP_PT_BYE = 203

        // V=2 with a zero report count, and V=2 with a source count of one.
        const val RTCP_RR_FIRST_BYTE = 0x80
        const val RTCP_BYE_FIRST_BYTE = 0x81

        // Each packet is two 32-bit words; RTCP's length field counts words minus one.
        const val RTCP_ONE_WORD_LENGTH = 1
        const val BYE_COMPOUND_BYTES = 16
        const val BYTE_MASK = 0xFF
        const val BITS_PER_BYTE = 8
        const val INT_BYTES = 4
    }
}

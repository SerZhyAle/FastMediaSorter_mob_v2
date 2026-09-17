package com.sza.fastmediasorter.wear.data.network

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Removes a constant `;rtptime=0` from the `RTP-Info` header of an RTSP PLAY response (S3210).
 *
 * The phone's RTSP server (pedroSG94 RTSP-Server) announces `rtptime=0` for every track while its
 * packets carry encoder-relative timestamps. Media3 bases each track on whichever arrives first, the
 * header or the first packet, so audio and video end up on different bases and playback stalls.
 * Without `rtptime` both tracks count from their own first packet. A non-zero value is left alone:
 * a real server's value is what Media3 needs to resume after a seek.
 *
 * Only a line starting with `RTP-Info:` is rewritten, because interleaved binary RTP frames share
 * this TCP connection and a removed byte there would break their length framing.
 */
internal class RtpInfoZeroTimestampStrippingInputStream(
    source: InputStream,
) : FilterInputStream(source) {

    private val ready = ArrayDeque<Int>()
    private val chunk = ByteArray(CHUNK_SIZE)
    private var linePrefixMatched = 0
    private var patternHeld = 0
    private var endOfStream = false

    override fun read(): Int {
        fill(1)
        return ready.removeFirstOrNull() ?: END_OF_STREAM
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        fill(length)
        val count = minOf(length, ready.size)
        repeat(count) { index -> bytes[offset + index] = ready.removeFirst().toByte() }
        return if (count == 0) END_OF_STREAM else count
    }

    override fun available(): Int = ready.size + super.available()

    override fun markSupported(): Boolean = false

    private fun fill(wanted: Int) {
        while (ready.isEmpty() && !endOfStream) {
            val count = super.read(chunk, 0, minOf(wanted, chunk.size))
            if (count == END_OF_STREAM) {
                endOfStream = true
                releaseHeldPattern()
            }
            for (index in 0 until count) {
                accept(chunk[index].toInt() and BYTE_MASK)
            }
        }
    }

    private fun accept(value: Int) {
        if (linePrefixMatched == RTP_INFO_PREFIX.size) {
            acceptInsideRtpInfo(value)
        } else {
            trackLinePrefix(value)
            ready.addLast(value)
        }
    }

    private fun acceptInsideRtpInfo(value: Int) {
        when {
            patternHeld == ZERO_TIMESTAMP.size && isEntryEnd(value) -> {
                patternHeld = 0
                emitInsideRtpInfo(value)
            }
            patternHeld < ZERO_TIMESTAMP.size && value == ZERO_TIMESTAMP[patternHeld] -> patternHeld++
            else -> {
                releaseHeldPattern()
                if (value == ZERO_TIMESTAMP[0]) patternHeld = 1 else emitInsideRtpInfo(value)
            }
        }
    }

    private fun emitInsideRtpInfo(value: Int) {
        ready.addLast(value)
        if (isLineBreak(value)) linePrefixMatched = 0
    }

    private fun trackLinePrefix(value: Int) {
        linePrefixMatched = when {
            isLineBreak(value) -> 0
            linePrefixMatched in RTP_INFO_PREFIX.indices && value == RTP_INFO_PREFIX[linePrefixMatched] ->
                linePrefixMatched + 1
            else -> NOT_RTP_INFO_LINE
        }
    }

    private fun releaseHeldPattern() {
        repeat(patternHeld) { index -> ready.addLast(ZERO_TIMESTAMP[index]) }
        patternHeld = 0
    }

    private fun isEntryEnd(value: Int): Boolean = value == COMMA || isLineBreak(value)

    private fun isLineBreak(value: Int): Boolean = value == CARRIAGE_RETURN || value == LINE_FEED

    private companion object {
        const val END_OF_STREAM = -1
        const val BYTE_MASK = 0xFF
        const val CHUNK_SIZE = 8192
        const val NOT_RTP_INFO_LINE = -1
        const val COMMA = ','.code
        const val CARRIAGE_RETURN = '\r'.code
        const val LINE_FEED = '\n'.code
        val RTP_INFO_PREFIX = "RTP-Info:".map { it.code }
        val ZERO_TIMESTAMP = ";rtptime=0".map { it.code }
    }
}

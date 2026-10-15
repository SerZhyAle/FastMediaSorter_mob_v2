package com.sza.fastmediasorter.data.security.e2e

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** How one read of a sealed relay ended (contract DEVICE-EXCHANGE 27 item DL). */
enum class E2eRelayEnd {
    /** The relay body ended - the broadcast ended or the stream dropped. */
    ENDED,

    /** A media record of an epoch this listener holds no key for: reconnect once for the current header chunk. */
    UNKNOWN_EPOCH,

    /** A tag failed or no ring entry carries this listener's group key: stop with `sealed-failed`, play nothing. */
    FAILED,
}

/**
 * The listener side of a sealed relay, shared by the phone and the watch: reads records from the relay body by
 * `len`, opens the key ring with [groupKey], and writes the clear media to the player. The listen body carries
 * chunk payloads without their flag byte, so a media record is opened under the header flag when it follows a key
 * ring, then under the joinable flag, then the plain one - a wrong flag fails its tag and costs one more check.
 */
class E2eRelayListener(private val broadcastId: String, private val groupKey: ByteArray) {

    private val epochKeys = mutableMapOf<Long, ByteArray>()

    /** The true media type from the first ring, which the player is told instead of the relay's sealed type. */
    var mediaType: String? = null
        private set

    /** Reads [body] until it ends; [onMediaType] runs once, before the first clear byte reaches [player]. */
    fun pump(body: InputStream, player: OutputStream, onMediaType: (String) -> Unit): E2eRelayEnd {
        val reader = E2eRelayRecords.Reader(body)
        var afterRing = false
        var end: E2eRelayEnd? = null
        while (end == null) {
            // A record cut short throws: the stream dropped mid-record, which the caller handles as any lost stream.
            val record = reader.next()
            end = when (record?.type) {
                null -> E2eRelayEnd.ENDED
                E2eRelayRecords.TYPE_KEY_RING -> ring(record.body, onMediaType).also { afterRing = it == null }
                E2eRelayRecords.TYPE_MEDIA -> media(record.body, afterRing, player).also { afterRing = false }
                else -> null
            }
        }
        return end
    }

    private fun ring(body: ByteArray, onMediaType: (String) -> Unit): E2eRelayEnd? {
        val ring = E2eRelayRecords.openKeyRing(broadcastId, body, groupKey) ?: return E2eRelayEnd.FAILED
        epochKeys[ring.epoch] = ring.epochKey
        if (mediaType == null) {
            mediaType = ring.mediaType
            onMediaType(ring.mediaType)
        }
        return null
    }

    private fun media(body: ByteArray, afterRing: Boolean, player: OutputStream): E2eRelayEnd? {
        val flags = if (afterRing) HEADER_FIRST else JOINABLE_FIRST
        var opened: E2eRelayMedia = E2eRelayMedia.Failed
        for (flag in flags) {
            opened = E2eRelayRecords.openMedia(broadcastId, body, flag) { epochKeys[it] }
            if (opened !is E2eRelayMedia.Failed) break
        }
        return when (opened) {
            is E2eRelayMedia.Clear -> {
                player.write(opened.bytes)
                player.flush()
                null
            }
            is E2eRelayMedia.UnknownEpoch -> E2eRelayEnd.UNKNOWN_EPOCH
            E2eRelayMedia.Failed -> E2eRelayEnd.FAILED
        }
    }

    private companion object {
        const val FLAG_PLAIN = 0x00
        const val FLAG_JOINABLE = 0x01
        const val FLAG_HEADER = 0x02
        val HEADER_FIRST = intArrayOf(FLAG_HEADER, FLAG_JOINABLE, FLAG_PLAIN)
        val JOINABLE_FIRST = intArrayOf(FLAG_JOINABLE, FLAG_PLAIN, FLAG_HEADER)
    }
}

/**
 * The few lines of HTTP/1.1 a sealed relay listener speaks over its own TLS stream (7.6): one `GET`, the status and
 * headers, and the body - de-chunked when the relay sends `Transfer-Encoding: chunked`, as it does.
 */
object E2eRelayHttp {

    private const val CR = '\r'.code
    private const val LF = '\n'.code
    private const val HTTP_OK = 200
    private const val STATUS_PARTS = 3
    private const val MAX_HEAD_BYTES = 16 * 1024
    private const val HEX_RADIX = 16

    /** [extraHeader] is one complete header line, e.g. the watch's Basic credentials, never logged; null adds none. */
    fun get(path: String, host: String, output: OutputStream, extraHeader: String? = null) {
        val extra = extraHeader?.let { "$it\r\n" }.orEmpty()
        val request = "GET $path HTTP/1.1\r\nHost: $host\r\nAccept: */*\r\n${extra}Connection: close\r\n\r\n"
        output.write(request.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    /** Reads a player's request head up to its blank line; the proxy serves one stream whatever was asked. */
    fun skipRequest(input: InputStream) {
        var header = line(input)
        while (header.isNotEmpty()) header = line(input)
    }

    /** The body of a `200` answer; throws for any other status, so the caller tries the next endpoint. */
    fun body(input: InputStream): InputStream {
        val status = line(input)
        val code = status.split(' ', limit = STATUS_PARTS).getOrNull(1)?.toIntOrNull()
        if (code != HTTP_OK) throw IOException("relay answered $code")
        var chunked = false
        var header = line(input)
        while (header.isNotEmpty()) {
            if (header.lowercase().replace(" ", "") == "transfer-encoding:chunked") chunked = true
            header = line(input)
        }
        return if (chunked) Dechunked(input) else input
    }

    internal fun line(input: InputStream): String {
        val out = ByteArrayOutputStream()
        var byte = input.read()
        while (byte >= 0 && byte != LF) {
            if (byte != CR) out.write(byte)
            if (out.size() > MAX_HEAD_BYTES) throw IOException("relay header line too long")
            byte = input.read()
        }
        if (byte < 0 && out.size() == 0) throw EOFException("relay closed before its answer")
        return out.toString(Charsets.US_ASCII.name())
    }

    private class Dechunked(input: InputStream) : FilterInputStream(input) {
        private var left = 0L
        private var done = false
        private var started = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and BYTE_MASK
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (left == 0L && !done) nextChunk()
            return if (done) {
                -1
            } else {
                val read = super.read(buffer, offset, minOf(length.toLong(), left).toInt())
                if (read < 0) throw EOFException("relay chunk cut short")
                left -= read
                read
            }
        }

        private fun nextChunk() {
            if (left == 0L && started) line(`in`)
            started = true
            left = line(`in`).substringBefore(';').trim().toLongOrNull(HEX_RADIX)
                ?: throw IOException("relay chunk size unreadable")
            done = left == 0L
        }

        private companion object {
            const val BYTE_MASK = 0xFF
        }
    }
}

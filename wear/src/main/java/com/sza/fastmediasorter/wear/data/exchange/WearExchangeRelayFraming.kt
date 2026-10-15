package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Turns the ADTS payloads of one upload stream into the sealed records of contract DEVICE-EXCHANGE 27 item DL,
 * mirrored from the phone's `ExchangeRelaySealer`: the header chunk is the key ring of [ring]'s epoch alone, since
 * ADTS has no format header, and every other payload becomes one media record under its chunk's flag.
 */
class WearExchangeRelaySealer(
    private val broadcastId: String,
    private val mediaType: String,
    private val ring: WearExchangeRelayRing,
    private val isCurrent: () -> Boolean = { true },
) {

    fun headerChunk(): ByteArray =
        E2eRelayRecords.encodeKeyRing(broadcastId, ring.epoch, mediaType, ring.epochKey, listOf(ring.groupKey))

    /**
     * One media record. Throws once the group key was replaced (an own device left the account), which ends this
     * upload stream; the uploader reconnects with a replacement upload and a new epoch (item DL).
     */
    fun media(payload: ByteArray, flag: Int): ByteArray {
        if (!isCurrent()) throw IOException("the relay epoch was replaced")
        return E2eRelayRecords.encodeMedia(broadcastId, ring.epoch, ring.epochKey, flag, payload)
    }
}

/**
 * The upload chunk framing of contract DEVICE-EXCHANGE 7.6, mirrored from the phone's `ExchangeRelayChunkWriter`:
 * a 4-byte unsigned big-endian length counting one flag byte plus the payload, the flag byte, the payload; a zero
 * length ends the broadcast. A sealed upload opens every stream with [header], the key ring (27 item DL).
 */
class WearExchangeRelayChunkWriter(
    private val out: OutputStream,
    private val header: ByteArray? = null,
    private val sealer: WearExchangeRelaySealer? = null,
) {

    private var opened = false

    /** Writes [payload] as one chunk; the first chunk of the stream is preceded by the header chunk. */
    fun write(payload: ByteArray, joinable: Boolean) {
        require(payload.size + FLAG_BYTES <= MAX_CHUNK_LENGTH) { "a chunk is at most 1 MiB with its flag byte" }
        if (!opened) {
            opened = true
            header?.let { frame(FLAG_HEADER, it) }
        }
        val flag = if (joinable) FLAG_JOINABLE else FLAG_NONE
        frame(flag, sealer?.media(payload, flag) ?: payload)
        out.flush()
    }

    private fun frame(flag: Int, payload: ByteArray) {
        val length = payload.size + FLAG_BYTES
        out.write(
            byteArrayOf(
                (length ushr BYTE_3_SHIFT).toByte(),
                (length ushr BYTE_2_SHIFT).toByte(),
                (length ushr BYTE_1_SHIFT).toByte(),
                length.toByte(),
                flag.toByte(),
            )
        )
        out.write(payload)
    }

    /** A clean end of the broadcast (7.6): the relay ends every listener's response instead of timing out. */
    fun end() {
        out.write(ByteArray(LENGTH_BYTES))
        out.flush()
    }

    companion object {
        const val FLAG_NONE = 0x00
        const val FLAG_JOINABLE = 0x01
        const val FLAG_HEADER = 0x02
        const val MAX_CHUNK_LENGTH = 1_048_576
        private const val FLAG_BYTES = 1
        private const val LENGTH_BYTES = 4
        private const val BYTE_3_SHIFT = 24
        private const val BYTE_2_SHIFT = 16
        private const val BYTE_1_SHIFT = 8
    }
}

/**
 * Cuts a byte stream of ADTS AAC into whole frames (7.6: for `AUDIO_ONLY` every chunk is whole frames and joinable),
 * mirrored from the phone's `AdtsFrameSplitter`. Bytes before a sync word are dropped, so a stream joined in the
 * middle of a frame starts at the next one.
 */
class WearAdtsFrameSplitter {

    private val pending = ByteArrayOutputStream()

    /** Feeds [length] bytes of [buffer] and returns every frame they complete, in order. */
    fun feed(buffer: ByteArray, length: Int): List<ByteArray> {
        pending.write(buffer, 0, length)
        val bytes = pending.toByteArray()
        val frames = mutableListOf<ByteArray>()
        var at = 0
        var waiting = false
        while (!waiting && at + HEADER_BYTES <= bytes.size) {
            if (!isSync(bytes, at)) {
                at++
                continue
            }
            val size = frameLength(bytes, at)
            when {
                size < HEADER_BYTES -> at++
                at + size > bytes.size -> waiting = true
                else -> {
                    frames += bytes.copyOfRange(at, at + size)
                    at += size
                }
            }
        }
        pending.reset()
        pending.write(bytes, at, bytes.size - at)
        return frames
    }

    private fun isSync(bytes: ByteArray, at: Int): Boolean =
        bytes[at].toInt() and BYTE_MASK == SYNC_HIGH && bytes[at + 1].toInt() and SYNC_LOW_MASK == SYNC_LOW_MASK

    private fun frameLength(bytes: ByteArray, at: Int): Int =
        (bytes[at + LENGTH_HIGH_BYTE].toInt() and LENGTH_HIGH_MASK shl LENGTH_HIGH_SHIFT) or
            (bytes[at + LENGTH_MID_BYTE].toInt() and BYTE_MASK shl LENGTH_MID_SHIFT) or
            (bytes[at + LENGTH_LOW_BYTE].toInt() and LENGTH_LOW_MASK ushr LENGTH_LOW_SHIFT)

    private companion object {
        const val HEADER_BYTES = 7
        const val LENGTH_HIGH_BYTE = 3
        const val LENGTH_MID_BYTE = 4
        const val LENGTH_LOW_BYTE = 5
        const val BYTE_MASK = 0xFF
        const val SYNC_HIGH = 0xFF
        const val SYNC_LOW_MASK = 0xF0
        const val LENGTH_HIGH_MASK = 0x03
        const val LENGTH_HIGH_SHIFT = 11
        const val LENGTH_MID_SHIFT = 3
        const val LENGTH_LOW_MASK = 0xE0
        const val LENGTH_LOW_SHIFT = 5
    }
}

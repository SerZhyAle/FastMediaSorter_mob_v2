package com.sza.fastmediasorter.data.broadcast.exchange

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * The upload chunk framing of contract DEVICE-EXCHANGE 7.6: a 4-byte unsigned big-endian length counting one flag
 * byte plus the payload, the flag byte, the payload; a zero length ends the broadcast. A header chunk, when the
 * format has one (MPEG-TS PAT and PMT), opens every upload stream, so a relay that lost its copy gets it again
 * on the next reconnect.
 */
class ExchangeRelayChunkWriter(
    private val out: OutputStream,
    private val header: ByteArray? = null,
    private val sealer: ExchangeRelaySealer? = null,
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

    /** A clean end of the broadcast (7.6): the relay ends every listener's response instead of timing out. */
    fun end() {
        out.write(ByteArray(LENGTH_BYTES))
        out.flush()
    }

    private fun frame(flags: Int, payload: ByteArray) {
        val length = payload.size + FLAG_BYTES
        out.write(
            byteArrayOf(
                (length ushr BYTE_3_SHIFT).toByte(),
                (length ushr BYTE_2_SHIFT).toByte(),
                (length ushr BYTE_1_SHIFT).toByte(),
                length.toByte(),
                flags.toByte(),
            )
        )
        out.write(payload)
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
 * Cuts a byte stream of ADTS AAC into whole frames (7.6: for `AUDIO_ONLY` every chunk is whole frames and joinable).
 * Bytes before a sync word are dropped, so a stream joined in the middle of a frame starts at the next one.
 */
class AdtsFrameSplitter {

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

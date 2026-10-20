package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eRelayMedia
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** The 7.6 upload framing and the ADTS cutter as the watch mirrors them from the phone. */
class WearExchangeRelayFramingTest {

    @Test
    fun `a chunk is the length of flag plus payload, the flag, the payload`() {
        val out = ByteArrayOutputStream()

        WearExchangeRelayChunkWriter(out).write(byteArrayOf(9, 8, 7), joinable = true)

        assertArrayEquals(byteArrayOf(0, 0, 0, 4, 1, 9, 8, 7), out.toByteArray())
    }

    @Test
    fun `a chunk that is not joinable carries a zero flag`() {
        val out = ByteArrayOutputStream()

        WearExchangeRelayChunkWriter(out).write(byteArrayOf(6), joinable = false)

        assertArrayEquals(byteArrayOf(0, 0, 0, 2, 0, 6), out.toByteArray())
    }

    @Test
    fun `a sealed upload opens with the key ring alone and seals each chunk under its flag`() {
        val groupKey = ByteArray(E2eRelayRecords.GROUP_KEY_LENGTH) { 7 }
        val ring = WearExchangeRelayRing(2L, ByteArray(E2eRelayRecords.EPOCH_KEY_LENGTH) { 1 }, groupKey)
        val sealer = WearExchangeRelaySealer(BROADCAST_ID, "audio/aac", ring)
        val out = ByteArrayOutputStream()

        WearExchangeRelayChunkWriter(out, sealer.headerChunk(), sealer).write(byteArrayOf(5, 6), joinable = true)

        val bytes = out.toByteArray()
        val headerLength = E2eBytes.readU32be(bytes, 0).toInt()
        assertEquals(WearExchangeRelayChunkWriter.FLAG_HEADER, bytes[4].toInt())
        val header = requireNotNull(E2eRelayRecords.split(bytes.copyOfRange(5, 4 + headerLength)))
        assertEquals("ADTS has no format header", listOf(E2eRelayRecords.TYPE_KEY_RING), header.map { it.type })
        val opened = requireNotNull(E2eRelayRecords.openKeyRing(BROADCAST_ID, header[0].body, groupKey))
        assertEquals("audio/aac", opened.mediaType)
        val chunkStart = 4 + headerLength
        assertEquals(WearExchangeRelayChunkWriter.FLAG_JOINABLE, bytes[chunkStart + 4].toInt())
        val media = requireNotNull(E2eRelayRecords.split(bytes.copyOfRange(chunkStart + 5, bytes.size))).single()
        val flag = WearExchangeRelayChunkWriter.FLAG_JOINABLE
        val clear = E2eRelayRecords.openMedia(BROADCAST_ID, media.body, flag) { epoch ->
            opened.epochKey.takeIf { epoch == opened.epoch }
        }
        assertArrayEquals(byteArrayOf(5, 6), (clear as E2eRelayMedia.Clear).bytes)
    }

    @Test
    fun `a zero length ends the broadcast`() {
        val out = ByteArrayOutputStream()

        WearExchangeRelayChunkWriter(out).end()

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), out.toByteArray())
    }

    @Test
    fun `a chunk above 1 MiB with its flag byte is refused`() {
        val writer = WearExchangeRelayChunkWriter(ByteArrayOutputStream())

        writer.write(ByteArray(WearExchangeRelayChunkWriter.MAX_CHUNK_LENGTH - 1), joinable = true)
        assertThrows(IllegalArgumentException::class.java) {
            writer.write(ByteArray(WearExchangeRelayChunkWriter.MAX_CHUNK_LENGTH), joinable = true)
        }
    }

    @Test
    fun `ADTS frames are cut whole across arbitrary reads and leading bytes are dropped`() {
        val first = adts(payload = 3)
        val second = adts(payload = 10)
        val stream = byteArrayOf(0x11, 0x22) + first + second
        val splitter = WearAdtsFrameSplitter()

        val frames = mutableListOf<ByteArray>()
        stream.toList().chunked(5).forEach { piece -> frames += splitter.feed(piece.toByteArray(), piece.size) }

        assertEquals(2, frames.size)
        assertArrayEquals(first, frames[0])
        assertArrayEquals(second, frames[1])
    }

    @Test
    fun `a frame still arriving is held back`() {
        val frame = adts(payload = 20)
        val splitter = WearAdtsFrameSplitter()

        assertTrue(splitter.feed(frame, frame.size - 1).isEmpty())
        assertArrayEquals(frame, splitter.feed(frame.copyOfRange(frame.size - 1, frame.size), 1).single())
    }

    // A 7-byte ADTS header without CRC, AAC-LC, 44.1 kHz, stereo, then [payload] bytes.
    private fun adts(payload: Int): ByteArray {
        val length = ADTS_HEADER + payload
        val header = byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            0x50,
            (0x80 or (length shr 11)).toByte(),
            ((length shr 3) and 0xFF).toByte(),
            (((length and 0x07) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
        return header + ByteArray(payload) { (it + 1).toByte() }
    }

    private companion object {
        const val ADTS_HEADER = 7
        const val BROADCAST_ID = "b1"
    }
}

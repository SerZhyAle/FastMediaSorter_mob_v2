package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eRelayMedia
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ExchangeRelayFramingTest {

    @Test
    fun `a chunk is the length of flag plus payload, the flag, the payload`() {
        val out = ByteArrayOutputStream()

        ExchangeRelayChunkWriter(out).write(byteArrayOf(9, 8, 7), joinable = true)

        assertArrayEquals(byteArrayOf(0, 0, 0, 4, 1, 9, 8, 7), out.toByteArray())
    }

    @Test
    fun `the header chunk opens every upload stream before its first chunk`() {
        val header = byteArrayOf(0x47, 0x40)
        repeat(2) {
            val out = ByteArrayOutputStream()
            val writer = ExchangeRelayChunkWriter(out, header)

            writer.write(byteArrayOf(5), joinable = true)
            writer.write(byteArrayOf(6), joinable = false)

            assertArrayEquals(
                byteArrayOf(0, 0, 0, 3, 2, 0x47, 0x40, 0, 0, 0, 2, 1, 5, 0, 0, 0, 2, 0, 6),
                out.toByteArray(),
            )
        }
    }

    @Test
    fun `a sealed header chunk is a key ring for the group key and the format header as a media record`() {
        val groupKey = ByteArray(E2eRelayRecords.GROUP_KEY_LENGTH) { 7 }
        val epochKey = ByteArray(E2eRelayRecords.EPOCH_KEY_LENGTH) { 1 }
        val ring = ExchangeRelayRing(epoch = 3L, epochKey = epochKey, groupKeys = listOf(groupKey))
        val sealer = ExchangeRelaySealer(BROADCAST_ID, "video/mp2t", ring)
        val out = ByteArrayOutputStream()

        ExchangeRelayChunkWriter(out, sealer.headerChunk(byteArrayOf(0x47, 0x40)), sealer)
            .write(byteArrayOf(5), joinable = true)

        val bytes = out.toByteArray()
        val headerLength = E2eBytes.readU32be(bytes, 0).toInt()
        assertEquals(ExchangeRelayChunkWriter.FLAG_HEADER, bytes[4].toInt())
        val records = requireNotNull(E2eRelayRecords.split(bytes.copyOfRange(5, 4 + headerLength)))
        assertEquals(listOf(E2eRelayRecords.TYPE_KEY_RING, E2eRelayRecords.TYPE_MEDIA), records.map { it.type })
        val opened = requireNotNull(E2eRelayRecords.openKeyRing(BROADCAST_ID, records[0].body, groupKey))
        assertEquals("video/mp2t", opened.mediaType)
        val flag = ExchangeRelayChunkWriter.FLAG_HEADER
        val header = E2eRelayRecords.openMedia(BROADCAST_ID, records[1].body, flag) { epoch ->
            opened.epochKey.takeIf { epoch == opened.epoch }
        }
        assertArrayEquals(byteArrayOf(0x47, 0x40), (header as E2eRelayMedia.Clear).bytes)
    }

    @Test
    fun `a zero length ends the broadcast`() {
        val out = ByteArrayOutputStream()

        ExchangeRelayChunkWriter(out).end()

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), out.toByteArray())
    }

    @Test
    fun `a chunk above 1 MiB with its flag byte is refused`() {
        val writer = ExchangeRelayChunkWriter(ByteArrayOutputStream())

        writer.write(ByteArray(ExchangeRelayChunkWriter.MAX_CHUNK_LENGTH - 1), joinable = true)
        assertThrows(IllegalArgumentException::class.java) {
            writer.write(ByteArray(ExchangeRelayChunkWriter.MAX_CHUNK_LENGTH), joinable = true)
        }
    }

    @Test
    fun `ADTS frames are cut whole across arbitrary reads`() {
        val first = adts(payload = 3)
        val second = adts(payload = 10)
        val stream = byteArrayOf(0x11, 0x22) + first + second
        val splitter = AdtsFrameSplitter()

        val frames = mutableListOf<ByteArray>()
        stream.toList().chunked(5).forEach { piece -> frames += splitter.feed(piece.toByteArray(), piece.size) }

        assertEquals(2, frames.size)
        assertArrayEquals(first, frames[0])
        assertArrayEquals(second, frames[1])
    }

    @Test
    fun `a frame still arriving is held back`() {
        val frame = adts(payload = 20)
        val splitter = AdtsFrameSplitter()

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

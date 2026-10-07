package com.sza.fastmediasorter.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.CRC32

class PngTimeChunkTest {

    private val signature = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        return ByteBuffer.allocate(12 + data.size)
            .putInt(data.size).put(typeBytes).put(data).putInt(crc.value.toInt()).array()
    }

    private fun minimalPng(): ByteArray =
        signature + chunk("IHDR", ByteArray(13)) + chunk("IDAT", byteArrayOf(1, 2, 3)) + chunk("IEND", ByteArray(0))

    private fun millisOf(zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, 7, 23, 45, 59)
    }.timeInMillis

    @Test
    fun `inserts tIME right after IHDR with local fields and valid CRC`() {
        val zone = TimeZone.getTimeZone("Europe/Kyiv")
        val original = minimalPng()
        val result = PngTimeChunk.insertAfterIhdr(original, millisOf(zone), zone)

        val buffer = ByteBuffer.wrap(result)
        val timeOffset = 8 + 12 + 13
        assertEquals(original.size + 19, result.size)
        assertEquals(7, buffer.getInt(timeOffset))
        assertEquals("tIME", String(result, timeOffset + 4, 4, Charsets.US_ASCII))
        val dataOffset = timeOffset + 8
        assertEquals(2026, buffer.getShort(dataOffset).toInt())
        assertEquals(10, result[dataOffset + 2].toInt())
        assertEquals(7, result[dataOffset + 3].toInt())
        assertEquals(23, result[dataOffset + 4].toInt())
        assertEquals(45, result[dataOffset + 5].toInt())
        assertEquals(59, result[dataOffset + 6].toInt())

        val crc = CRC32().apply { update(result, timeOffset + 4, 11) }
        assertEquals(crc.value.toInt(), buffer.getInt(dataOffset + 7))

        assertArrayEquals(original.copyOfRange(0, timeOffset), result.copyOfRange(0, timeOffset))
        assertArrayEquals(
            original.copyOfRange(timeOffset, original.size),
            result.copyOfRange(timeOffset + 19, result.size),
        )
    }

    @Test
    fun `fields follow the given zone, not UTC`() {
        val zone = TimeZone.getTimeZone("Asia/Tokyo")
        val result = PngTimeChunk.insertAfterIhdr(minimalPng(), millisOf(zone), zone)
        assertEquals(23, result[8 + 12 + 13 + 8 + 4].toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-PNG input`() {
        PngTimeChunk.insertAfterIhdr(ByteArray(40) { 1 }, 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects PNG whose first chunk is not IHDR`() {
        PngTimeChunk.insertAfterIhdr(signature + chunk("IDAT", ByteArray(13)), 0L)
    }
}

package com.sza.fastmediasorter.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.CRC32

/**
 * CAPTURE-OUTPUT rule 16: a PNG capture carries its capture time in a `tIME` chunk placed right
 * after `IHDR`. `Bitmap.compress` offers no chunk seam, so the chunk is spliced into the encoded
 * bytes afterwards. The fields hold LOCAL wall-clock time - the same second the file name was
 * formed from (rule 3) - although the PNG specification itself recommends UTC.
 */
object PngTimeChunk {

    private const val SIGNATURE_TEXT = "\u0089PNG\r\n\u001A\n"
    private val SIGNATURE = SIGNATURE_TEXT.toByteArray(Charsets.ISO_8859_1)
    private const val IHDR = "IHDR"
    private const val TIME = "tIME"
    private const val LENGTH_SIZE = 4
    private const val TYPE_SIZE = 4
    private const val CRC_SIZE = 4
    private const val TIME_DATA_SIZE = 7
    private const val PNG_QUALITY = 100

    /** Encodes [bitmap] as PNG and writes it to [output] with a `tIME` chunk for [timestampMillis]. */
    fun writePng(bitmap: Bitmap, timestampMillis: Long, output: OutputStream) {
        val encoded = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, encoded)) {
            throw IOException("Bitmap.compress returned false")
        }
        output.write(insertAfterIhdr(encoded.toByteArray(), timestampMillis))
    }

    /**
     * Returns a copy of [png] with a `tIME` chunk inserted directly after its `IHDR` chunk.
     * Throws [IllegalArgumentException] when [png] does not start with a PNG signature and `IHDR`.
     */
    fun insertAfterIhdr(
        png: ByteArray,
        timestampMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): ByteArray {
        val ihdrEnd = ihdrEndOffset(png)
        val chunk = buildTimeChunk(timestampMillis, timeZone)
        val result = ByteArray(png.size + chunk.size)
        System.arraycopy(png, 0, result, 0, ihdrEnd)
        System.arraycopy(chunk, 0, result, ihdrEnd, chunk.size)
        System.arraycopy(png, ihdrEnd, result, ihdrEnd + chunk.size, png.size - ihdrEnd)
        return result
    }

    private fun ihdrEndOffset(png: ByteArray): Int {
        val headerSize = SIGNATURE.size + LENGTH_SIZE + TYPE_SIZE
        require(png.size >= headerSize && SIGNATURE.indices.all { png[it] == SIGNATURE[it] }) {
            "Not a PNG stream"
        }
        val buffer = ByteBuffer.wrap(png)
        val ihdrLength = buffer.getInt(SIGNATURE.size)
        val type = String(png, SIGNATURE.size + LENGTH_SIZE, TYPE_SIZE, Charsets.US_ASCII)
        val end = headerSize.toLong() + ihdrLength + CRC_SIZE
        require(type == IHDR && ihdrLength >= 0 && end <= png.size) { "PNG does not start with IHDR" }
        return end.toInt()
    }

    private fun buildTimeChunk(timestampMillis: Long, timeZone: TimeZone): ByteArray {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestampMillis }
        val typeBytes = TIME.toByteArray(Charsets.US_ASCII)
        val data = ByteBuffer.allocate(TIME_DATA_SIZE)
            .putShort(calendar.get(Calendar.YEAR).toShort())
            .put((calendar.get(Calendar.MONTH) + 1).toByte())
            .put(calendar.get(Calendar.DAY_OF_MONTH).toByte())
            .put(calendar.get(Calendar.HOUR_OF_DAY).toByte())
            .put(calendar.get(Calendar.MINUTE).toByte())
            .put(calendar.get(Calendar.SECOND).toByte())
            .array()
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        return ByteBuffer.allocate(LENGTH_SIZE + TYPE_SIZE + TIME_DATA_SIZE + CRC_SIZE)
            .putInt(TIME_DATA_SIZE)
            .put(typeBytes)
            .put(data)
            .putInt(crc.value.toInt())
            .array()
    }
}

package com.sza.fastmediasorter.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S3842 - [PdfInfoParser] reads positional windows instead of buffering the file on the heap.
 */
class PdfInfoParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class BytesReader(private val bytes: ByteArray) : PdfInfoParser.RandomReader {
        var maxRequested = 0
        override val size: Long = bytes.size.toLong()

        override fun read(position: Long, length: Int): ByteArray {
            maxRequested = maxOf(maxRequested, length)
            val start = position.toInt().coerceAtMost(bytes.size)
            return bytes.copyOfRange(start, minOf(bytes.size, start + length))
        }
    }

    private fun pdf(bodyPadding: Int = 0, padBeforeObject: Int = 0): ByteArray {
        val text = buildString {
            append("%PDF-1.7\n")
            append(" ".repeat(padBeforeObject))
            append("11 0 obj\n<< /Title (Wrong) >>\nendobj\n")
            append("1 0 obj\n<< /Title (Right) /Author (Me) /CreationDate (D:20240102030405Z) >>\nendobj\n")
            append(" ".repeat(bodyPadding))
            append("trailer\n<< /Size 12 /Info 1 0 R >>\n%%EOF\n")
        }
        return text.toByteArray(Charsets.ISO_8859_1)
    }

    @Test
    fun `reads the referenced info dict and skips an object whose number only ends with the ref`() {
        val info = PdfInfoParser.parse(BytesReader(pdf()))

        assertEquals("1.7", info.version)
        assertEquals("Right", info.title)
        assertEquals("Me", info.author)
        assertEquals("2024-01-02 03:04:05 UTC", info.creationDate)
    }

    @Test
    fun `finds an object header that straddles a scan chunk boundary`() {
        val chunk = 1024 * 1024
        val prefix = "%PDF-1.7\n11 0 obj\n<< /Title (Wrong) >>\nendobj\n".length
        // Place "1 0 obj" three bytes before the first chunk boundary.
        val info = PdfInfoParser.parse(BytesReader(pdf(padBeforeObject = chunk - 3 - prefix)))

        assertEquals("Right", info.title)
    }

    @Test
    fun `heap use stays bounded for a large file`() {
        val reader = BytesReader(pdf(bodyPadding = 20 * 1024 * 1024))

        val info = PdfInfoParser.parse(reader)

        assertEquals("Right", info.title)
        assertTrue("requested ${reader.maxRequested}", reader.maxRequested <= 1024 * 1024)
    }

    @Test
    fun `parses a file on disk`() {
        val file = tmp.newFile("doc.pdf").apply { writeBytes(pdf()) }

        val info = PdfInfoParser.parse(file)

        assertEquals("Right", info.title)
    }

    @Test
    fun `missing info reference keeps the version only`() {
        val bytes = "%PDF-1.4\n1 0 obj\n<< /Title (X) >>\nendobj\n".toByteArray(Charsets.ISO_8859_1)

        val info = PdfInfoParser.parse(BytesReader(bytes))

        assertEquals("1.4", info.version)
        assertNull(info.title)
    }
}

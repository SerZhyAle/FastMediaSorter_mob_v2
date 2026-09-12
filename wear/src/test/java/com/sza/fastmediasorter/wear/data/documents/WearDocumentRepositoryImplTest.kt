package com.sza.fastmediasorter.wear.data.documents

import com.sza.fastmediasorter.wear.domain.documents.WearDocumentContent
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFailure
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/**
 * S2532: the four outcomes of reading a document, pinned apart from one another.
 *
 * Strategic goals 4 and 5 name truncation, emptiness and a read failure as separately visible
 * states, so each needs a case asserting it did not collapse into its neighbour - a reader that
 * returned an empty string for all three would satisfy every compile check there is.
 *
 * The tests drive `readFrom` rather than `readText`: the watch module's unit suite runs on the plain
 * JVM, where `Uri` and `ContentResolver` are stubs that throw on every call.
 */
class WearDocumentRepositoryImplTest {

    private val repository = WearDocumentRepositoryImpl(mockk(relaxed = true))

    @Test
    fun `a short utf-8 file is read whole`() {
        val content = "Gruesse, Grusse and Grusse\nsecond line"
        val bytes = content.toByteArray(Charsets.UTF_8)

        val result = repository.readFrom(CAP_BYTES) { ByteArrayInputStream(bytes) }

        val text = result as WearDocumentContent.Text
        assertEquals(content, text.text)
        assertFalse(text.truncated)
        assertEquals(bytes.size.toLong(), text.totalBytes)
    }

    @Test
    fun `a file above the cap is truncated rather than refused`() {
        val bytes = "0123456789".repeat(10).toByteArray(Charsets.UTF_8)

        val result = repository.readFrom(SMALL_CAP_BYTES) { ByteArrayInputStream(bytes) }

        val text = result as WearDocumentContent.Text
        assertTrue(text.truncated)
        assertEquals(SMALL_CAP_BYTES, text.totalBytes)
        assertEquals("01234567", text.text)
    }

    @Test
    fun `an empty file is empty and not a failure`() {
        val result = repository.readFrom(CAP_BYTES) { ByteArrayInputStream(ByteArray(0)) }

        assertEquals(WearDocumentContent.Empty, result)
    }

    @Test
    fun `a missing file is reported as not found`() {
        val absent = repository.readFrom(CAP_BYTES) { null }
        val vanished = repository.readFrom(CAP_BYTES) { throw FileNotFoundException("gone") }

        assertEquals(WearDocumentContent.Failure(WearDocumentFailure.NOT_FOUND), absent)
        assertEquals(WearDocumentContent.Failure(WearDocumentFailure.NOT_FOUND), vanished)
    }

    @Test
    fun `bytes that are text in no tried encoding are refused instead of shown`() {
        // Every byte value once: too malformed for UTF-8, and ISO-8859-1 maps a quarter of it to
        // control characters, which is what tells a binary file from a Latin-1 text one.
        val bytes = ByteArray(BYTE_VALUES) { it.toByte() }

        val result = repository.readFrom(CAP_BYTES) { ByteArrayInputStream(bytes) }

        assertEquals(WearDocumentContent.Failure(WearDocumentFailure.UNREADABLE_ENCODING), result)
    }

    private companion object {
        const val CAP_BYTES = 4096L
        const val SMALL_CAP_BYTES = 8L
        const val BYTE_VALUES = 256
    }
}

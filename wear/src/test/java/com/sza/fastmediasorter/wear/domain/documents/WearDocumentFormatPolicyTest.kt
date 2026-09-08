package com.sza.fastmediasorter.wear.domain.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2532: the single answer to "does the watch render this document?".
 *
 * Worth pinning because two callers act on opposite halves of it - the router opens a reader on one
 * answer and a refusal on the other - and because the extension fallback exists for sources that
 * report no mime type at all, a case no screen makes visible until a wearer taps a file on a share.
 */
class WearDocumentFormatPolicyTest {

    private val policy = WearDocumentFormatPolicy()

    @Test
    fun `text mime types are readable`() {
        assertEquals(WearDocumentFormat.PLAIN_TEXT, policy.formatFor("text/plain", null))
        assertEquals(WearDocumentFormat.MARKDOWN, policy.formatFor("text/markdown", null))
        assertEquals(WearDocumentFormat.CSV, policy.formatFor("text/csv", null))
        assertEquals(WearDocumentFormat.JSON, policy.formatFor("application/json", null))
        assertEquals(WearDocumentFormat.XML, policy.formatFor("application/xml", null))
        assertTrue(policy.isReadable("text/plain", null))
    }

    @Test
    fun `pdf epub and office are documents the watch does not render`() {
        assertEquals(WearDocumentFormat.PDF, policy.formatFor("application/pdf", null))
        assertEquals(WearDocumentFormat.EPUB, policy.formatFor("application/epub+zip", null))
        assertEquals(WearDocumentFormat.OFFICE, policy.formatFor("application/msword", null))
        assertFalse(policy.isReadable("application/pdf", null))
        assertFalse(policy.isReadable("application/epub+zip", null))
        assertFalse(policy.isReadable("application/msword", null))
    }

    @Test
    fun `an unpinned application type is refused rather than played`() {
        assertTrue(policy.isDocument("application/zip", null))
        assertEquals(WearDocumentFormat.OTHER, policy.formatFor("application/zip", null))
    }

    @Test
    fun `an absent mime type falls back to a txt name`() {
        assertEquals(WearDocumentFormat.PLAIN_TEXT, policy.formatFor(null, "notes.txt"))
        assertTrue(policy.isReadable(null, "notes.txt"))
    }

    @Test
    fun `an absent mime type falls back to a pdf name`() {
        assertEquals(WearDocumentFormat.PDF, policy.formatFor(null, "manual.pdf"))
        assertFalse(policy.isReadable(null, "manual.pdf"))
    }

    @Test
    fun `a generic mime type falls back to the name as an absent one does`() {
        assertEquals(WearDocumentFormat.PLAIN_TEXT, policy.formatFor("application/octet-stream", "notes.txt"))
    }

    @Test
    fun `a name with no extension is not a document`() {
        assertFalse(policy.isDocument(null, "README"))
        assertFalse(policy.isReadable(null, "README"))
    }

    @Test
    fun `media files are not documents`() {
        assertFalse(policy.isDocument("audio/mpeg", "song.mp3"))
        assertFalse(policy.isDocument(null, "song.mp3"))
        assertFalse(policy.isDocument(null, null))
    }

    @Test
    fun `an unknown route token names no format rather than crashing`() {
        assertEquals(WearDocumentFormat.PDF, WearDocumentFormat.fromToken("PDF"))
        assertEquals(WearDocumentFormat.OTHER, WearDocumentFormat.fromToken("SPREADSHEET_V2"))
        assertEquals(WearDocumentFormat.OTHER, WearDocumentFormat.fromToken(null))
    }
}

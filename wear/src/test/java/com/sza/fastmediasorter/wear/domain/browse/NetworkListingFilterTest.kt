package com.sza.fastmediasorter.wear.domain.browse

import com.sza.fastmediasorter.wear.domain.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2691: the defect these assertions replace was a network listing that read no category token at
 * all, so every token with no [MediaType] of its own - documents, "all", "browse" - opened the audio
 * files of the share. A category a source offers must lead to a list filtered by that same category,
 * never by the media type substituted for a token the route could not express.
 */
class NetworkListingFilterTest {

    @Test
    fun `documents category accepts document mime types and refuses media`() {
        val documentMimes = listOf(
            "application/pdf",
            "application/epub+zip",
            "text/plain",
            "application/msword"
        )

        documentMimes.forEach { mime ->
            assertTrue(
                "the documents category refused $mime",
                accepts(BrowseCategoryCatalog.TOKEN_DOCUMENTS, mime)
            )
        }
        assertFalse(
            "the documents category admitted an audio file - the defect this ticket fixes",
            accepts(BrowseCategoryCatalog.TOKEN_DOCUMENTS, "audio/mpeg")
        )
    }

    @Test
    fun `music category refuses a document`() {
        assertTrue(accepts(BrowseCategoryCatalog.TOKEN_MUSIC, "audio/mpeg"))
        assertFalse(accepts(BrowseCategoryCatalog.TOKEN_MUSIC, "application/pdf"))
        assertFalse(accepts(BrowseCategoryCatalog.TOKEN_MUSIC, "video/mp4"))
    }

    @Test
    fun `typed categories accept only their own kind`() {
        assertTrue(accepts(BrowseCategoryCatalog.TOKEN_VIDEOS, "video/mp4"))
        assertFalse(accepts(BrowseCategoryCatalog.TOKEN_VIDEOS, "image/jpeg"))
        assertTrue(accepts(BrowseCategoryCatalog.TOKEN_PHOTOS, "image/jpeg"))
        assertFalse(accepts(BrowseCategoryCatalog.TOKEN_PHOTOS, "audio/mpeg"))
    }

    /**
     * "All" is the category that made the defect wider than the one it was reported under: on a
     * share offering every file it was filtered down to audio by the same substituted media type.
     */
    @Test
    fun `all accepts every presentable kind and nothing else`() {
        listOf("audio/mpeg", "video/mp4", "image/jpeg", "application/pdf").forEach { mime ->
            assertTrue("the all category refused $mime", accepts(BrowseCategoryCatalog.TOKEN_ALL, mime))
        }
        assertFalse(
            "a file no resolver can place reached a media list",
            accepts(BrowseCategoryCatalog.TOKEN_ALL, "application/zip")
        )
        assertFalse(
            "a directory entry with no mime type reached a media list",
            accepts(BrowseCategoryCatalog.TOKEN_ALL, null)
        )
    }

    /**
     * A route argument naming no category keeps what the network path did before this object: the
     * media type `parseMediaType` derived from it stays the filter, so a wrong route is no worse
     * than it already was.
     */
    @Test
    fun `an unknown token falls back to the media type`() {
        assertTrue(NetworkListingFilter.accepts("nonsense", "audio/mpeg", MediaType.MUSIC))
        assertFalse(NetworkListingFilter.accepts("nonsense", "audio/mpeg", MediaType.VIDEO))
        assertTrue(NetworkListingFilter.accepts(null, "video/mp4", MediaType.VIDEO))
    }

    private fun accepts(token: String, mimeType: String?): Boolean =
        NetworkListingFilter.accepts(token, mimeType, MediaType.MUSIC)
}

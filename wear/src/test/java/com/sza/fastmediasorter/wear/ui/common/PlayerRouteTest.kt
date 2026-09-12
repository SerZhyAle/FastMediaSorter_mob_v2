package com.sza.fastmediasorter.wear.ui.common

import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFormat
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2006: the branch that decides which player opens a file.
 *
 * Worth pinning because the whole defect it fixes lived in one `else`: before this ticket a document
 * fell through to the audio player, and the same `else` still carries a file whose source reported no
 * mime type at all. The two cases must stay apart, and only a test says so cheaply.
 */
class PlayerRouteTest {

    private val fileId = 42L

    @Test
    fun `pdf is refused by name of its format`() {
        assertEquals(
            WearRoutes.unsupportedFile(WearDocumentFormat.PDF),
            playerRouteFor(fileId, "application/pdf")
        )
    }

    @Test
    fun `plain text opens the reader rather than the refusal`() {
        assertEquals(WearRoutes.documentViewer(fileId), playerRouteFor(fileId, "text/plain"))
    }

    /** S2532: a share reports no mime type, so the name is the only thing that says this is text. */
    @Test
    fun `a mime-less text file is read from its name`() {
        assertEquals(
            WearRoutes.documentViewer(fileId),
            playerRouteFor(fileId, mimeType = null, fileName = "notes.txt")
        )
    }

    @Test
    fun `audio still opens the audio player`() {
        assertEquals(WearRoutes.audioPlayer(fileId), playerRouteFor(fileId, "audio/mpeg"))
    }

    @Test
    fun `an unreported mime type keeps the audio fallback`() {
        assertEquals(WearRoutes.audioPlayer(fileId), playerRouteFor(fileId, null))
    }

    @Test
    fun `image and video are unaffected`() {
        assertEquals(WearRoutes.imageViewer(fileId), playerRouteFor(fileId, "image/jpeg"))
        assertEquals(WearRoutes.videoPlayer(fileId), playerRouteFor(fileId, "video/mp4"))
    }
}

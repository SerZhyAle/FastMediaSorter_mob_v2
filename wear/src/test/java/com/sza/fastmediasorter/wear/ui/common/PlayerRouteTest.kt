package com.sza.fastmediasorter.wear.ui.common

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
    fun `pdf is refused rather than played`() {
        assertEquals(WearRoutes.UNSUPPORTED_FILE, playerRouteFor(fileId, "application/pdf"))
    }

    @Test
    fun `plain text is refused rather than played`() {
        assertEquals(WearRoutes.UNSUPPORTED_FILE, playerRouteFor(fileId, "text/plain"))
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

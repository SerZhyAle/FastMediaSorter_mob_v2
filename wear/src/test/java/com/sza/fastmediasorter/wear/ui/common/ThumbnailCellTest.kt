package com.sza.fastmediasorter.wear.ui.common

import android.graphics.Bitmap
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.browse.captionOverCover
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The caption-placement rule of the watch file browser, read without a composition.
 *
 * The grid asks two questions in sequence - is this file audio, and has its picture resolved - and
 * only the pair decides where the name is drawn, so both are exercised here together.
 */
class ThumbnailCellTest {

    private val ready = WearThumbnail.Ready(mockk<Bitmap>(relaxed = true))

    @Test
    fun `ready audio cover carries the caption`() {
        val layout = CellCaption(overGroupIcon = true, overReadyPicture = captionOverCover("audio/mpeg"))

        assertTrue(layout.overlaysPicture(ready))
    }

    @Test
    fun `ready non-audio thumbnail keeps the caption below it`() {
        val layout = CellCaption(overGroupIcon = true, overReadyPicture = captionOverCover("image/jpeg"))

        assertFalse(layout.overlaysPicture(ready))
    }

    @Test
    fun `audio without a cover keeps the caption on its group glyph`() {
        val layout = CellCaption(overGroupIcon = true, overReadyPicture = captionOverCover("audio/mpeg"))

        assertTrue(layout.overlaysPicture(WearThumbnail.Loading))
        assertTrue(layout.overlaysPicture(WearThumbnail.Unavailable))
    }

    @Test
    fun `a screen that asked for neither overlay keeps every caption below the cell`() {
        val layout = CellCaption()

        assertFalse(layout.overlaysPicture(ready))
        assertFalse(layout.overlaysPicture(WearThumbnail.Unavailable))
    }

    @Test
    fun `a file with no mime type is not treated as audio`() {
        assertFalse(captionOverCover(null))
    }
}

package com.sza.fastmediasorter.ui.player.views

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1704: the translation plate colour must be sampled from the point actually under
 * the plate. Bounding boxes are measured on the down-scaled OCR bitmap, while
 * [TranslationOverlayView.setSourceBitmap] holds the full-resolution original, so the
 * OCR coordinates must be scaled into source-bitmap space before sampling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslationOverlayViewTest {

    private fun newView(): TranslationOverlayView =
        TranslationOverlayView(RuntimeEnvironment.getApplication())

    private fun bitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    @Test
    fun `ocr point maps to source when source is larger`() {
        val view = newView()
        // OCR bitmap 1000x1000, source (original) 2000x2000 -> 2x scale
        view.setOriginalImageSize(1000, 1000)
        val source = bitmap(2000, 2000)
        view.setSourceBitmap(source)

        // Top-left of a box at OCR (10, 20) must sample at source (20, 40)
        val (x, y) = view.ocrPointToSource(10, 20, source)
        assertEquals(20, x)
        assertEquals(40, y)
    }

    @Test
    fun `ocr point unchanged when source and ocr bitmap are same size`() {
        val view = newView()
        view.setOriginalImageSize(800, 600)
        val source = bitmap(800, 600)
        view.setSourceBitmap(source)

        val (x, y) = view.ocrPointToSource(10, 20, source)
        assertEquals(10, x)
        assertEquals(20, y)
    }

    @Test
    fun `ocr point falls back to source size when original image size not set`() {
        val view = newView()
        // originalImageWidth/Height left at 0 -> treat OCR space as source space
        val source = bitmap(500, 500)
        view.setSourceBitmap(source)

        val (x, y) = view.ocrPointToSource(7, 9, source)
        assertEquals(7, x)
        assertEquals(9, y)
    }

    @Test
    fun `ocr point is clamped to source bounds`() {
        val view = newView()
        view.setOriginalImageSize(100, 100)
        val source = bitmap(200, 200)
        view.setSourceBitmap(source)

        // OCR coordinate near the edge scales past the source edge and must clamp
        val (x, y) = view.ocrPointToSource(150, 150, source)
        assertEquals(199, x)
        assertEquals(199, y)
    }

    /** S1711: a carried type size replaces the box height as the source of the automatic font size. */
    @Test
    fun `carried type size is used instead of the box height`() {
        val view = newView()
        view.setOriginalImageSize(1000, 1000)
        view.updateImageDisplayRect(RectF(0f, 0f, 1000f, 1000f))

        val stretchedByArtifact = block(boxHeight = 90, typeSizePx = 20)

        assertEquals(20f, view.autoTextSizeSourcePx(stretchedByArtifact, scaledBoxHeightPx = 90f), 0.01f)
    }

    /** S1711: without a carried value the view keeps deriving the size from the box, as it always did. */
    @Test
    fun `no carried type size falls back to the scaled box height`() {
        val view = newView()
        view.setOriginalImageSize(1000, 1000)
        view.updateImageDisplayRect(RectF(0f, 0f, 1000f, 1000f))

        val withoutWords = block(boxHeight = 90, typeSizePx = null)

        assertEquals(90f, view.autoTextSizeSourcePx(withoutWords, scaledBoxHeightPx = 90f), 0.01f)
    }

    /**
     * S2064 & S1714: `samplePlateColors` populates both `backgroundColor` and `textColor`
     * with opaque median colors and sets fully opaque alpha.
     */
    @Test
    fun `sampled background stays fully opaque regardless of the source pixel`() {
        val view = newView()
        val source = bitmap(10, 10)
        source.setPixel(0, 0, Color.argb(120, 10, 20, 30))
        view.setSourceBitmap(source)

        val translatedBlock = block(boxHeight = 10, typeSizePx = null)
        view.setTranslatedBlocks(listOf(translatedBlock))

        assertEquals(255, Color.alpha(translatedBlock.backgroundColor))
        assertEquals(255, Color.alpha(translatedBlock.textColor))
    }

    /** S1714: ocrRectToSource correctly maps and clamps OCR bounding rects to source coordinates. */
    @Test
    fun `ocr rect maps to source rect proportionally`() {
        val view = newView()
        view.setOriginalImageSize(100, 100)
        val source = bitmap(200, 200)
        view.setSourceBitmap(source)

        val mapped = view.ocrRectToSource(Rect(10, 20, 50, 60), source)
        assertEquals(Rect(20, 40, 100, 120), mapped)
    }

    private fun block(boxHeight: Int, typeSizePx: Int?): TranslationOverlayView.TranslatedBlock =
        TranslationOverlayView.TranslatedBlock(
            originalText = "source",
            translatedText = "перевод",
            boundingBox = Rect(0, 0, 300, boxHeight),
            confidence = 90f,
            typeSizePx = typeSizePx
        )

    @Test
    fun `aspect ratio preserved on non-uniform scale`() {
        val view = newView()
        // OCR bitmap 1000x500, source 2000x2000 -> scaleX=2, scaleY=4
        view.setOriginalImageSize(1000, 500)
        val source = bitmap(2000, 2000)
        view.setSourceBitmap(source)

        val (x, y) = view.ocrPointToSource(10, 10, source)
        assertEquals(20, x)
        assertEquals(40, y)
    }
}

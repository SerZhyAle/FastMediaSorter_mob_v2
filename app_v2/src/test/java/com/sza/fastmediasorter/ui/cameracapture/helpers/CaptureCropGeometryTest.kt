package com.sza.fastmediasorter.ui.cameracapture.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1920: the WYSIWYG divergence stated as numbers, on the geometry actually measured on the owner's
 * Galaxy S21 - view 1080x2400, 4:3 stream, therefore letterboxed content 1080x1440.
 *
 * The owner could not name a reproducing combination and the device sweep passed every cell it could
 * score, so this arithmetic is what stands in for a reproduction: it does not need a phone, a scene,
 * or a lens that reaches beyond its optical maximum.
 */
class CaptureCropGeometryTest {

    @Test
    fun `four by three stream letterboxes into the measured S21 view`() {
        assertEquals(CONTENT_H, CaptureCropGeometry.contentHeightIn(VIEW_W, VIEW_H, RATIO_4_3))
    }

    @Test
    fun `letterboxed view keeps everything at factor one`() {
        val (keepX, keepY) = keepOnLetterboxedView(zoomFactor = 1f)
        assertEquals(1f, keepX, TOLERANCE)
        assertEquals(1f, keepY, TOLERANCE)
    }

    @Test
    fun `letterboxed view diverges from the file crop at factor two`() {
        val (keepX, keepY) = keepOnLetterboxedView(zoomFactor = 2f)
        // The file crop keeps 1/F on BOTH axes. The viewfinder keeps 1/F horizontally and considerably
        // more vertically, so the saved photo loses scene the viewfinder showed - the reported symptom.
        assertEquals(0.5f, keepX, TOLERANCE)
        assertEquals(0.8333f, keepY, TOLERANCE)
        assertTrue("the vertical axis is where the two rules part company", keepY > keepX)
    }

    @Test
    fun `letterboxed view still shows all content while the bars are shrinking`() {
        val (_, keepY) = keepOnLetterboxedView(zoomFactor = 1.2f)
        // Below view height over content height the scaled content has not yet outgrown the view, so
        // every row is still visible while the file has already been cropped to 1/1.2.
        assertEquals(1f, keepY, TOLERANCE)
    }

    @Test
    fun `view sized to stream keeps exactly one over the factor on both axes`() {
        // The full-screen selection was already correct because content fills the view, and Phase 04
        // gives the other two selections the same property. Both axes then match what the file crop
        // writes, which is the whole point of the fix.
        for (factor in floatArrayOf(1f, 2f, 4f)) {
            val (keepX, keepY) = CaptureCropGeometry.visibleKeepFractions(
                viewWidth = CONTENT_W,
                viewHeight = CONTENT_H,
                contentWidth = CONTENT_W,
                contentHeight = CONTENT_H,
                zoomFactor = factor,
            )
            assertEquals("horizontal keep at factor $factor", 1f / factor, keepX, TOLERANCE)
            assertEquals("vertical keep at factor $factor", 1f / factor, keepY, TOLERANCE)
        }
    }

    @Test
    fun `only the clip box reading agrees with what the file crop writes`() {
        // Phase 04 puts the PreviewView inside a box of the stream's shape because a view is clipped
        // by its parent and never by itself. These are the same zoom and the same stream read against
        // the two candidate parents, and only the box answers what cropCenter wrote.
        val fileKeep = 1f / FACTOR_TWO
        val (_, screenKeepY) = keepOnLetterboxedView(zoomFactor = FACTOR_TWO)
        val (_, boxKeepY) = CaptureCropGeometry.visibleKeepFractions(
            viewWidth = CONTENT_W,
            viewHeight = CONTENT_H,
            contentWidth = CONTENT_W,
            contentHeight = CONTENT_H,
            zoomFactor = FACTOR_TWO,
        )
        assertEquals("clipped at the box, the viewfinder keeps what the file keeps", fileKeep, boxKeepY, TOLERANCE)
        assertTrue("clipped at the screen, it keeps more than the file", screenKeepY > fileKeep)
    }

    @Test
    fun `degenerate geometry keeps the whole frame rather than throwing`() {
        val (keepX, keepY) = CaptureCropGeometry.visibleKeepFractions(0, 0, 0, 0, 4f)
        assertEquals(1f, keepX, TOLERANCE)
        assertEquals(1f, keepY, TOLERANCE)
    }

    @Test
    fun `centre crop rect is centred and sized by the kept fractions`() {
        val rect = CaptureCropGeometry.centreCropRect(SENSOR_W, SENSOR_H, keepX = 0.5f, keepY = 0.5f)
        assertEquals(SENSOR_W / 4, rect.left)
        assertEquals(SENSOR_H / 4, rect.top)
        assertEquals(SENSOR_W / 2, rect.right - rect.left)
        assertEquals(SENSOR_H / 2, rect.bottom - rect.top)
    }

    @Test
    fun `factor crop divides rather than multiplying by the reciprocal`() {
        // 4032 / 3.15 truncates to 1280 while 4032 * (1 / 3.15) truncates to 1279. One pixel, on the
        // path that overwrites the photo - so the soft-zoom crop keeps its own entry point.
        val rect = CaptureCropGeometry.centreCropByFactor(SENSOR_W, SENSOR_H, AWKWARD_FACTOR)
        assertEquals((SENSOR_W / AWKWARD_FACTOR).toInt(), rect.right - rect.left)
        assertEquals((SENSOR_H / AWKWARD_FACTOR).toInt(), rect.bottom - rect.top)
    }

    @Test
    fun `ratio crop trims the short edge only and leaves a wider frame alone`() {
        val narrowed = CaptureCropGeometry.cropRectForRatio(SENSOR_W, SENSOR_H, SCREEN_RATIO)
        assertEquals("the long edge is never trimmed", SENSOR_W, narrowed.right - narrowed.left)
        assertTrue("the short edge is trimmed", narrowed.bottom - narrowed.top < SENSOR_H)

        val alreadyNarrow = CaptureCropGeometry.cropRectForRatio(SENSOR_W, SENSOR_H, RATIO_4_3)
        assertEquals(SENSOR_H, alreadyNarrow.bottom - alreadyNarrow.top)
    }

    private fun keepOnLetterboxedView(zoomFactor: Float): Pair<Float, Float> =
        CaptureCropGeometry.visibleKeepFractions(
            viewWidth = VIEW_W,
            viewHeight = VIEW_H,
            contentWidth = CONTENT_W,
            contentHeight = CONTENT_H,
            zoomFactor = zoomFactor,
        )

    private companion object {
        /** Galaxy S21 portrait viewfinder, as measured in the strategic spec. */
        const val VIEW_W = 1080
        const val VIEW_H = 2400

        /** The 4:3 stream letterboxed into that view - the band 480..1920 the sweep reported. */
        const val CONTENT_W = 1080
        const val CONTENT_H = 1440

        /** A sensor JPEG from the same device. */
        const val SENSOR_W = 4032
        const val SENSOR_H = 3024

        const val RATIO_4_3 = 4f / 3f

        /** The host screen's own shape, which the full-screen selection crops the file to. */
        const val SCREEN_RATIO = VIEW_H.toFloat() / VIEW_W.toFloat()

        /** A factor where dividing and multiplying by the reciprocal disagree by a pixel. */
        const val AWKWARD_FACTOR = 3.15f

        /** Any soft-zoom factor above 1 shows the divergence; two keeps the arithmetic readable. */
        const val FACTOR_TWO = 2f

        const val TOLERANCE = 0.001f
    }
}

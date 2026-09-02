package com.sza.fastmediasorter.domain.ocr

import com.sza.fastmediasorter.ocrbench.SyntheticScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1716: the plate rectangle is the whole of what this ticket measures, so every case below asserts
 * numeric bounds. A pixel-difference assertion is deliberately absent: strategic §6.1 measured that
 * Robolectric records draw calls here without rasterising them, so a pixel claim on this host would
 * report a silent zero rather than evidence, and §2.3 narrows the first step to rectangle axes anyway.
 *
 * The runner is Robolectric only because the phase 01 scene below carries `android.graphics.Rect`
 * boxes; the geometry under test holds no Android type at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayPlateGeometryTest {

    @Test
    fun `plate keeps the source bounds when the translation fits inside them`() {
        val bounds = OverlayPlateGeometry.plateBounds(
            source = SOURCE,
            translation = OverlayTranslationExtent(width = 200f, height = 30f, padding = PADDING),
            viewBottom = VIEW_BOTTOM,
        )

        assertEquals(SOURCE.left, bounds.left, DELTA)
        assertEquals(SOURCE.top, bounds.top, DELTA)
        assertEquals(SOURCE.left + SOURCE.width, bounds.right, DELTA)
        assertEquals(SOURCE.top + SOURCE.height, bounds.bottom, DELTA)
    }

    @Test
    fun `plate widens rightward when the translated line is wider than its source`() {
        val bounds = OverlayPlateGeometry.plateBounds(
            source = SOURCE,
            translation = OverlayTranslationExtent(width = 400f, height = 30f, padding = PADDING),
            viewBottom = VIEW_BOTTOM,
        )

        assertEquals(SOURCE.left, bounds.left, DELTA)
        assertEquals(SOURCE.left + 400f + PADDING * 2, bounds.right, DELTA)
        assertEquals(SOURCE.top + SOURCE.height, bounds.bottom, DELTA)
    }

    @Test
    fun `plate grows downward when the translated line is taller than its source`() {
        val bounds = OverlayPlateGeometry.plateBounds(
            source = SOURCE,
            translation = OverlayTranslationExtent(width = 200f, height = 120f, padding = PADDING),
            viewBottom = VIEW_BOTTOM,
        )

        assertEquals(SOURCE.top, bounds.top, DELTA)
        assertEquals(SOURCE.top + 120f + PADDING * 2, bounds.bottom, DELTA)
        assertEquals(SOURCE.left + SOURCE.width, bounds.right, DELTA)
    }

    @Test
    fun `plate stops at the view bottom instead of growing past it`() {
        val tightBottom = SOURCE.top + 100f

        val bounds = OverlayPlateGeometry.plateBounds(
            source = SOURCE,
            translation = OverlayTranslationExtent(width = 200f, height = 400f, padding = PADDING),
            viewBottom = tightBottom,
        )

        assertEquals(tightBottom, bounds.bottom, DELTA)
        assertTrue("a clamped plate still covers its own line", bounds.height >= SOURCE.height)
    }

    @Test
    fun `a phase 01 scene box feeds the calculation and is covered by its plate`() {
        val scene = SyntheticScene.lineWithTallArtifact()
        val textBox = scene.annotation.textAreas.first().box
        val source = OverlaySourceBox(
            left = textBox.left.toFloat(),
            top = textBox.top.toFloat(),
            width = textBox.width().toFloat(),
            height = textBox.height().toFloat(),
        )

        val bounds = OverlayPlateGeometry.plateBounds(
            source = source,
            translation = OverlayTranslationExtent(width = 100f, height = 20f, padding = 4f),
            viewBottom = scene.annotation.heightPx.toFloat(),
        )

        assertEquals(textBox.left.toFloat(), bounds.left, DELTA)
        assertEquals(textBox.top.toFloat(), bounds.top, DELTA)
        assertTrue("plate must cover its own text box", bounds.right >= textBox.right)
        assertTrue("plate must cover its own text box", bounds.bottom >= textBox.bottom)
    }

    private companion object {
        val SOURCE = OverlaySourceBox(left = 100f, top = 200f, width = 300f, height = 48f)
        const val PADDING = 8f
        const val VIEW_BOTTOM = 1000f
        const val DELTA = 0.001f
    }
}

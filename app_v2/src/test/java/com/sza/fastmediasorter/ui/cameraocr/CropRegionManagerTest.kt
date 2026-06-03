package com.sza.fastmediasorter.ui.cameraocr

import android.graphics.RectF
import com.sza.fastmediasorter.ui.cameraocr.helpers.CropRegionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Geometry tests for [CropRegionManager.toPixelRect]: the pure normalized-rect -> pixel-bounds
 * mapping. Robolectric supplies android.graphics.RectF / Rect so the math runs on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CropRegionManagerTest {

    @Test
    fun fullFrameRectMapsToFullBounds() {
        val r = CropRegionManager.toPixelRect(100, 200, RectF(0f, 0f, 1f, 1f))
        assertEquals(0, r.left)
        assertEquals(0, r.top)
        assertEquals(100, r.right)
        assertEquals(200, r.bottom)
    }

    @Test
    fun centeredHalfRectMapsToExpectedBounds() {
        val r = CropRegionManager.toPixelRect(100, 200, RectF(0.25f, 0.25f, 0.75f, 0.75f))
        assertEquals(25, r.left)
        assertEquals(50, r.top)
        assertEquals(75, r.right)
        assertEquals(150, r.bottom)
    }

    @Test
    fun outOfRangeValuesClampToImageBounds() {
        val r = CropRegionManager.toPixelRect(100, 200, RectF(-0.5f, -0.5f, 1.5f, 1.5f))
        assertEquals(0, r.left)
        assertEquals(0, r.top)
        assertEquals(100, r.right)
        assertEquals(200, r.bottom)
    }

    @Test
    fun degenerateRectClampsToMinimumSide() {
        val r = CropRegionManager.toPixelRect(100, 200, RectF(0.5f, 0.5f, 0.5f, 0.5f))
        assertTrue("width >= min side", r.width() >= CropRegionManager.MIN_SIDE_PX)
        assertTrue("height >= min side", r.height() >= CropRegionManager.MIN_SIDE_PX)
        assertTrue("left within bounds", r.left in 0..100)
        assertTrue("top within bounds", r.top in 0..200)
    }
}

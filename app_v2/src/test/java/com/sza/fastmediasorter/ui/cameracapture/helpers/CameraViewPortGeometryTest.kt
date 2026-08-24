package com.sza.fastmediasorter.ui.cameracapture.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1986: the crop shape must be stated the way the viewfinder is shaped, and it must come from the
 * selection rather than from the live view. Stated in landscape form, a portrait 4:3 shot was saved
 * as a landscape strip; taken from the view mid-resize, it was saved cropped to the whole screen.
 */
class CameraViewPortGeometryTest {

    @Test
    fun `every selection is taller than it is wide`() {
        val shapes = listOf(
            CameraViewPortGeometry.rationalFor(sixteenNine = false, cropsToScreen = false, 1080, 2400),
            CameraViewPortGeometry.rationalFor(sixteenNine = true, cropsToScreen = false, 1080, 2400),
            CameraViewPortGeometry.rationalFor(sixteenNine = true, cropsToScreen = true, 1080, 2400),
        )
        // The one property the first defect broke: asked in landscape form, CameraX cropped the buffer
        // to the transpose of what the user was looking at.
        shapes.forEach { assertTrue("$it must be portrait", it.first < it.second) }
    }

    @Test
    fun `a plain selection keeps its proportion and ignores the screen`() {
        assertEquals(3 to 4, CameraViewPortGeometry.rationalFor(false, cropsToScreen = false, 1080, 2400))
        assertEquals(9 to 16, CameraViewPortGeometry.rationalFor(true, cropsToScreen = false, 1080, 2400))
        // Same answer on a different screen: the second defect was letting the screen decide here.
        assertEquals(3 to 4, CameraViewPortGeometry.rationalFor(false, cropsToScreen = false, 1440, 3200))
    }

    @Test
    fun `the screen filling selection takes the screen shape`() {
        assertEquals(1080 to 2400, CameraViewPortGeometry.rationalFor(true, cropsToScreen = true, 1080, 2400))
    }

    @Test
    fun `the screen shape is normalised to portrait whichever way it arrives`() {
        assertEquals(1080 to 2400, CameraViewPortGeometry.rationalFor(true, cropsToScreen = true, 2400, 1080))
    }

    @Test
    fun `an unmeasured screen falls back to the selection rather than dividing by zero`() {
        assertEquals(9 to 16, CameraViewPortGeometry.rationalFor(true, cropsToScreen = true, 0, 0))
        assertEquals(3 to 4, CameraViewPortGeometry.rationalFor(false, cropsToScreen = true, 1080, 0))
    }
}

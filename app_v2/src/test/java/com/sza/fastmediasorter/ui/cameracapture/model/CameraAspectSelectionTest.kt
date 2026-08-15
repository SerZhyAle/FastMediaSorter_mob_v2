package com.sza.fastmediasorter.ui.cameracapture.model

import androidx.camera.core.AspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1658: the stored values of [CameraAspectSelection] are a persistence contract - they are read
 * back out of `camera_aspect_ratio`, where preferences written before this ticket already sit - so
 * the mapping is pinned by test rather than left to a future renumbering.
 */
class CameraAspectSelectionTest {

    @Test
    fun `every stored value maps back to its own entry`() {
        assertEquals(CameraAspectSelection.RATIO_4_3, CameraAspectSelection.fromStored(0))
        assertEquals(CameraAspectSelection.RATIO_16_9, CameraAspectSelection.fromStored(1))
        assertEquals(CameraAspectSelection.FULL_SCREEN, CameraAspectSelection.fromStored(2))
    }

    @Test
    fun `the first two stored values are the CameraX constants themselves`() {
        assertEquals(AspectRatio.RATIO_4_3, CameraAspectSelection.RATIO_4_3.storedValue)
        assertEquals(AspectRatio.RATIO_16_9, CameraAspectSelection.RATIO_16_9.storedValue)
    }

    @Test
    fun `an unrecognised stored value falls back to the default`() {
        assertEquals(CameraAspectSelection.RATIO_16_9, CameraAspectSelection.fromStored(-1))
        assertEquals(CameraAspectSelection.RATIO_16_9, CameraAspectSelection.fromStored(99))
        assertEquals(CameraAspectSelection.RATIO_16_9, CameraAspectSelection.DEFAULT)
    }

    @Test
    fun `full screen requests a 16-9 stream and crops to the screen`() {
        assertEquals(AspectRatio.RATIO_16_9, CameraAspectSelection.FULL_SCREEN.cameraXAspectRatio)
        assertTrue(CameraAspectSelection.FULL_SCREEN.cropsToScreen)
        assertFalse(CameraAspectSelection.RATIO_4_3.cropsToScreen)
        assertFalse(CameraAspectSelection.RATIO_16_9.cropsToScreen)
    }

    @Test
    fun `full screen is offered only when the device can deliver a 16-9 stream`() {
        val both = listOf(AspectRatio.RATIO_4_3, AspectRatio.RATIO_16_9)
        assertEquals(
            listOf(
                CameraAspectSelection.RATIO_4_3,
                CameraAspectSelection.RATIO_16_9,
                CameraAspectSelection.FULL_SCREEN,
            ),
            CameraAspectSelection.photoOptions(both),
        )
        assertEquals(
            listOf(CameraAspectSelection.RATIO_4_3),
            CameraAspectSelection.photoOptions(listOf(AspectRatio.RATIO_4_3)),
        )
    }

    @Test
    fun `video mode degrades full screen to the stream it is built from`() {
        assertEquals(
            CameraAspectSelection.RATIO_16_9,
            CameraAspectSelection.FULL_SCREEN.forMode(videoMode = true),
        )
        assertEquals(
            CameraAspectSelection.FULL_SCREEN,
            CameraAspectSelection.FULL_SCREEN.forMode(videoMode = false),
        )
        assertEquals(
            CameraAspectSelection.RATIO_4_3,
            CameraAspectSelection.RATIO_4_3.forMode(videoMode = true),
        )
    }
}

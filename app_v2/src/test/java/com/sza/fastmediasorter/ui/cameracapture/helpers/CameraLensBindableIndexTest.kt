package com.sza.fastmediasorter.ui.cameracapture.helpers

import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1479: the video pipeline is built through `VideoCapture.withOutput`, which carries no physical
 * camera id, so a sub-lens selection binds its logical parent's optics. The index must resolve to
 * that parent before the lens label and the zoom multiplier are derived from it.
 */
class CameraLensBindableIndexTest {

    private val cameraInfo = mockk<CameraInfo>(relaxed = true)

    /** Ultra-wide sub-lens of logical camera 0, that camera itself, and a separate logical camera. */
    private val lenses = listOf(
        entry(logicalId = "0", physicalId = "3"),
        entry(logicalId = "0"),
        entry(logicalId = "2"),
    )

    @Test
    fun `video mode resolves a sub-lens to its logical parent`() {
        assertEquals(1, lenses.bindableIndex(index = 0, videoMode = true))
    }

    @Test
    fun `photo mode keeps the sub-lens the user picked`() {
        assertEquals(0, lenses.bindableIndex(index = 0, videoMode = false))
    }

    @Test
    fun `a logical entry is never redirected`() {
        assertEquals(1, lenses.bindableIndex(index = 1, videoMode = true))
        assertEquals(2, lenses.bindableIndex(index = 2, videoMode = true))
    }

    @Test
    fun `a sub-lens whose parent is missing keeps the index`() {
        val orphan = listOf(entry(logicalId = "0", physicalId = "3"), entry(logicalId = "2"))
        assertEquals(0, orphan.bindableIndex(index = 0, videoMode = true))
    }

    @Test
    fun `an out-of-range index is returned unchanged`() {
        assertEquals(OUT_OF_RANGE, lenses.bindableIndex(index = OUT_OF_RANGE, videoMode = true))
    }

    private fun entry(logicalId: String, physicalId: String? = null): CameraLensEntry =
        CameraLensEntry(
            cameraInfo = cameraInfo,
            logicalCameraId = logicalId,
            physicalCameraId = physicalId,
            lensFacing = CameraSelector.LENS_FACING_BACK,
        )

    private companion object {
        const val OUT_OF_RANGE = 7
    }
}

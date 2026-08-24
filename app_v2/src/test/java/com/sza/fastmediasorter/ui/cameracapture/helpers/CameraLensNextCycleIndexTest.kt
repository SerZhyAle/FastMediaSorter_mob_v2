package com.sza.fastmediasorter.ui.cameracapture.helpers

import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1987: the switch cycle used to advance by a plain +1, which stalls for good in video mode - a
 * sub-lens of the bound logical camera resolves back to that camera, the rebind is a silent no-op,
 * and every further press repeats the same question. The cycle must skip to a lens it can land on.
 */
class CameraLensNextCycleIndexTest {

    private val cameraInfo = mockk<CameraInfo>(relaxed = true)

    /** Logical camera 0, two of its physical sub-lenses, then a separate logical camera. */
    private val lenses = listOf(
        entry(logicalId = "0"),
        entry(logicalId = "0", physicalId = "3"),
        entry(logicalId = "0", physicalId = "4"),
        entry(logicalId = "2"),
    )

    @Test
    fun `photo mode advances by one`() {
        assertEquals(1, lenses.nextCycleIndex(activeIndex = 0, videoMode = false))
        assertEquals(3, lenses.nextCycleIndex(activeIndex = 2, videoMode = false))
    }

    @Test
    fun `photo mode wraps past the end of the list`() {
        assertEquals(0, lenses.nextCycleIndex(activeIndex = 3, videoMode = false))
    }

    @Test
    fun `video mode skips a run of sub-lenses that resolve back to the bound lens`() {
        assertEquals(3, lenses.nextCycleIndex(activeIndex = 0, videoMode = true))
    }

    @Test
    fun `video mode still reaches the logical camera behind a sub-lens`() {
        assertEquals(0, lenses.nextCycleIndex(activeIndex = 3, videoMode = true))
    }

    @Test
    fun `a list with nowhere to go returns the active index`() {
        val stuck = listOf(entry(logicalId = "0"), entry(logicalId = "0", physicalId = "3"))
        assertEquals(0, stuck.nextCycleIndex(activeIndex = 0, videoMode = true))
    }

    @Test
    fun `an empty list returns the active index`() {
        assertEquals(0, emptyList<CameraLensEntry>().nextCycleIndex(activeIndex = 0, videoMode = true))
    }

    private fun entry(logicalId: String, physicalId: String? = null): CameraLensEntry =
        CameraLensEntry(
            cameraInfo = cameraInfo,
            logicalCameraId = logicalId,
            physicalCameraId = physicalId,
            lensFacing = CameraSelector.LENS_FACING_BACK,
        )
}

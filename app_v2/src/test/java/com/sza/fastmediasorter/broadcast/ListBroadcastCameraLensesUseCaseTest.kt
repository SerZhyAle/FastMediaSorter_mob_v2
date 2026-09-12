package com.sza.fastmediasorter.broadcast

import android.content.Context
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2551: the id is the only token the watch sends back on a switch, and the label key is the only
 * thing that decides whether the watch shows a translated lens name or a raw facing word - so both
 * are asserted literally rather than through the constants that produce them.
 */
class ListBroadcastCameraLensesUseCaseTest {

    private val useCase = ListBroadcastCameraLensesUseCase(mockk<Context>(relaxed = true))

    @Test
    fun `maps a back and a front logical camera to two distinct entries`() {
        val wire = useCase.toWire(
            listOf(
                entry("0", facing = CameraSelector.LENS_FACING_BACK),
                entry("1", facing = CameraSelector.LENS_FACING_FRONT)
            )
        )

        assertEquals(listOf("0", "1"), wire.lenses.map { it.id })
        assertEquals(listOf("lens_back", "lens_front"), wire.lenses.map { it.labelKey })
        assertEquals(listOf("back", "front"), wire.lenses.map { it.facing })
    }

    @Test
    fun `keeps a physical sub-lens id in its logical-slash-physical form`() {
        val wire = useCase.toWire(
            listOf(entry("0"), entry("0", physicalId = "3"))
        )

        assertTrue(wire.lenses.map { it.id }.contains("0/3"))
    }

    @Test
    fun `names the main back lens as the active one`() {
        val wire = useCase.toWire(
            listOf(entry("0"), entry("1", facing = CameraSelector.LENS_FACING_FRONT))
        )

        assertEquals("0", wire.activeLensId)
    }

    @Test
    fun `maps an empty enumeration to an empty list and no active lens`() {
        val wire = useCase.toWire(emptyList())

        assertEquals(emptyList<Any>(), wire.lenses)
        assertNull(wire.activeLensId)
    }

    @Test
    fun `gives a lens of unknown facing a key the watch will not resolve`() {
        val wire = useCase.toWire(listOf(entry("2", facing = UNKNOWN_FACING)))

        assertEquals("lens_external", wire.lenses.single().labelKey)
        assertEquals("external", wire.lenses.single().facing)
    }

    private fun entry(
        logicalId: String,
        physicalId: String? = null,
        facing: Int = CameraSelector.LENS_FACING_BACK
    ): CameraLensEntry = CameraLensEntry(
        cameraInfo = mockk<CameraInfo>(relaxed = true),
        logicalCameraId = logicalId,
        physicalCameraId = physicalId,
        lensFacing = facing
    )

    private companion object {
        /** Neither BACK nor FRONT - an external camera, or a facing a future platform adds. */
        private const val UNKNOWN_FACING = 2
    }
}

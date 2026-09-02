package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Context
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2120: a picker row the user cannot tell from its neighbour cannot express a choice. The set below
 * is the one SM-G996U1 publishes - one front sensor exposed as two logical cameras of the same focal
 * length, beside two back lenses that differ - so the focal-length fallback ties on the front pair and
 * only the entry id separates them.
 *
 * The decimal separator is locale-dependent, so the assertions read distinctness and the id suffix
 * rather than the formatted focal text.
 */
class CameraLensLabelFormatterTest {

    private val cameraInfo = mockk<CameraInfo>(relaxed = true)
    private val context = mockk<Context>().also {
        every { it.getString(any<Int>()) } answers { "res-${firstArg<Int>()}" }
    }
    private val formatter = CameraLensLabelFormatter()

    private val ultraWide = entry(id = "2", focalMm = 1.8f, multiplier = 0.6f)
    private val wide = entry(id = "0", focalMm = 5.4f, multiplier = 1f)
    private val frontMain = entry(id = "1", focalMm = 3.3f, multiplier = 1f, front = true)
    private val frontCropped = entry(id = "3", focalMm = 3.3f, multiplier = 1f, front = true)

    private val deviceSet = listOf(ultraWide, wide, frontMain, frontCropped)

    @Test
    fun `two front lenses of one focal length get different labels`() {
        val labels = deviceSet.map { formatter.label(context, it, deviceSet) }

        assertEquals(deviceSet.size, labels.toSet().size)
    }

    @Test
    fun `a tied label carries the entry id`() {
        assertTrue(formatter.label(context, frontMain, deviceSet).endsWith("(1)"))
        assertTrue(formatter.label(context, frontCropped, deviceSet).endsWith("(3)"))
    }

    @Test
    fun `a label that is already unique carries no id`() {
        assertFalse(formatter.label(context, ultraWide, deviceSet).contains("("))
        assertFalse(formatter.label(context, wide, deviceSet).contains("("))
    }

    @Test
    fun `a lens set with no collision is left untouched`() {
        val distinct = listOf(ultraWide, wide, frontMain)
        val labels = distinct.map { formatter.label(context, it, distinct) }

        assertEquals(distinct.size, labels.toSet().size)
        assertTrue(labels.none { it.contains("(") })
    }

    @Test
    fun `lenses tied on an unknown focal length still separate`() {
        val unknownFocal = listOf(
            entry(id = "1", focalMm = 0f, multiplier = 1f, front = true),
            entry(id = "3", focalMm = 0f, multiplier = 1f, front = true),
        )
        val labels = unknownFocal.map { formatter.label(context, it, unknownFocal) }

        assertEquals(unknownFocal.size, labels.toSet().size)
    }

    private fun entry(
        id: String,
        focalMm: Float,
        multiplier: Float,
        front: Boolean = false,
    ): CameraLensEntry = CameraLensEntry(
        cameraInfo = cameraInfo,
        logicalCameraId = id,
        lensFacing = if (front) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK,
        focalLengthMm = focalMm,
        equivalentMultiplier = multiplier,
    )
}

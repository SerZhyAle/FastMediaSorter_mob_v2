package com.sza.fastmediasorter.ui.cameracapture.model

import android.hardware.camera2.CameraMetadata
import android.util.Range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1262: the availability predicates behind the photo-profile menu.
 *
 * Robolectric supplies the real [Range] and [CameraMetadata] constants; nothing here binds a camera.
 * The point of the suite is that a profile the device cannot honour is never offered - the failure
 * mode being guarded against is a menu entry that selects and then quietly behaves like NORMAL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoProfileTest {

    private fun sportCapableShutter() = Range(1_000_000L, 100_000_000L)

    @Test
    fun `normal is always offered, even on a capability-free device`() {
        val bare = CameraRuntimeCapabilities()

        assertTrue(PhotoProfile.NORMAL.isAvailable(bare))
        assertEquals(listOf(PhotoProfile.NORMAL), PhotoProfile.available(bare))
    }

    @Test
    fun `night, portrait and selfie each follow their own capability flag`() {
        assertTrue(PhotoProfile.NIGHT.isAvailable(CameraRuntimeCapabilities(supportsNightMode = true)))
        assertFalse(PhotoProfile.NIGHT.isAvailable(CameraRuntimeCapabilities(supportsNightMode = false)))

        assertTrue(PhotoProfile.PORTRAIT.isAvailable(CameraRuntimeCapabilities(supportsBokehExtension = true)))
        assertFalse(PhotoProfile.PORTRAIT.isAvailable(CameraRuntimeCapabilities(supportsBokehExtension = false)))

        val withFront = CameraRuntimeCapabilities(
            availableLensFacings = listOf(CameraMetadata.LENS_FACING_BACK, CameraMetadata.LENS_FACING_FRONT),
        )
        val backOnly = CameraRuntimeCapabilities(availableLensFacings = listOf(CameraMetadata.LENS_FACING_BACK))
        assertTrue(PhotoProfile.SELFIE.isAvailable(withFront))
        assertFalse(PhotoProfile.SELFIE.isAvailable(backOnly))
    }

    @Test
    fun `macro is offered from either the main lens or a dedicated one`() {
        assertTrue(PhotoProfile.MACRO.isAvailable(CameraRuntimeCapabilities(supportsMacro = true)))
        assertTrue(PhotoProfile.MACRO.isAvailable(CameraRuntimeCapabilities(macroLensAvailable = true)))
        assertFalse(PhotoProfile.MACRO.isAvailable(CameraRuntimeCapabilities()))
    }

    @Test
    fun `sport needs manual sensor, an iso range and a short enough shutter`() {
        val ok = CameraRuntimeCapabilities(
            supportsManualSensor = true,
            isoRange = Range(50, 3200),
            shutterRangeNs = sportCapableShutter(),
        )
        assertTrue(PhotoProfile.SPORT.isAvailable(ok))

        assertFalse(PhotoProfile.SPORT.isAvailable(ok.copy(supportsManualSensor = false)))
        assertFalse(PhotoProfile.SPORT.isAvailable(ok.copy(isoRange = null)))
        assertFalse(PhotoProfile.SPORT.isAvailable(ok.copy(shutterRangeNs = null)))
    }

    @Test
    fun `sport is hidden when the sensor cannot reach the target exposure`() {
        val tooSlow = CameraRuntimeCapabilities(
            supportsManualSensor = true,
            isoRange = Range(50, 3200),
            // Lower bound above the 1/250 s target: the profile would not beat NORMAL.
            shutterRangeNs = Range(PhotoProfile.SPORT_TARGET_EXPOSURE_NS + 1, 100_000_000L),
        )

        assertFalse(PhotoProfile.SPORT.isAvailable(tooSlow))
    }

    @Test
    fun `available keeps declaration order and always starts with normal`() {
        val everything = CameraRuntimeCapabilities(
            supportsNightMode = true,
            supportsBokehExtension = true,
            availableLensFacings = listOf(CameraMetadata.LENS_FACING_FRONT),
            supportsMacro = true,
            supportsManualSensor = true,
            isoRange = Range(50, 3200),
            shutterRangeNs = sportCapableShutter(),
        )

        assertEquals(PhotoProfile.entries.toList(), PhotoProfile.available(everything))
    }
}

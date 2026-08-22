package com.sza.fastmediasorter.ui.settings.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure key->signal decision of [SettingsSearchDeviceFeatureGate]. The device-fact
 * acquisition (Context/PackageManager/Build) is thin glue; the logic worth testing is which row is
 * gated by which device signal, and that unknown keys pass.
 */
class SettingsSearchDeviceFeatureGateTest {

    @Test
    fun `suppresses PiP row when picture-in-picture unsupported`() {
        assertFalse(decide("rowEnablePip", supportsPictureInPicture = false))
    }

    @Test
    fun `keeps PiP row when picture-in-picture supported`() {
        assertTrue(decide("rowEnablePip", supportsPictureInPicture = true))
    }

    @Test
    fun `suppresses rotation rows when accelerometer absent`() {
        assertFalse(decide("rowFollowSystemRotation", hasAccelerometer = false))
        assertFalse(decide("rowFollowSystemRotationPlayer", hasAccelerometer = false))
    }

    @Test
    fun `keeps rotation rows when accelerometer present`() {
        assertTrue(decide("rowFollowSystemRotation", hasAccelerometer = true))
        assertTrue(decide("rowFollowSystemRotationPlayer", hasAccelerometer = true))
    }

    @Test
    fun `suppresses camera-OCR rows when OCR unsupported on device`() {
        assertFalse(decide("rowCameraOcrTranslationEnabled", supportsOcr = false))
        assertFalse(decide("rowCameraOcrOnly", supportsOcr = false))
    }

    @Test
    fun `keeps camera-OCR rows when OCR supported on device`() {
        assertTrue(decide("rowCameraOcrTranslationEnabled", supportsOcr = true))
        assertTrue(decide("rowCameraOcrOnly", supportsOcr = true))
    }

    @Test
    fun `suppresses in-app ocr picker rows when OCR unsupported on device`() {
        assertFalse(decide("rowOcrFontSize", supportsOcr = false))
        assertFalse(decide("rowOcrFontFamily", supportsOcr = false))
    }

    @Test
    fun `keeps in-app ocr picker rows when OCR supported on device`() {
        assertTrue(decide("rowOcrFontSize", supportsOcr = true))
        assertTrue(decide("rowOcrFontFamily", supportsOcr = true))
    }

    @Test
    fun `keeps unknown keys regardless of device signals`() {
        assertTrue(
            decide(
                key = "rowSomethingUnrelated",
                supportsPictureInPicture = false,
                hasAccelerometer = false,
                supportsOcr = false
            )
        )
    }

    private fun decide(
        key: String,
        supportsPictureInPicture: Boolean = true,
        hasAccelerometer: Boolean = true,
        supportsOcr: Boolean = true
    ): Boolean = SettingsSearchDeviceFeatureGate.decide(
        key = key,
        supportsPictureInPicture = supportsPictureInPicture,
        hasAccelerometer = hasAccelerometer,
        supportsOcr = supportsOcr
    )
}

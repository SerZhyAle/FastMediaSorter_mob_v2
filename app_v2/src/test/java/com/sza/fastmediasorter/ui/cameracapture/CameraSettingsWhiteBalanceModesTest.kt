package com.sza.fastmediasorter.ui.cameracapture

import android.hardware.camera2.CameraMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * S1574: the white-balance dropdown listed every AWB mode the camera reported and labelled anything
 * it did not recognise "Auto", so CONTROL_AWB_MODE_OFF appeared as a second "Auto" row that turned
 * automatic white balance off. The filter and the label source are now one ordered map; these tests
 * pin the two properties that made the duplicate possible.
 */
class CameraSettingsWhiteBalanceModesTest {

    @Test
    fun `off is not offered`() {
        val reported = listOf(
            CameraMetadata.CONTROL_AWB_MODE_OFF,
            CameraMetadata.CONTROL_AWB_MODE_AUTO,
            CameraMetadata.CONTROL_AWB_MODE_SHADE,
        )

        val presented = CameraSettingsDialogFragment.presentableWhiteBalanceModes(reported)

        assertFalse(
            "CONTROL_AWB_MODE_OFF hands white balance to an app that sets no colour-correction gains",
            presented.contains(CameraMetadata.CONTROL_AWB_MODE_OFF),
        )
        assertEquals(
            listOf(CameraMetadata.CONTROL_AWB_MODE_AUTO, CameraMetadata.CONTROL_AWB_MODE_SHADE),
            presented,
        )
    }

    @Test
    fun `an unknown vendor mode is not offered`() {
        val reported = listOf(CameraMetadata.CONTROL_AWB_MODE_AUTO, UNKNOWN_VENDOR_MODE)

        val presented = CameraSettingsDialogFragment.presentableWhiteBalanceModes(reported)

        assertEquals(listOf(CameraMetadata.CONTROL_AWB_MODE_AUTO), presented)
    }

    @Test
    fun `automatic comes first and no mode is listed twice`() {
        val reported = listOf(
            CameraMetadata.CONTROL_AWB_MODE_SHADE,
            CameraMetadata.CONTROL_AWB_MODE_TWILIGHT,
            CameraMetadata.CONTROL_AWB_MODE_AUTO,
            CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
            CameraMetadata.CONTROL_AWB_MODE_AUTO,
        )

        val presented = CameraSettingsDialogFragment.presentableWhiteBalanceModes(reported)

        assertEquals(CameraMetadata.CONTROL_AWB_MODE_AUTO, presented.first())
        assertEquals(presented.distinct(), presented)
    }

    private companion object {
        /** Outside the Camera2 AWB range, standing in for a vendor mode a future device may report. */
        const val UNKNOWN_VENDOR_MODE = 99
    }
}

package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.hardware.camera2.CameraMetadata
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1418: the derived "manual" name for the plain profile.
 *
 * The regression this suite exists to catch is a profile that sets its own exposure - night, sport -
 * being reported as manual the moment the user picks it, which would make the name meaningless.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraProfilePresentationTest {

    private val auto = CameraMetadata.CONTROL_AWB_MODE_AUTO
    private val incandescent = CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT

    @Test
    fun `untouched normal is not manual`() {
        assertFalse(CameraProfilePresentation.isManual(PhotoProfile.NORMAL, 0, null))
        assertFalse(CameraProfilePresentation.isManual(PhotoProfile.NORMAL, 0, auto))
    }

    @Test
    fun `exposure pulled off automatic makes normal manual, in either direction`() {
        assertTrue(CameraProfilePresentation.isManual(PhotoProfile.NORMAL, 2, auto))
        assertTrue(CameraProfilePresentation.isManual(PhotoProfile.NORMAL, -2, auto))
    }

    @Test
    fun `white balance pulled off automatic makes normal manual`() {
        assertTrue(CameraProfilePresentation.isManual(PhotoProfile.NORMAL, 0, incandescent))
    }

    @Test
    fun `a profile that sets its own exposure never reads as manual`() {
        for (profile in PhotoProfile.entries.filter { it != PhotoProfile.NORMAL }) {
            assertFalse(
                "$profile must not be reported as manual",
                CameraProfilePresentation.isManual(profile, 2, incandescent),
            )
        }
    }

    @Test
    fun `manual substitutes the name of normal and of nothing else`() {
        assertEquals(
            R.string.camera_profile_manual,
            CameraProfilePresentation.labelRes(PhotoProfile.NORMAL, manual = true),
        )
        assertEquals(
            R.string.camera_profile_normal,
            CameraProfilePresentation.labelRes(PhotoProfile.NORMAL, manual = false),
        )
        assertEquals(
            R.string.camera_profile_night,
            CameraProfilePresentation.labelRes(PhotoProfile.NIGHT, manual = true),
        )
    }
}

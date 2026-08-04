package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.util.Range
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1262: the sport recipe's clamping, which is the only part of it a device cannot argue with.
 *
 * The numbers are asserted through `resolvedExposureNs` / `resolvedIso` rather than by reading the
 * built `CaptureRequestOptions` back - the options bag has no public getter, and the two entry
 * points share one code path by construction, which is why they exist as a pair.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SportExposureOptionsFactoryTest {

    private fun sportCapable(
        shutter: Range<Long> = Range(1_000_000L, 100_000_000L),
        iso: Range<Int> = Range(50, 3200),
    ) = CameraRuntimeCapabilities(
        supportsManualSensor = true,
        isoRange = iso,
        shutterRangeNs = shutter,
    )

    @Test
    fun `exposure sits at the target when the sensor range contains it`() {
        val caps = sportCapable()

        assertEquals(PhotoProfile.SPORT_TARGET_EXPOSURE_NS, SportExposureOptionsFactory.resolvedExposureNs(caps))
        assertTrue(SportExposureOptionsFactory.isApplicable(caps))
    }

    @Test
    fun `exposure clamps up into a sensor that cannot go as short as the target`() {
        val floor = PhotoProfile.SPORT_TARGET_EXPOSURE_NS + 1_000_000L
        val caps = sportCapable(shutter = Range(floor, 100_000_000L))

        // The clamp still reports the floor, but the profile is not offered at all - see below.
        assertEquals(floor, SportExposureOptionsFactory.resolvedExposureNs(caps))
        assertFalse(SportExposureOptionsFactory.isApplicable(caps))
    }

    @Test
    fun `exposure clamps down when the sensor's longest exposure is shorter than the target`() {
        val ceiling = PhotoProfile.SPORT_TARGET_EXPOSURE_NS - 1_000_000L
        val caps = sportCapable(shutter = Range(100_000L, ceiling))

        assertEquals(ceiling, SportExposureOptionsFactory.resolvedExposureNs(caps))
    }

    @Test
    fun `iso caps at the recipe ceiling and floors at what the sensor supports`() {
        assertEquals(
            SportExposureOptionsFactory.ISO_CAP,
            SportExposureOptionsFactory.resolvedIso(sportCapable(iso = Range(50, 6400))),
        )
        // Sensor tops out below the cap.
        assertEquals(800, SportExposureOptionsFactory.resolvedIso(sportCapable(iso = Range(50, 800))))
        // Sensor floor is above the cap - the floor wins, because a lower ISO is not selectable.
        assertEquals(3200, SportExposureOptionsFactory.resolvedIso(sportCapable(iso = Range(3200, 6400))))
    }

    @Test
    fun `no options when the device fails the sport predicate`() {
        assertFalse(SportExposureOptionsFactory.isApplicable(sportCapable().copy(supportsManualSensor = false)))
        assertFalse(SportExposureOptionsFactory.isApplicable(sportCapable().copy(isoRange = null)))
        assertFalse(SportExposureOptionsFactory.isApplicable(sportCapable().copy(shutterRangeNs = null)))
        assertFalse(SportExposureOptionsFactory.isApplicable(CameraRuntimeCapabilities()))
    }
}

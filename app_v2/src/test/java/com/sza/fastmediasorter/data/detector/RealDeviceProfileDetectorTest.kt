package com.sza.fastmediasorter.data.detector

import android.content.Context
import android.content.res.Configuration
import com.sza.fastmediasorter.data.model.DetectionConfidence
import com.sza.fastmediasorter.data.model.DeviceProfileType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RealDeviceProfileDetector].
 *
 * [DetectionHelper] is mocked as an object so the detector's decision logic is exercised
 * deterministically without a real Android [Context] or platform feature flags.
 */
class RealDeviceProfileDetectorTest {

    private val context = mockk<Context>(relaxed = true)
    private lateinit var detector: RealDeviceProfileDetector

    @Before
    fun setup() {
        mockkObject(DetectionHelper)
        // Baseline: no signals fire.
        every { DetectionHelper.hasVrFeatures(any()) } returns false
        every { DetectionHelper.hasAutomotiveFeature(any()) } returns false
        every { DetectionHelper.hasTelevisionFeature(any()) } returns false
        every { DetectionHelper.getSmallestWidthDp(any()) } returns 0
        every { DetectionHelper.hasTelephonyFeature(any()) } returns false
        every { DetectionHelper.isKnownVrHeadsetManufacturer() } returns false
        every { DetectionHelper.getManufacturer() } returns "generic"
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_NORMAL
        every { DetectionHelper.isChromebook(any()) } returns false
        every { DetectionHelper.hasPcFeature(any()) } returns false
        detector = RealDeviceProfileDetector(context)
    }

    @After
    fun tearDown() {
        unmockkObject(DetectionHelper)
    }

    @Test
    fun `detects VR headset with HIGH confidence when VR features present`() = runTest {
        every { DetectionHelper.hasVrFeatures(any()) } returns true
        every { DetectionHelper.isKnownVrHeadsetManufacturer() } returns true

        val result = detector.detectProfile()

        assertEquals(DeviceProfileType.VR_HEADSET, result.profile)
        assertEquals(DetectionConfidence.HIGH, result.confidence)
        assertTrue(result.signals.contains("has_vr_features"))
    }

    @Test
    fun `detects car head unit with HIGH confidence when automotive feature present`() = runTest {
        every { DetectionHelper.hasAutomotiveFeature(any()) } returns true

        val result = detector.detectProfile()

        assertEquals(DeviceProfileType.CAR_HEAD_UNIT, result.profile)
        assertEquals(DetectionConfidence.HIGH, result.confidence)
    }

    @Test
    fun `detects TV media box with HIGH confidence when television feature present`() = runTest {
        every { DetectionHelper.hasTelevisionFeature(any()) } returns true

        val result = detector.detectProfile()

        assertEquals(DeviceProfileType.TV_MEDIA_BOX, result.profile)
        assertEquals(DetectionConfidence.HIGH, result.confidence)
    }

    @Test
    fun `detects home tablet with MEDIUM confidence for large screen width`() = runTest {
        every { DetectionHelper.getSmallestWidthDp(any()) } returns 800

        val result = detector.detectProfile()

        assertEquals(DeviceProfileType.HOME_TABLET, result.profile)
        assertEquals(DetectionConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun `detects personal smartphone with MEDIUM confidence for small screen with telephony`() = runTest {
        every { DetectionHelper.getSmallestWidthDp(any()) } returns 400
        every { DetectionHelper.hasTelephonyFeature(any()) } returns true

        val result = detector.detectProfile()

        assertEquals(DeviceProfileType.PERSONAL_SMARTPHONE, result.profile)
        assertEquals(DetectionConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun `falls back to personal smartphone with LOW confidence when no signals fire`() = runTest {
        val result = detector.detectProfile()

        assertEquals(DeviceProfileType.PERSONAL_SMARTPHONE, result.profile)
        assertEquals(DetectionConfidence.LOW, result.confidence)
        assertTrue(result.signals.contains("fallback_unknown"))
    }

    @Test
    fun `VR detection takes priority over screen width signals`() = runTest {
        every { DetectionHelper.hasVrFeatures(any()) } returns true
        every { DetectionHelper.getSmallestWidthDp(any()) } returns 800

        val result = detector.detectProfile()

        assertEquals(DeviceProfileType.VR_HEADSET, result.profile)
        assertEquals(DetectionConfidence.HIGH, result.confidence)
    }

    @Test
    fun `detects car head unit from UiModeManager car mode`() = runTest {
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_CAR

        val result = detector.detectProfile()

        assertEquals(DeviceProfileType.CAR_HEAD_UNIT, result.profile)
        assertEquals(DetectionConfidence.HIGH, result.confidence)
    }

    @Test
    fun `detects tablet and desktop profile for a Chromebook`() = runTest {
        every { DetectionHelper.isChromebook(any()) } returns true

        val result = detector.detectProfile()

        assertEquals(DeviceProfileType.HOME_TABLET, result.profile)
        assertEquals(DetectionConfidence.MEDIUM, result.confidence)
    }
}

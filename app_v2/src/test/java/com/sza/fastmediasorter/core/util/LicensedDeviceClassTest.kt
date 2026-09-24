package com.sza.fastmediasorter.core.util

import android.content.Context
import android.content.res.Configuration
import com.sza.fastmediasorter.core.util.LicensedDeviceClass.DeviceClass
import com.sza.fastmediasorter.data.detector.DetectionHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The device-class -> translation-availability matrix S1625 exists to keep red.
 *
 * [DetectionHelper] is mocked as an object so each device class is expressed by its platform signals
 * without a real [Context]. The cache is dropped between cases; without that every case after the
 * first would assert the first one's answer.
 */
class LicensedDeviceClassTest {

    private val context = mockk<Context>(relaxed = true)

    private companion object {
        const val TABLET_WIDTH_DP = 800
        const val PHONE_WIDTH_DP = 411
    }

    @Before
    fun setup() {
        mockkObject(DetectionHelper)
        noSignals()
        LicensedDeviceClass.resetForTest()
    }

    @After
    fun tearDown() {
        unmockkObject(DetectionHelper)
        LicensedDeviceClass.resetForTest()
    }

    private fun noSignals() {
        every { DetectionHelper.hasVrFeatures(any()) } returns false
        every { DetectionHelper.hasAutomotiveFeature(any()) } returns false
        every { DetectionHelper.hasTelevisionFeature(any()) } returns false
        every { DetectionHelper.isKnownVrHeadsetManufacturer() } returns false
        every { DetectionHelper.isChromebook(any()) } returns false
        every { DetectionHelper.hasPcFeature(any()) } returns false
        every { DetectionHelper.hasTelephonyFeature(any()) } returns false
        every { DetectionHelper.getSmallestWidthDp(any()) } returns PHONE_WIDTH_DP
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_UNDEFINED
    }

    private fun resolve(): DeviceClass {
        LicensedDeviceClass.resetForTest()
        return LicensedDeviceClass.current(context)
    }

    private fun allowed(): Boolean {
        LicensedDeviceClass.resetForTest()
        return LicensedDeviceClass.isMlKitTranslationAllowed(context)
    }

    // ===== Forbidden classes =====

    @Test
    fun `television is not licensed`() {
        every { DetectionHelper.hasTelevisionFeature(any()) } returns true
        assertEquals(DeviceClass.TELEVISION, resolve())
        assertFalse(allowed())
    }

    @Test
    fun `television by ui mode alone is not licensed`() {
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_TELEVISION
        assertEquals(DeviceClass.TELEVISION, resolve())
        assertFalse(allowed())
    }

    @Test
    fun `car head unit is not licensed`() {
        every { DetectionHelper.hasAutomotiveFeature(any()) } returns true
        assertEquals(DeviceClass.AUTOMOTIVE, resolve())
        assertFalse(allowed())
    }

    @Test
    fun `xr headset is not licensed`() {
        every { DetectionHelper.hasVrFeatures(any()) } returns true
        assertEquals(DeviceClass.XR_HEADSET, resolve())
        assertFalse(allowed())
    }

    @Test
    fun `watch is not licensed`() {
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_WATCH
        assertEquals(DeviceClass.WATCH, resolve())
        assertFalse(allowed())
    }

    @Test
    fun `unknown device is not licensed`() {
        assertEquals(DeviceClass.UNKNOWN, resolve())
        assertFalse(allowed())
    }

    // ===== Allowed classes =====

    @Test
    fun `handheld is licensed`() {
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_NORMAL
        every { DetectionHelper.hasTelephonyFeature(any()) } returns true
        assertEquals(DeviceClass.HANDHELD, resolve())
        assertTrue(allowed())
    }

    @Test
    fun `tablet is licensed`() {
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_NORMAL
        every { DetectionHelper.getSmallestWidthDp(any()) } returns TABLET_WIDTH_DP
        assertEquals(DeviceClass.TABLET, resolve())
        assertTrue(allowed())
    }

    @Test
    fun `chromebook is licensed`() {
        every { DetectionHelper.isChromebook(any()) } returns true
        assertEquals(DeviceClass.CHROMEBOOK, resolve())
        assertTrue(allowed())
    }

    @Test
    fun `desktop is licensed`() {
        every { DetectionHelper.hasPcFeature(any()) } returns true
        assertEquals(DeviceClass.DESKTOP, resolve())
        assertTrue(allowed())
    }

    @Test
    fun `phone in a desk dock is licensed`() {
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_DESK
        assertEquals(DeviceClass.HANDHELD, resolve())
        assertTrue(allowed())
    }

    // ===== Precedence and caching =====

    @Test
    fun `headset reporting a normal ui mode is still a headset`() {
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_NORMAL
        every { DetectionHelper.hasVrFeatures(any()) } returns true
        assertEquals(DeviceClass.XR_HEADSET, resolve())
    }

    @Test
    fun `a tv that also declares pc is judged a television`() {
        every { DetectionHelper.hasTelevisionFeature(any()) } returns true
        every { DetectionHelper.hasPcFeature(any()) } returns true
        assertEquals(DeviceClass.TELEVISION, resolve())
        assertFalse(allowed())
    }

    @Test
    fun `the class is resolved once per process`() {
        every { DetectionHelper.hasTelevisionFeature(any()) } returns true
        assertEquals(DeviceClass.TELEVISION, resolve())

        every { DetectionHelper.hasTelevisionFeature(any()) } returns false
        every { DetectionHelper.getUiModeType(any()) } returns Configuration.UI_MODE_TYPE_NORMAL
        assertEquals(DeviceClass.TELEVISION, LicensedDeviceClass.current(context))
    }

    @Test
    fun `the allow set holds exactly the four licensed classes`() {
        assertEquals(
            setOf(DeviceClass.HANDHELD, DeviceClass.TABLET, DeviceClass.DESKTOP, DeviceClass.CHROMEBOOK),
            LicensedDeviceClass.MLKIT_TRANSLATION_ALLOWED,
        )
    }
}

package com.sza.fastmediasorter.core.capability

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.util.LicensedDeviceClass
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
 * S0403: the foss flavor mounts no capability source set, so its `@CompiledCapabilities` set
 * resolves empty through the `@Multibinds` default. This locks what that empty set has to mean -
 * the invisibility contract is enforced one layer above, and every gate up there asks these
 * methods.
 *
 * S1625 added the second axis for translation: the two must multiply, so a capability compiled into
 * the build is still unavailable on a device class the ML Kit licence forbids.
 */
class CapabilityAvailabilityTest {

    private val emptyBuild = CapabilityAvailability(emptySet())
    private val translationBuild = CapabilityAvailability(setOf(CapabilityAvailability.CAP_TRANSLATION))
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setup() {
        mockkObject(LicensedDeviceClass)
        allowDeviceClass(LicensedDeviceClass.DeviceClass.HANDHELD)
    }

    @After
    fun tearDown() {
        unmockkObject(LicensedDeviceClass)
    }

    private fun allowDeviceClass(deviceClass: LicensedDeviceClass.DeviceClass) {
        every { LicensedDeviceClass.current(any()) } returns deviceClass
    }

    @Test
    fun `empty compiled set reports translation unavailable`() {
        assertFalse(emptyBuild.isTranslationAvailable(context))
    }

    @Test
    fun `empty compiled set reports ocr unavailable without touching the device axis`() {
        // isOcrAvailable short-circuits on isOcrCompiledIn, so the context is never dereferenced -
        // which is the point: a build without the capability must not need a device to answer.
        assertFalse(emptyBuild.isOcrCompiledIn())
        assertFalse(emptyBuild.isOcrAvailable(mockk<Context>()))
    }

    @Test
    fun `empty compiled set reports vr and newpipe unavailable`() {
        assertFalse(emptyBuild.isVrAvailable())
        assertFalse(emptyBuild.isNewPipeAvailable())
    }

    /**
     * The extensions screen is not a pure function of the multibound set - streams is a BuildConfig
     * flag, and this suite runs on the standard variant where it is on. Asserting `false` here would
     * pass only on a flavor nobody runs the shared test set against, so the assertion is the
     * relation instead: with OCR and translation gone, the screen exists exactly when streams do.
     */
    @Test
    fun `extensions screen follows streams alone once ocr and translation are gone`() {
        assertEquals(BuildConfig.SUPPORT_STREAMS, emptyBuild.isExtensionsScreenAvailable(context))
    }

    @Test
    fun `a compiled capability is reported available on a licensed device`() {
        assertTrue(translationBuild.isTranslationAvailable(context))
        assertFalse(translationBuild.isOcrCompiledIn())
    }

    // ===== S1625: the device axis =====

    @Test
    fun `translation compiled in is unavailable on a device class the licence forbids`() {
        allowDeviceClass(LicensedDeviceClass.DeviceClass.TELEVISION)
        assertFalse(translationBuild.isTranslationAvailable(context))
        val support = translationBuild.translationSupport(context)
        assertEquals(
            CapabilityAvailability.TranslationSupport.Unsupported(
                reason = CapabilityAvailability.TranslationUnavailableReason.DEVICE_CLASS_NOT_LICENSED,
                deviceClass = LicensedDeviceClass.DeviceClass.TELEVISION,
            ),
            support,
        )
    }

    @Test
    fun `translation compiled in is supported on a licensed device class`() {
        allowDeviceClass(LicensedDeviceClass.DeviceClass.TABLET)
        assertEquals(
            CapabilityAvailability.TranslationSupport.Supported,
            translationBuild.translationSupport(context),
        )
    }

    /** The compile axis is reported first: a build without translation says so whatever the device is. */
    @Test
    fun `translation absent from the build reports the build as the reason`() {
        allowDeviceClass(LicensedDeviceClass.DeviceClass.XR_HEADSET)
        val support = emptyBuild.translationSupport(context)
        assertEquals(
            CapabilityAvailability.TranslationSupport.Unsupported(
                reason = CapabilityAvailability.TranslationUnavailableReason.NOT_COMPILED_IN,
                deviceClass = LicensedDeviceClass.DeviceClass.XR_HEADSET,
            ),
            support,
        )
    }

    @Test
    fun `the compile axis alone still answers without a device`() {
        assertTrue(translationBuild.isTranslationCompiledIn())
        assertFalse(emptyBuild.isTranslationCompiledIn())
    }
}

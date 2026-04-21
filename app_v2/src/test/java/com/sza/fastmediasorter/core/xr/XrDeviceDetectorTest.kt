package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrDeviceDetectorTest {

    private fun buildContext(
        hasHeadtracking: Boolean = false,
        hasVrHighPerf: Boolean = false,
        manufacturer: String = "Samsung"
    ): Context {
        val pm = mockk<PackageManager>(relaxed = true)
        every { pm.hasSystemFeature("android.hardware.vr.headtracking") } returns hasHeadtracking
        @Suppress("DEPRECATION")
        every { pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE) } returns hasVrHighPerf

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.packageManager } returns pm

        // Mock Build.MANUFACTURER via reflection is not practical in unit tests —
        // instead we verify manufacturer fallback indirectly through the detector's
        // public contract: if features are false, only manufacturer name matters.
        // The manufacturer field is read from Build.MANUFACTURER statically, so
        // we test it on real device or via Robolectric; here we cover the feature paths.
        return ctx
    }

    @Test
    fun `headtracking feature triggers headset detection`() {
        val ctx = buildContext(hasHeadtracking = true)
        assertTrue(XrDeviceDetector.isXrHeadset(ctx))
    }

    @Test
    fun `vr high perf feature triggers headset detection`() {
        val ctx = buildContext(hasVrHighPerf = true)
        assertTrue(XrDeviceDetector.isXrHeadset(ctx))
    }

    @Test
    fun `no features and non-Meta manufacturer returns false`() {
        val ctx = buildContext(hasHeadtracking = false, hasVrHighPerf = false, manufacturer = "Samsung")
        // Build.MANUFACTURER on JVM is "unknown" — not oculus/meta — so this is false
        assertFalse(XrDeviceDetector.isXrHeadset(ctx))
    }

    @Test
    fun `both features false but headtracking true wins`() {
        val ctx = buildContext(hasHeadtracking = true, hasVrHighPerf = false)
        assertTrue(XrDeviceDetector.isXrHeadset(ctx))
    }

    @Test
    fun `only vr high perf true is sufficient`() {
        val ctx = buildContext(hasHeadtracking = false, hasVrHighPerf = true)
        assertTrue(XrDeviceDetector.isXrHeadset(ctx))
    }
}

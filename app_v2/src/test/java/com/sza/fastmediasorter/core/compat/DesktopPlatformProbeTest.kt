package com.sza.fastmediasorter.core.compat

import android.content.Context
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@Suppress("FunctionNaming") // backtick test names, project convention
class DesktopPlatformProbeTest {

    private val packageManager = mockk<PackageManager>()
    private val context =
        mockk<Context> { every { packageManager } returns this@DesktopPlatformProbeTest.packageManager }

    @Before
    fun resetCaches() {
        DesktopPlatformProbe.resetForTest()
        // Both probes cache for the process lifetime; a leaked answer would hide the case under test.
        ChromeOsCompat::class.java.getDeclaredField("_isChromeOs").apply {
            isAccessible = true
            set(null, null)
        }
    }

    private fun features(vararg present: String) {
        every { packageManager.hasSystemFeature(any<String>()) } answers { firstArg<String>() in present }
    }

    @Test
    fun `pc feature present makes a pc and a desktop platform`() {
        features(PC)
        assertTrue(DesktopPlatformProbe.isPcFormFactor(context))
        assertTrue(DesktopPlatformProbe.isDesktopPlatform(context))
    }

    @Test
    fun `no pc and no arc feature is neither`() {
        features()
        assertFalse(DesktopPlatformProbe.isPcFormFactor(context))
        assertFalse(DesktopPlatformProbe.isDesktopPlatform(context))
    }

    @Test
    fun `chromebook is a desktop platform but not a pc form factor`() {
        features(ARC)
        assertFalse(DesktopPlatformProbe.isPcFormFactor(context))
        assertTrue(DesktopPlatformProbe.isDesktopPlatform(context))
    }

    @Test
    fun `throwing package manager reads as not a pc`() {
        every { packageManager.hasSystemFeature(any<String>()) } throws SecurityException("denied")
        assertFalse(DesktopPlatformProbe.isPcFormFactor(context))
    }

    private companion object {
        const val PC = "android.hardware.type.pc"
        const val ARC = "org.chromium.arc"
    }
}

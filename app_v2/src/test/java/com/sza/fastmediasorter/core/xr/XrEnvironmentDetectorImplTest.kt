package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class XrEnvironmentDetectorImplTest {

    private val context: Context = mockk()
    private val packageManager: PackageManager = mockk()

    private lateinit var detector: XrEnvironmentDetectorImpl

    @Before
    fun setUp() {
        every { context.packageManager } returns packageManager
        detector = XrEnvironmentDetectorImpl(context)
    }

    @Test
    fun `detect returns ANDROID_XR when Android XR OpenXR feature is present`() {
        stubFeatures("android.software.xr.api.openxr")

        assertEquals(XrEnvironment.ANDROID_XR, detector.detect())
    }

    @Test
    fun `detect returns VR_QUEST for Quest 3 Oculus runtime markers`() {
        stubFeatures(
            "oculus.hardware.standalone_vr",
            "oculus.software.spatial",
            "oculus.software.xrsp",
        )

        assertEquals(XrEnvironment.VR_QUEST, detector.detect())
    }

    @Test
    fun `detect returns NONE when XR markers are absent`() {
        stubFeatures()

        assertEquals(XrEnvironment.NONE, detector.detect())
    }

    private fun stubFeatures(vararg features: String) {
        val active = features.toSet()
        every { packageManager.hasSystemFeature(any()) } answers {
            firstArg<String>() in active
        }
    }
}
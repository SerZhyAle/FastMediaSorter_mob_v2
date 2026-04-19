package com.sza.fastmediasorter.vr.render

import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VrLayerFactoryTest {

    private val factory: VrLayerFactory = DefaultVrLayerFactory()

    @Test
    fun `describe returns equirect layer with SBS UV split for 360 stereo`() {
        val descriptor = factory.describe(
            StereoMode.EQUIRECT_360_SBS,
            VrRenderingMode.FULL_STEREO,
        )

        assertEquals(VrLayerType.EQUIRECT_2, descriptor.type)
        assertEquals(0f, descriptor.leftEyeUv.uOffset)
        assertEquals(0.5f, descriptor.leftEyeUv.uScale)
        assertEquals(0.5f, descriptor.rightEyeUv.uOffset)
        assertEquals(0.5f, descriptor.rightEyeUv.uScale)
        assertEquals(VrLayerDescriptor.FULL_SPHERE_RADIANS, descriptor.centralHorizontalAngleRadians)
    }

    @Test
    fun `describe returns projection layer for flat stereo full mode`() {
        val descriptor = factory.describe(StereoMode.SBS_FULL, VrRenderingMode.FULL_STEREO)

        assertEquals(VrLayerType.PROJECTION, descriptor.type)
        assertEquals(0.5f, descriptor.leftEyeUv.uScale)
        assertEquals(0.5f, descriptor.rightEyeUv.uScale)
    }

    @Test
    fun `describe falls back to cinema quad for unsupported flat cinema stereo combo`() {
        val descriptor = factory.describe(StereoMode.SBS_FULL, VrRenderingMode.CINEMA)

        assertEquals(VrLayerType.QUAD_CINEMA, descriptor.type)
        assertEquals(VrLayerDescriptor.FULL_FRAME_UV, descriptor.leftEyeUv)
        assertEquals(VrLayerDescriptor.FULL_FRAME_UV, descriptor.rightEyeUv)
    }

    @Test
    fun `rendering mode parser accepts legacy fullscreen values`() {
        assertEquals(VrRenderingMode.FULL_STEREO, VrRenderingMode.fromPreferenceValue("FULL_SBS"))
        assertEquals(VrRenderingMode.FULL_STEREO, VrRenderingMode.fromPreferenceValue("FULL_OU"))
        assertEquals(VrRenderingMode.CINEMA, VrRenderingMode.fromPreferenceValue(null))
        assertTrue(VrRenderingMode.fromPreferenceValue("FULL_STEREO") == VrRenderingMode.FULL_STEREO)
    }
}
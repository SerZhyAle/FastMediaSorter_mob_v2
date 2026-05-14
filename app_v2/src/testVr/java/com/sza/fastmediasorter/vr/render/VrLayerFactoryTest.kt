package com.sza.fastmediasorter.vr.render

import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.render.stereoscopic.DefaultVrLayerFactory
import com.sza.fastmediasorter.ui.player.render.stereoscopic.VrLayerDescriptor
import com.sza.fastmediasorter.ui.player.render.stereoscopic.VrLayerFactory
import com.sza.fastmediasorter.ui.player.render.stereoscopic.VrLayerType
import com.sza.fastmediasorter.ui.player.render.stereoscopic.VrRenderingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer mapping stays covered by `testVr` so the VR renderer slice keeps
 * validating the shared-contract cutover together with VR-only code.
 */
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

    @Test
    fun `quadCinema geometry snapshot matches reference values`() {
        val d = factory.describe(StereoMode.MONO, VrRenderingMode.CINEMA)
        assertEquals(VrLayerType.QUAD_CINEMA, d.type)
        assertEquals(4f, d.widthMeters, 1e-4f)
        assertEquals(2.25f, d.heightMeters, 1e-4f)
        assertEquals(4f, d.distanceMeters, 1e-4f)
        assertEquals(1f, d.radiusMeters, 1e-4f)
    }

    @Test
    fun `equirect180 geometry snapshot matches reference values`() {
        val d = factory.describe(StereoMode.EQUIRECT_180_MONO, VrRenderingMode.FULL_STEREO)
        assertEquals(VrLayerType.EQUIRECT_2, d.type)
        assertEquals(VrLayerDescriptor.HALF_DOME_RADIANS, d.centralHorizontalAngleRadians, 1e-4f)
        assertEquals(VrLayerDescriptor.HALF_SPHERE_RADIANS, d.upperVerticalAngleRadians, 1e-4f)
        assertEquals(-VrLayerDescriptor.HALF_SPHERE_RADIANS, d.lowerVerticalAngleRadians, 1e-4f)
        assertEquals(1f, d.radiusMeters, 1e-4f)
    }

    @Test
    fun `equirect360 geometry snapshot matches reference values`() {
        val d = factory.describe(StereoMode.EQUIRECT_360_MONO, VrRenderingMode.FULL_STEREO)
        assertEquals(VrLayerType.EQUIRECT_2, d.type)
        assertEquals(VrLayerDescriptor.FULL_SPHERE_RADIANS, d.centralHorizontalAngleRadians, 1e-4f)
        assertEquals(VrLayerDescriptor.HALF_SPHERE_RADIANS, d.upperVerticalAngleRadians, 1e-4f)
        assertEquals(-VrLayerDescriptor.HALF_SPHERE_RADIANS, d.lowerVerticalAngleRadians, 1e-4f)
        assertEquals(1f, d.radiusMeters, 1e-4f)
    }
}
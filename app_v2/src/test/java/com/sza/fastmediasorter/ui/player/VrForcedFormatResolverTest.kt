package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Test

class VrForcedFormatResolverTest {

    @Test
    fun `per-file override wins over family settings`() {
        val resolved = VrForcedFormatResolver.resolve(
            detected = StereoMode.EQUIRECT_360_MONO,
            perFileOverride = StereoMode.VR180_FISHEYE_SBS,
            forcedPlat = StereoMode.MONO,
            forcedSpherical = StereoMode.EQUIRECT_360_SBS,
        )

        assertEquals(StereoMode.VR180_FISHEYE_SBS, resolved)
    }

    @Test
    fun `spherical content uses only spherical forced setting`() {
        val resolved = VrForcedFormatResolver.resolve(
            detected = StereoMode.EQUIRECT_360_MONO,
            perFileOverride = null,
            forcedPlat = StereoMode.MONO,
            forcedSpherical = StereoMode.EQUIRECT_360_SBS,
        )

        assertEquals(StereoMode.EQUIRECT_360_SBS, resolved)
    }

    @Test
    fun `flat content ignores spherical forced setting`() {
        val resolved = VrForcedFormatResolver.resolve(
            detected = StereoMode.SBS_FULL,
            perFileOverride = null,
            forcedPlat = StereoMode.MONO,
            forcedSpherical = StereoMode.EQUIRECT_360_SBS,
        )

        assertEquals(StereoMode.MONO, resolved)
    }

    @Test
    fun `unknown detection does not apply global forced family`() {
        val resolved = VrForcedFormatResolver.resolve(
            detected = StereoMode.UNKNOWN,
            perFileOverride = null,
            forcedPlat = StereoMode.MONO,
            forcedSpherical = StereoMode.EQUIRECT_360_SBS,
        )

        assertEquals(StereoMode.UNKNOWN, resolved)
    }

    @Test
    fun `setting mappers keep only values from the matching family`() {
        assertEquals(StereoMode.SBS_FULL, VrForcedFormatResolver.mapPlatSetting("SBS"))
        assertEquals(StereoMode.AUTO, VrForcedFormatResolver.mapPlatSetting("EQUIRECT_360_SBS"))
        assertEquals(StereoMode.EQUIRECT_180_MONO, VrForcedFormatResolver.mapSphericalSetting("EQUIRECT_180_MONO"))
        assertEquals(StereoMode.AUTO, VrForcedFormatResolver.mapSphericalSetting("OU"))
    }
}
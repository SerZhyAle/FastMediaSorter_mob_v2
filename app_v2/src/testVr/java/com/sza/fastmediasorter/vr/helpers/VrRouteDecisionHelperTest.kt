package com.sza.fastmediasorter.vr.helpers

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.vr.VrLaunchRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class VrRouteDecisionHelperTest {

    private val helper = VrRouteDecisionHelper()

    @Test
    fun `stereo image enters immersive static image route`() {
        val decision = helper.decide(
            currentFile = mediaFile("/photos/test_SBS.jpg", MediaType.IMAGE),
            effectiveStereoMode = StereoMode.SBS_FULL,
            settings = AppSettings(),
        )

        assertEquals(VrLaunchRoute.IMMERSIVE_STATIC_IMAGE, decision.route)
        assertEquals(StereoMode.SBS_FULL, decision.effectiveStereoMode)
    }

    @Test
    fun `plain image stays on standard player route`() {
        val decision = helper.decide(
            currentFile = mediaFile("/photos/plain.jpg", MediaType.IMAGE),
            effectiveStereoMode = StereoMode.MONO,
            settings = AppSettings(),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
    }

    @Test
    fun `unsupported immersive non image media shows explicit message route`() {
        val decision = helper.decide(
            currentFile = mediaFile("/docs/pano_360.pdf", MediaType.PDF),
            effectiveStereoMode = StereoMode.EQUIRECT_360_MONO,
            settings = AppSettings(),
        )

        assertEquals(VrLaunchRoute.UNSUPPORTED_IMMERSIVE_WITH_MESSAGE, decision.route)
    }

    @Test
    fun `disable 3d vr forces panel fallback`() {
        val decision = helper.decide(
            currentFile = mediaFile("/photos/test_SBS.jpg", MediaType.IMAGE),
            effectiveStereoMode = StereoMode.SBS_FULL,
            settings = AppSettings(disable3dVr = true),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals(StereoMode.MONO, decision.effectiveStereoMode)
    }

    private fun mediaFile(path: String, type: MediaType): MediaFile {
        return MediaFile(
            name = path.substringAfterLast('/'),
            path = path,
            type = type,
            size = 1L,
            createdDate = 1L,
        )
    }
}
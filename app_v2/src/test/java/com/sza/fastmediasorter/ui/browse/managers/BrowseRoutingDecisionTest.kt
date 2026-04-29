package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseRoutingDecisionTest {

    private fun media(
        path: String = "/movies/sample.mp4",
        type: MediaType = MediaType.VIDEO,
    ) = MediaFile(
        name = path.substringAfterLast('/'),
        path = path,
        type = type,
        size = 0L,
        createdDate = 0L,
    )

    // ── Non-video media ──────────────────────────────────────────────────────

    @Test
    fun `image always routes to standard player`() {
        val r = BrowseRoutingDecision.decide(
            media(path = "/photos/p.jpg", type = MediaType.IMAGE),
            StereoMode.SBS_FULL,
            AppSettings(),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    @Test
    fun `audio always routes to standard player`() {
        val r = BrowseRoutingDecision.decide(
            media(path = "/music/a.mp3", type = MediaType.AUDIO),
            StereoMode.MONO,
            AppSettings(),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    // ── disable3dVr kill-switch ──────────────────────────────────────────────

    @Test
    fun `disable3dVr forces standard for stereo video`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.VR180_FISHEYE_SBS,
            AppSettings(disable3dVr = true, vrAutoImmersive = true),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    // ── Plain 2D video ───────────────────────────────────────────────────────

    @Test
    fun `mono video routes to standard player even with auto-immersive on`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.MONO,
            AppSettings(vrAutoImmersive = true),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    @Test
    fun `unknown video routes to standard player`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.UNKNOWN,
            AppSettings(vrAutoImmersive = true),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    // ── S0026 main rule: vrAutoImmersive=false × stereo content ──────────────

    @Test
    fun `auto-immersive disabled keeps SBS_FULL video on standard player`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.SBS_FULL,
            AppSettings(vrAutoImmersive = false),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    @Test
    fun `auto-immersive disabled keeps OU video on standard player`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.OU,
            AppSettings(vrAutoImmersive = false),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    @Test
    fun `auto-immersive disabled keeps VR180 fisheye video on standard player`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.VR180_FISHEYE_SBS,
            AppSettings(vrAutoImmersive = false),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    @Test
    fun `auto-immersive disabled keeps EQUIRECT_360_MONO video on standard player`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.EQUIRECT_360_MONO,
            AppSettings(vrAutoImmersive = false),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    @Test
    fun `auto-immersive disabled keeps EQUIRECT_360_SBS video on standard player`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.EQUIRECT_360_SBS,
            AppSettings(vrAutoImmersive = false),
        )
        assertEquals(BrowseRoutingDecision.Route.STANDARD_PLAYER, r)
    }

    // ── Auto-immersive enabled × stereo content → VR ─────────────────────────

    @Test
    fun `auto-immersive enabled routes SBS_FULL video to VR`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.SBS_FULL,
            AppSettings(vrAutoImmersive = true),
        )
        assertEquals(BrowseRoutingDecision.Route.VR_PLAYER, r)
    }

    @Test
    fun `auto-immersive enabled routes VR180 video to VR`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.VR180_FISHEYE_SBS,
            AppSettings(vrAutoImmersive = true),
        )
        assertEquals(BrowseRoutingDecision.Route.VR_PLAYER, r)
    }

    @Test
    fun `auto-immersive enabled routes EQUIRECT_360_MONO video to VR`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.EQUIRECT_360_MONO,
            AppSettings(vrAutoImmersive = true),
        )
        assertEquals(BrowseRoutingDecision.Route.VR_PLAYER, r)
    }

    @Test
    fun `auto-immersive enabled routes OU video to VR`() {
        val r = BrowseRoutingDecision.decide(
            media(),
            StereoMode.OU,
            AppSettings(vrAutoImmersive = true),
        )
        assertEquals(BrowseRoutingDecision.Route.VR_PLAYER, r)
    }
}

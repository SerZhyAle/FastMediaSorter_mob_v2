package com.sza.fastmediasorter.vr.helpers

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.vr.VrLaunchRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // ── Phase 01.1 — vrAutoImmersive=false × video matrix ────────────────────

    @Test
    fun `auto-immersive disabled keeps SBS_FULL video on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/sbs.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.SBS_FULL,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("auto-immersive-disabled", decision.logReason)
    }

    @Test
    fun `auto-immersive disabled keeps OU video on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/ou.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.OU,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("auto-immersive-disabled", decision.logReason)
    }

    @Test
    fun `auto-immersive disabled keeps EQUIRECT_360_MONO video on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/360_mono.webm", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.EQUIRECT_360_MONO,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("auto-immersive-disabled", decision.logReason)
    }

    @Test
    fun `auto-immersive disabled keeps EQUIRECT_360_SBS video on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/360_sbs.webm", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.EQUIRECT_360_SBS,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("auto-immersive-disabled", decision.logReason)
    }

    @Test
    fun `auto-immersive disabled keeps VR180 fisheye video on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/vr180.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.VR180_FISHEYE_SBS,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("auto-immersive-disabled", decision.logReason)
    }

    @Test
    fun `auto-immersive disabled keeps MONO video on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/plain.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.MONO,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("plain-2d-video", decision.logReason)
    }

    // ── Phase 01.2 — vrAutoImmersive=false × image matrix ────────────────────
    // Resolves strategic §6.2: VR photo follows the same rule as VR video stereo content.

    @Test
    fun `auto-immersive disabled keeps MONO image on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/photos/plain.jpg", MediaType.IMAGE),
            effectiveStereoMode = StereoMode.MONO,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("plain-2d-content", decision.logReason)
    }

    @Test
    fun `auto-immersive disabled keeps SBS_FULL image on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/photos/sbs.jpg", MediaType.IMAGE),
            effectiveStereoMode = StereoMode.SBS_FULL,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("auto-immersive-disabled", decision.logReason)
    }

    @Test
    fun `auto-immersive disabled keeps OU image on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/photos/ou.jpg", MediaType.IMAGE),
            effectiveStereoMode = StereoMode.OU,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("auto-immersive-disabled", decision.logReason)
    }

    @Test
    fun `auto-immersive disabled keeps EQUIRECT_360_MONO image on panel`() {
        val decision = helper.decide(
            currentFile = mediaFile("/photos/360.jpg", MediaType.IMAGE),
            effectiveStereoMode = StereoMode.EQUIRECT_360_MONO,
            settings = AppSettings(vrAutoImmersive = false),
        )

        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, decision.route)
        assertEquals("auto-immersive-disabled", decision.logReason)
    }

    // ── Phase 01.3 — vrAutoImmersive=true × all-stereo matrix (legacy contract) ──

    @Test
    fun `auto-immersive enabled enters immersive video for SBS_FULL`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/sbs.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.SBS_FULL,
            settings = AppSettings(vrAutoImmersive = true),
        )

        assertEquals(VrLaunchRoute.IMMERSIVE_VIDEO, decision.route)
        assertEquals("immersive-video", decision.logReason)
    }

    @Test
    fun `auto-immersive enabled enters immersive video for EQUIRECT_360_SBS`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/360_sbs.webm", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.EQUIRECT_360_SBS,
            settings = AppSettings(vrAutoImmersive = true),
        )

        assertEquals(VrLaunchRoute.IMMERSIVE_VIDEO, decision.route)
        assertEquals("immersive-video", decision.logReason)
    }

    @Test
    fun `auto-immersive enabled enters immersive video for VR180 fisheye`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/vr180.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.VR180_FISHEYE_SBS,
            settings = AppSettings(vrAutoImmersive = true),
        )

        assertEquals(VrLaunchRoute.IMMERSIVE_VIDEO, decision.route)
        assertEquals("immersive-video", decision.logReason)
    }

    @Test
    fun `auto-immersive enabled enters immersive static image for SBS_FULL image`() {
        val decision = helper.decide(
            currentFile = mediaFile("/photos/sbs.jpg", MediaType.IMAGE),
            effectiveStereoMode = StereoMode.SBS_FULL,
            settings = AppSettings(vrAutoImmersive = true),
        )

        assertEquals(VrLaunchRoute.IMMERSIVE_STATIC_IMAGE, decision.route)
        assertEquals("immersive-static-image", decision.logReason)
    }

    @Test
    fun `auto-immersive enabled enters immersive static image for EQUIRECT_360_MONO image`() {
        val decision = helper.decide(
            currentFile = mediaFile("/photos/360.jpg", MediaType.IMAGE),
            effectiveStereoMode = StereoMode.EQUIRECT_360_MONO,
            settings = AppSettings(vrAutoImmersive = true),
        )

        assertEquals(VrLaunchRoute.IMMERSIVE_STATIC_IMAGE, decision.route)
        assertEquals("immersive-static-image", decision.logReason)
    }

    @Test
    fun `auto-immersive enabled routes MONO video to cinema immersive`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/plain.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.MONO,
            settings = AppSettings(vrAutoImmersive = true),
        )

        assertEquals(VrLaunchRoute.CINEMA_IMMERSIVE, decision.route)
        assertEquals("plain-2d-video", decision.logReason)
    }

    // ── Phase 01.4 — (route, reason) coupling invariant ──────────────────────
    //
    // For every reason string the helper currently emits, this test asserts the
    // returned route is in the allowed set for that reason. Catches regressions
    // where the helper would return a route that contradicts the logged reason.

    @Test
    fun `route and reason are coupled - panel-only reasons never yield immersive route`() {
        // (route, reason) invariant — exhaustive sweep across reasons that MUST
        // map to STANDARD_PANEL_FALLBACK.
        val panelOnlyCases = listOf(
            // disable-3d-vr
            Triple("disable-3d-vr", AppSettings(disable3dVr = true), StereoMode.SBS_FULL to MediaType.VIDEO),
            // user-forced-panel
            Triple("user-forced-panel", AppSettings(), StereoMode.EQUIRECT_360_SBS to MediaType.VIDEO),
            // auto-immersive-disabled
            Triple("auto-immersive-disabled", AppSettings(vrAutoImmersive = false), StereoMode.EQUIRECT_360_SBS to MediaType.VIDEO),
            Triple("auto-immersive-disabled", AppSettings(vrAutoImmersive = false), StereoMode.SBS_FULL to MediaType.IMAGE),
            // plain-2d-content (image MONO)
            Triple("plain-2d-content", AppSettings(), StereoMode.MONO to MediaType.IMAGE),
            // plain-2d-video with vrAutoImmersive disabled stays on panel
            Triple("plain-2d-video", AppSettings(vrAutoImmersive = false), StereoMode.MONO to MediaType.VIDEO),
        )

        for ((expectedReason, settings, modeAndType) in panelOnlyCases) {
            val (mode, type) = modeAndType
            val decision = helper.decide(
                currentFile = mediaFile("/test/file.bin", type),
                effectiveStereoMode = mode,
                settings = settings,
                userForcedPanel = expectedReason == "user-forced-panel",
            )
            assertEquals(
                "reason=$expectedReason must yield STANDARD_PANEL_FALLBACK",
                VrLaunchRoute.STANDARD_PANEL_FALLBACK,
                decision.route,
            )
            assertEquals(
                "reason mismatch for case mode=$mode type=$type settings=$settings",
                expectedReason,
                decision.logReason,
            )
        }
    }

    @Test
    fun `route and reason are coupled - immersive reasons match immersive routes`() {
        val immersiveCases = listOf(
            // immersive-video
            Triple("immersive-video", VrLaunchRoute.IMMERSIVE_VIDEO, StereoMode.SBS_FULL to MediaType.VIDEO),
            Triple("immersive-video", VrLaunchRoute.IMMERSIVE_VIDEO, StereoMode.EQUIRECT_360_SBS to MediaType.VIDEO),
            // immersive-static-image
            Triple("immersive-static-image", VrLaunchRoute.IMMERSIVE_STATIC_IMAGE, StereoMode.SBS_FULL to MediaType.IMAGE),
            // user-forced-immersive (via the userForcedImmersive flag)
            Triple("user-forced-immersive", VrLaunchRoute.IMMERSIVE_VIDEO, StereoMode.MONO to MediaType.VIDEO),
            // unsupported-immersive-media-type (PDF asking for immersive)
            Triple("unsupported-immersive-media-type", VrLaunchRoute.UNSUPPORTED_IMMERSIVE_WITH_MESSAGE, StereoMode.EQUIRECT_360_MONO to MediaType.PDF),
        )

        for ((expectedReason, expectedRoute, modeAndType) in immersiveCases) {
            val (mode, type) = modeAndType
            val decision = helper.decide(
                currentFile = mediaFile("/test/file.bin", type),
                effectiveStereoMode = mode,
                settings = AppSettings(),
                userForcedImmersive = expectedReason == "user-forced-immersive",
            )
            assertEquals(
                "reason=$expectedReason must yield route=$expectedRoute",
                expectedRoute,
                decision.route,
            )
            assertEquals(
                "reason mismatch for case mode=$mode type=$type",
                expectedReason,
                decision.logReason,
            )
        }
    }

    @Test
    fun `route and reason are coupled - plain-2d-video with auto-immersive enabled goes to cinema`() {
        // Special case: plain-2d-video reason is allowed both routes depending on vrAutoImmersive.
        val withAuto = helper.decide(
            currentFile = mediaFile("/movies/plain.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.MONO,
            settings = AppSettings(vrAutoImmersive = true),
        )
        assertEquals(VrLaunchRoute.CINEMA_IMMERSIVE, withAuto.route)
        assertEquals("plain-2d-video", withAuto.logReason)

        val withoutAuto = helper.decide(
            currentFile = mediaFile("/movies/plain.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.MONO,
            settings = AppSettings(vrAutoImmersive = false),
        )
        assertEquals(VrLaunchRoute.STANDARD_PANEL_FALLBACK, withoutAuto.route)
        assertEquals("plain-2d-video", withoutAuto.logReason)

        // Both have the same reason — but the (route, reason) pair is logged atomically,
        // so the consumer never sees a mismatch between this reason and the actual route.
        assertTrue(
            "plain-2d-video must always yield one of {CINEMA_IMMERSIVE, STANDARD_PANEL_FALLBACK}",
            withAuto.route == VrLaunchRoute.CINEMA_IMMERSIVE &&
                withoutAuto.route == VrLaunchRoute.STANDARD_PANEL_FALLBACK,
        )
    }

    // ── S0026 regression — lost-stereo-mode bug ──────────────────────────────
    //
    // Documents the inner-helper behaviour that the route flicker exploits when
    // VrPlayerActivity inherits a MONO default before BrowseEventHandler's hint
    // is consumed. With the F03 fix, requested=MONO will be replaced by the
    // detected mode before this helper is called. This test pins the helper's
    // contract for the unhinted path: requested=MONO + auto-immersive=on
    // STILL yields plain-2d-video, which is correct for genuinely 2D files.

    @Test
    fun `S0026 regression - requested MONO with auto-immersive on yields cinema immersive for video`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/file_appears_stereo.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.MONO,
            settings = AppSettings(vrAutoImmersive = true),
        )

        assertEquals(VrLaunchRoute.CINEMA_IMMERSIVE, decision.route)
        assertEquals("plain-2d-video", decision.logReason)
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

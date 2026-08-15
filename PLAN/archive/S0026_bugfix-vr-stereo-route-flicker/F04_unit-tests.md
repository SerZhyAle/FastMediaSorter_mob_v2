# Phase 04 — Unit tests

**Ticket:** S0026 / F04
**Goal:** lock the new contracts in CI. Test matrix:

- `BrowseRoutingDecision.decide` — full matrix `(video|image|other) × (disable3dVr) × (vrAutoImmersive on|off) × (stereo mode {MONO, SBS_FULL, OU, EQUIRECT_360_*, VR180_FISHEYE_SBS})`.
- `VrRouteDecisionHelperTest` (existing) — already covers the inner helper; extend with one targeted regression case for the lost-stereo-mode bug to prevent silent re-introduction.

**Files touched:**

1. **NEW** `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecisionTest.kt`
2. `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt` — append one regression test.

---

## Step 1 — Create `BrowseRoutingDecisionTest`

`BrowseRoutingDecision` lives in main source set, so test goes in `app_v2/src/test/...` (standard test sourceset).

```kotlin
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
        path = path,
        type = type,
        name = path.substringAfterLast('/'),
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
```

If the `MediaFile` data class has additional required parameters not shown above, fix the helper constructor accordingly — read `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MediaFile.kt` for the canonical constructor signature.

### Verification

- File exists at the path above.
- `pwsh -Command ".\gradlew.bat :app_v2:testStandardDebugUnitTest --tests com.sza.fastmediasorter.ui.browse.managers.BrowseRoutingDecisionTest"` — all tests PASS.

## Step 2 — Append regression test to `VrRouteDecisionHelperTest`

`app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt` — append at the end of the class (before closing brace and the `mediaFile` helper at the bottom, search for `// ── Phase` separators for context):

```kotlin
    // ── S0026 regression — lost-stereo-mode bug ──────────────────────────────
    // Documents the inner-helper behaviour that the route flicker exploits when
    // VrPlayerActivity inherits a MONO default before BrowseEventHandler's hint
    // is consumed. With the F03 fix, requested=MONO will be replaced by the
    // detected mode before this helper is called. This test is here to lock
    // the helper's contract for the unhinted path: requested=MONO + auto-immersive=on
    // STILL yields plain-2d-video, which is correct for genuinely 2D files.

    @Test
    fun `requested MONO with auto-immersive on yields plain-2d-video for video`() {
        val decision = helper.decide(
            currentFile = mediaFile("/movies/file_appears_stereo.mp4", MediaType.VIDEO),
            effectiveStereoMode = StereoMode.MONO,
            settings = AppSettings(vrAutoImmersive = true),
        )

        assertEquals(VrLaunchRoute.CINEMA_IMMERSIVE, decision.route)
        assertEquals("plain-2d-video", decision.logReason)
    }
```

This test pins existing behaviour: MONO + auto-immersive=on + video → `CINEMA_IMMERSIVE` with reason `plain-2d-video` (cinema quad, no stereo). The S0026 fix moves "actual stereo file with MONO requested" out of this code path entirely — the hint replaces MONO before the helper is called.

### Verification

- `Grep -n "S0026 regression" app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt` returns the new comment.
- `pwsh -Command ".\gradlew.bat :app_v2:testVrDebugUnitTest --tests com.sza.fastmediasorter.vr.helpers.VrRouteDecisionHelperTest"` — all tests PASS.

---

## Acceptance for F04

- New file `BrowseRoutingDecisionTest.kt` exists with 14 tests.
- `VrRouteDecisionHelperTest` has the new regression test.
- `:app_v2:testStandardDebugUnitTest` PASS (new tests included).
- `:app_v2:testVrDebugUnitTest` PASS (existing + new regression).
- Dev changelog: `.\scripts\add_to_dev_log.ps1 "app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecisionTest.kt;app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt" "S0026/F04" "Unit tests for BrowseRoutingDecision matrix + S0026 regression pin"`.

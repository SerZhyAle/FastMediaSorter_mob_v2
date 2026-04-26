# Phase 01 — Cinema Fallback for 2D Content

**Strategic spec:** [../spec_vr-input-reliability.md](../spec_vr-input-reliability.md)
**Status:** Implemented
**Pillar:** A — Cinema Fallback for 2D Content (ADR-1)

---

## Goal

When a user navigates to a plain 2D video file while inside an XR session, the app must NOT destroy the XR session and launch the standard player. Instead, it must switch the layer type to `QUAD_CINEMA` (flat screen in VR) and play the file in Cinema mode. The XR session remains alive.

---

## Steps

### Step 01.1 — Add `CINEMA_IMMERSIVE` to the playback route enum

**File:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrLaunchRoute.kt`

Add a new enum constant `CINEMA_IMMERSIVE` after `STANDARD_PANEL_FALLBACK`. KDoc: the XR session is kept alive; content is rendered in a QUAD_CINEMA layer.

```kotlin
internal enum class VrLaunchRoute {
    STANDARD_PANEL_FALLBACK,
    CINEMA_IMMERSIVE,          // 2D content played in QUAD_CINEMA layer; XR session stays alive
    IMMERSIVE_VIDEO,
    IMMERSIVE_STATIC_IMAGE,
    UNSUPPORTED_IMMERSIVE_WITH_MESSAGE,
}
```

**Verification:**

```text
Grep -pattern "CINEMA_IMMERSIVE" -path "app_v2/src/vr" → ≥1 match in VrLaunchRoute.kt
```

---

### Step 01.2 — Route plain 2D video to `CINEMA_IMMERSIVE` in the route decision helper

**File:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt`

In `decide()`, the current block that returns `STANDARD_PANEL_FALLBACK` for non-immersive content is:

```kotlin
val requestsImmersive = effectiveStereoMode.isSpherical() || effectiveStereoMode.isStereoscopic()
if (!requestsImmersive) {
    return VrRouteDecision(
        route = VrLaunchRoute.STANDARD_PANEL_FALLBACK,
        effectiveStereoMode = effectiveStereoMode,
        logReason = "plain-2d-content",
    )
}
```

Replace: if the current file is a VIDEO type (`currentFile.type == MediaType.VIDEO`), return `CINEMA_IMMERSIVE` with `StereoMode.MONO`. Non-video non-immersive content (images, audio) still falls through to `STANDARD_PANEL_FALLBACK`.

```kotlin
val requestsImmersive = effectiveStereoMode.isSpherical() || effectiveStereoMode.isStereoscopic()
if (!requestsImmersive) {
    if (currentFile.type == MediaType.VIDEO) {
        return VrRouteDecision(
            route = VrLaunchRoute.CINEMA_IMMERSIVE,
            effectiveStereoMode = StereoMode.MONO,
            logReason = "plain-2d-video-cinema",
        )
    }
    return VrRouteDecision(
        route = VrLaunchRoute.STANDARD_PANEL_FALLBACK,
        effectiveStereoMode = effectiveStereoMode,
        logReason = "plain-2d-content",
    )
}
```

**Verification:**

```text
Grep -pattern "CINEMA_IMMERSIVE" -path "app_v2/src/vr" -glob "*.kt" → ≥1 match in VrRouteDecisionHelper.kt
Grep -pattern "plain-2d-video-cinema" -path "app_v2/src/vr" → ≥1 match
```

---

### Step 01.3 — Handle `CINEMA_IMMERSIVE` route in VrPlayerActivity

**File:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`

In `resolvePlaybackRoute()`, the `when (route.route)` dispatch block currently has branches for `STANDARD_PANEL_FALLBACK`, `IMMERSIVE_VIDEO`, `IMMERSIVE_STATIC_IMAGE`, `UNSUPPORTED_IMMERSIVE_WITH_MESSAGE`. Add a branch for `CINEMA_IMMERSIVE`:

```kotlin
VrLaunchRoute.CINEMA_IMMERSIVE -> launchCinemaImmersive(route)
```

Add a new private method `launchCinemaImmersive(route: VrRouteDecision)`:

```kotlin
private fun launchCinemaImmersive(route: VrRouteDecision) {
    Timber.d("launchCinemaImmersive: switching to QUAD_CINEMA layer")
    applyStereoModeToVrRenderers(route.effectiveStereoMode, "cinema-immersive")
    startPlayerInCurrentActivity()
}
```

`startPlayerInCurrentActivity()` is the existing method that starts ExoPlayer playback without finishing the activity. If it does not exist by that name, use whatever method starts playback without calling `finish()` — check the `IMMERSIVE_VIDEO` branch for the correct call pattern.

**Verification:**

```text
Grep -pattern "CINEMA_IMMERSIVE" -path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" → ≥1 match
Grep -pattern "launchCinemaImmersive" -path "app_v2/src/vr" → ≥1 match
Grep -pattern "launchStandardPlayerFallback" -path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" → 0 matches for CINEMA_IMMERSIVE branch (must NOT call the fallback)
```

---

## Phase Done Criteria

- [ ] `VrLaunchRoute.CINEMA_IMMERSIVE` constant exists in the enum.
- [ ] `VrRouteDecisionHelper.decide()` returns `CINEMA_IMMERSIVE` for `MediaType.VIDEO` + non-immersive stereo.
- [ ] `VrPlayerActivity.resolvePlaybackRoute()` handles `CINEMA_IMMERSIVE` without calling `launchStandardPlayerFallback()`.
- [ ] No `VrLaunchRoute` switch has an unresolved `else`/`when` branch after adding the new constant.

# Phase 01 — Add VR_QUALITY_DEBUG Logging

**Status:** 🚧 In Progress
**Phase slug:** add-debug-logging
**Ticket:** S0041

---

## Goal

Add `VR_QUALITY_DEBUG` Timber log lines to surface the missing diagnostic data — selected ExoPlayer track format and fisheye render parameters — without altering any behaviour.

---

## Steps

### Step 1.1 — Backup VideoPlayerManager.kt (> 500 LOC rule)

**Status:** `[x] done`

**Files touched:**
- `temp/VideoPlayerManager_backup_<timestamp>.kt` (new)

**Prompt for developer:**
Create a timestamped backup copy:
```powershell
Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt" `
    "temp/VideoPlayerManager_backup_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt"
```

**Verification:**
- `Test-Path "temp/VideoPlayerManager_backup_*.kt"` returns `True`

---

### Step 1.2 — Log selected video track format in VideoPlayerManager

**Status:** `[x] done`

**Depends on:** Step 1.1

**Files touched:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`

**Prompt for developer:**
In `onTracksChanged` (around line 480), the code already obtains `videoFormat`. Add one `Timber.d` line immediately after `?.getTrackFormat(0)` and BEFORE the `if (videoFormat != null)` check:

Current code:
```kotlin
override fun onTracksChanged(tracks: Tracks) {
    val videoFormat = tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        ?.getTrackFormat(0)
    if (videoFormat != null) {
```

Replace with:
```kotlin
override fun onTracksChanged(tracks: Tracks) {
    val videoFormat = tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        ?.getTrackFormat(0)
    // WHY: VR_QUALITY_DEBUG — log selected track to diagnose S0041 pixelization regression.
    // Removed after root cause is confirmed (Фаза 2 of S0041 investigation).
    Timber.d("VR_QUALITY_DEBUG: selected track format=%s", videoFormat)
    if (videoFormat != null) {
```

**Verification:**
- `Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt" -Pattern "VR_QUALITY_DEBUG: selected track format"` — 1 match

---

### Step 1.3 — Log fisheye render parameters in VrStereoRenderer

**Status:** `[x] done`

**Files touched:**
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt`

**Prompt for developer:**
In `renderFisheyeQuad`, just before the `glUniform1f(fUFisheyeUOffsetLoc, fisheyeUOffset)` call, add a one-shot debug log guarded by `dbgRenderEyeCount == 0L`:

Current code pattern (at the end of renderFisheyeQuad binding block):
```kotlin
        GLES20.glUniform1i(fUTextureLoc, 0)
        GLES20.glUniform1f(fUFisheyeUOffsetLoc, fisheyeUOffset)
```

Insert before the glUniform1f line:
```kotlin
        GLES20.glUniform1i(fUTextureLoc, 0)
        // WHY: VR_QUALITY_DEBUG — one-shot log to confirm fisheye dimensions and uniform value.
        // dbgRenderEyeCount == 0L means this fires on the first ever renderFisheyeQuad call.
        // Removed after S0041 investigation is complete.
        if (dbgRenderEyeCount == 0L) {
            Timber.d(
                "VR_QUALITY_DEBUG: fisheye first frame uOffset=%.2f target=%dx%d fisheyeProgram=%d",
                fisheyeUOffset, targetWidthPx, targetHeightPx, fisheyeProgram
            )
        }
        GLES20.glUniform1f(fUFisheyeUOffsetLoc, fisheyeUOffset)
```

Note: `fisheyeProgram` is a field of `VrStereoRenderer` — already in scope.

**Verification:**
- `Select-String -Path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt" -Pattern "VR_QUALITY_DEBUG: fisheye first frame"` — 1 match

---

## Phase Done Criteria

- [x] 1. `VideoPlayerManager.kt` contains `VR_QUALITY_DEBUG: selected track format` (Grep).
- [x] 2. `VrStereoRenderer.kt` contains `VR_QUALITY_DEBUG: fisheye first frame` (Grep).
- [x] 3. BUILD-REQUIRED — project compiles (auto-build — PASS, standard debug v2.60.4301.658 + vr debug v2.60.4301.700, 2026-04-30).

---

## Step Log

<!-- append entries after each step completes -->

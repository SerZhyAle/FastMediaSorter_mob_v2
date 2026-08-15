# Phase 03 — Analyze Log and Fix

**Status:** ⬜ Not Started
**Phase slug:** analyze-and-fix
**Ticket:** S0041
**Depends on:** Phase 02 complete (log file with VR_QUALITY_DEBUG entries)

---

## Goal

Read the new device log, determine root cause of the pixelization regression using the VR_QUALITY_DEBUG entries, and apply the targeted fix.

---

## Steps

### Step 3.1 — Analyze selected track format [MANUAL]

**Status:** `[ ] not done`

**Prompt for developer:**
Run:
```powershell
.\scripts\utils\search-log.ps1 -LogFile "logs/fastmediasorter_vr_quality_debug_<date>.log" -Pattern "VR_QUALITY_DEBUG"
```

Look for `VR_QUALITY_DEBUG: selected track format=...` line. Check:
- `width` and `height` fields — should be 7168×3584 for the 7K file.
- If width/height are lower (e.g. 1920×960 or 3840×1920) → ExoPlayer selected a lower-quality track → proceed to Step 3.2A.
- If width/height are 7168×3584 → track selection is correct → check fisheye uniforms in Step 3.2B.

**Verification:**
- MANUAL-REQUIRED. Document finding in `## Last Audit` of `PLAN/S0041_debug-vr180-fisheye-quality-regression.md`.

---

### Step 3.2A — Fix: force max-quality track selection [CONDITIONAL — only if Step 3.1 found wrong track]

**Status:** `[ ] not done`

**Files touched:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`

**Prompt for developer:**
In `VideoPlayerManager` where ExoPlayer is configured (search for `TrackSelectionParameters` or `setTrackSelectionParameters`), add:
```kotlin
.setTrackSelectionParameters(
    player.trackSelectionParameters.buildUpon()
        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        .setMaxVideoBitrate(Int.MAX_VALUE)
        .build()
)
```
This forces ExoPlayer to prefer the highest available video track.

**Verification:**
- `Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt" -Pattern "setMaxVideoSize"` — 1 match.

---

### Step 3.2B — Investigate fisheye shader params [CONDITIONAL — only if Step 3.1 found correct track]

**Status:** `[ ] not done`

**Prompt for developer:**
Look for `VR_QUALITY_DEBUG: fisheye first frame` in the log. Check `target=WxH` — should be ≈ 1680×1760 per eye (Quest 3 swapchain).

If dimensions look correct:
1. Run `git log --oneline -- app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt` — find commits since last known-good VR180 session.
2. `git diff <last-good-commit>..HEAD -- app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt` — review fisheye shader changes.
3. Document findings in `## Last Audit`.

**Verification:**
- MANUAL-REQUIRED. Root cause documented in `## Last Audit`.

---

### Step 3.3 — Apply fix and update Last Audit [MANUAL]

**Status:** `[ ] not done`

**Depends on:** Step 3.1 + either 3.2A or 3.2B

**Prompt for developer:**
After root cause is known:
1. Apply the fix (per 3.2A or 3.2B finding).
2. Update `## Last Audit` in `PLAN/S0041_debug-vr180-fisheye-quality-regression.md` with: cause, fix applied, date.
3. Run dev log:
```powershell
.\scripts\add_to_dev_log.ps1 "<fixed-file>" "<fixed-class>" "S0041: fix VR180 quality regression — <cause>"
```

**Verification:**
- MANUAL-REQUIRED. User confirms on Quest 3 that pixelization is gone.

---

## Phase Done Criteria

- [ ] 1. [MANUAL] `## Last Audit` in strategic spec updated with root cause finding.
- [ ] 2. [MANUAL] Fix applied and logged in `dev/CHANGELOG.md`.
- [ ] 3. [MANUAL] User confirmed on Quest 3: no pixelization.

---

## Step Log

<!-- append entries after each step completes -->

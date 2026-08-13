# Phase 01 — Group A: Stereo Rendering

**Strategic spec:** [`../S0132_vr-quest3-epic-pending-verification.md`](../S0132_vr-quest3-epic-pending-verification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Verify SBS layer routing (ex-S0078) and combined stereo format regression (ex-S0012) on Quest 3; diagnose and fix VR180 7K fisheye pixelation (ex-S0041) through a two-step debug→fix cycle.

---

## Prerequisites

- [ ] Quest 3 connected and developer mode enabled.
- [ ] VR build installed from current branch.
- [ ] Reference file `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` accessible from the device (local or SMB).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt` | Modified | ≤ 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt` | Modified (verify only — no code change expected unless test reveals regression) | — |

> Step 01.2b may touch additional files once the log analysis selects a hypothesis. Update this table when the hypothesis is confirmed.

---

## Steps

### Step 01.1 — Verify SBS_FULL / SBS_HALF → PROJECTION layer on Quest 3

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt` (read only)
**Depends on:** — start of phase

**Prompt for developer:**

> Open an SBS_FULL file and an SBS_HALF file in immersive mode on Quest 3. Confirm each eye receives a distinct half of the frame (real stereo depth), not a full-width duplicate. Check logcat for the marker `renderQuad first stereo=SBS_FULL layer=PROJECTION` and `uScale=0.5`. Run a regression check: open OU and VR180 fisheye files and confirm they still render correctly.

**Verification:**

- On-device observation: left eye sees left half of frame, right eye sees right half — no flat side-by-side duplication.
- Logcat: `Grep -n "renderQuad.*SBS_FULL.*PROJECTION"` present in saved session log.
- Logcat: `Grep -n "uScale=0.5"` present in saved session log.
- OU regression: both eyes show distinct vertical halves (real stereo, not flat).

**Status:** `[ ]` not done

---

### Step 01.2a — Add VR_QUALITY_DEBUG diagnostic logging for fisheye quality

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt`
**Depends on:** — start of phase (parallel to 01.1)

**Prompt for developer:**

> Add `Timber.d("VR_QUALITY_DEBUG: fisheye uniforms fov=<value> uvOffset=<value> uvScale=<value>")` in `VrStereoRenderer` at the point where fisheye shader uniforms are set (just before the GL uniform upload call). Add `Timber.d("VR_QUALITY_DEBUG: swapchain format=<format>")` in `OpenXrSessionManager` where the video swapchain is created. Add `Timber.d("VR_QUALITY_DEBUG: selected track format resolution=<W>x<H> codec=<codec>")` in the ExoPlayer track selection callback (locate via `Grep -n "onTracksChanged\|onTrackSelected\|AnalyticsListener"` in the vr source set). Do not change any rendering logic. Commit the logging additions before running the test session.

**Verification:**

- `Grep -n "VR_QUALITY_DEBUG: fisheye uniforms"` — matches exactly once per playback start in `VrStereoRenderer.kt`.
- `Grep -n "VR_QUALITY_DEBUG: swapchain format"` — present in `OpenXrSessionManager.kt`.
- `Grep -n "VR_QUALITY_DEBUG: selected track format"` — present in the identified track-selection file.
- `Grep -n "Log\.d\("` — zero hits in any file modified by this step.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS. Files: `VrStereoRenderer.kt` (re-worded existing line to satisfy spec marker), `OpenXrSessionManager.kt` (+9 LOC). VideoPlayerManager.kt track-format line was already in place. Dev log recorded.

---

### Step 01.2b — Fix fisheye pixelation based on log analysis

**Files:** determined by hypothesis selected after 01.2a log analysis (update table above)
**Depends on:** Step 01.2a + on-device log collection session

**Prompt for developer:**

> Collect logcat from a 7K VR180 session opened with the reference file. Read the `VR_QUALITY_DEBUG` lines. Select the hypothesis from strategic §6 (A, C, D, or E) that the log data supports:
> - Hypothesis A (UV params): adjust `fov`, `uvOffset`, `uvScale` constants in `VrStereoRenderer` to match the fisheye lens geometry.
> - Hypothesis D (centralAngle): change `centralAngle` from `π` to the value that matches the actual fisheye FOV (e.g. `π/2` for 90° fisheye optics, check lens spec).
> - Hypothesis C (video effects): verify `SurfaceTexture` scale matrix is identity; if not, correct the transform before passing to the shader.
> - Hypothesis E (sRGB swapchain): change swapchain format from `GL_SRGB8_ALPHA8` to `GL_RGBA8` in `OpenXrSessionManager` swapchain creation.
> Apply only the changes dictated by the log evidence. One commit per hypothesis tested.

**Verification:**

- On-device observation: reference 7K file shows sharp image without pixel blocks ("кубики") at normal viewing distance.
- Other VR180 files (lower resolution) show no regression.
- `Grep -n "Log\.d\("` — zero hits in any file modified by this step.

**Status:** `[ ]` not done

---

### Step 01.3 — Combined stereo format regression test (VR180 / SBS / OU)

**Files:** none — on-device test session only
**Depends on:** Step 01.2b

**Prompt for developer:**

> Run a soak session covering all stereo formats. For each: open in immersive mode, confirm stereo depth is present, FPS ≥ 72 throughout. Formats: VR180 fisheye SBS (reference 7K file), SBS_FULL, SBS_HALF, OU/TAB, Half-OU, Half-SBS (if test files available). Test both local storage and SMB/SFTP sources for VR180.

**Verification:**

- On-device observation: each format shows real stereo depth (left/right eye divergence visible).
- FPS metric (from S0006 HUD or external tool): ≥ 72 fps during playback of each format.
- No format falls back to flat QUAD_CINEMA rendering unexpectedly.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` if any `.kt` changed.

---

## Handoff Notes to Next Phase

- Phase 02 (`group-b-immersive-ui`) may begin after this phase. Per strategic §15, the ray visual (Phase 02 step 02.1) is a prerequisite for the interactive panel UX test (step 02.4) — within Phase 02 those steps must be sequential.
- If hypothesis E (sRGB swapchain) was applied in 01.2b: note the swapchain format change in the Phase 06 dev log entry.

---

## Rollback Plan

Steps 01.1, 01.3 — on-device verification only; no code change, no rollback needed.

Steps 01.2a, 01.2b — revert the logging/fix commits. No Room migration, no data migration. Rendering returns to pre-step state.

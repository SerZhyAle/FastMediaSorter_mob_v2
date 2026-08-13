# Phase 01 - Panel Playback Fix

**Strategic spec:** [`../S0264_panel-stereo-crop-fix.md`](../S0264_panel-stereo-crop-fix.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Make the panel video path reliably apply single-eye stereo crop while suppressing the redundant VR-install CTA in VR-capable builds.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Existing inline comments in touched player files have been read before edits.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlaybackControlsHelper.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PanelStereoCropApplier.kt` | New | ≤ 250 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Gate VR CTA by build capability

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Prevent the VR install CTA from firing in VR-capable builds while preserving the existing CTA behavior for flat 3D detection in standard builds. Keep the change local to the playback callback and do not introduce a new UI path.

**Verification:**

- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` contains `BuildConfig.SUPPORT_VR_PLAYER`
- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` contains `showVrInstallCta`
- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` returns zero hits for `Log.d(`

**Status:** `[x]` done

---

### Step 01.2 - Route panel single-eye crop through TextureView transform

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlaybackControlsHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PanelStereoCropApplier.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Keep the stereo mode state machine intact, but make the visible single-eye crop come from a `TextureView` transform that survives relayouts and first-frame timing. The previous GL crop path may remain as a no-op/documented limitation, but the panel result must be driven by the TextureView-backed surface.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PanelStereoCropApplier.kt` exists
- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlaybackControlsHelper.kt` contains `PanelStereoCropApplier.apply`
- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` contains `PanelStereoCropApplier.reset`
- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt` contains `PanelStereoCropApplier`

**Status:** `[x]` done

---

### Step 01.3 - Validate panel playback fix on mandatory build set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlaybackControlsHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PanelStereoCropApplier.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Run the mandatory build validation for the bugfix and record the exact commands and results. Treat `standard` as the control build and one VR-capable build as the required capability build.

**Verification:**

- `/build` - `standard debug` passes
- `/build` - `noLegal debug` passes
- `Grep` - all files listed in this phase return zero hits for `Log.d(`

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Panel playback path now owns the visible single-eye crop and CTA gating behavior. Phase 02 only needs to align persisted defaults and localized copy with that runtime behavior.

---

## Rollback Plan

Revert phase commit(s) - no schema migration or irreversible data change.

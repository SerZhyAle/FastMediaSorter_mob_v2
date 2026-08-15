# Phase 02 — Failure Signal Wiring

**Strategic spec:** [../S0188_slideshow-stop-on-resource-unavailable.md](../S0188_slideshow-stop-on-resource-unavailable.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Wire all slideshow-relevant success/failure signals into the helper: manual navigation, image load outcomes, video playback callbacks, and audio-service callbacks.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationManager.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerKeyboardCallbackImpl.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerGestureCallbackImpl.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 60 |

> File projected >500 lines after edit → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 — Reset failure state on manual navigation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerKeyboardCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerGestureCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Route all manual next/previous/random navigation triggers through `PlayerNavigationManager` and call the new activity reset hook before `manual = true` navigation mutates `PlayerViewModel` state.

**Verification:**

- `Grep` — `onManualSlideshowNavigation()` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationManager.kt`.
- `Grep` — `navigateNextFromGesture()` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerGestureCallbackImpl.kt`.
- `Grep` — `navigateNextFromButton()` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: PlayerNavigationManager.kt, PlayerKeyboardCallbackImpl.kt, PlayerGestureCallbackImpl.kt, PlayerControlsSetupManager.kt. Dev log recorded.

---

### Step 02.2 — Forward image load success and transport failures into the helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend `ImageLoadingManager.ImageLoadingCallback` with a success hook that fires for both static images and GIFs. Use `PlayerImageLoadingCallbackImpl` to suppress the generic activity error when the helper reaches the S0188 threshold, while preserving the existing single-file skip behavior below threshold.

**Verification:**

- `Grep` — `fun onImageContentLoaded()` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt`.
- `Grep` — `callback.onImageContentLoaded()` matches twice in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt`.
- `Grep` — `handleImageLoadFailure` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: PlayerImageLoadingCallbackImpl.kt, ImageLoadingManager.kt. Dev log recorded.

---

### Step 02.3 — Forward video and audio-service failures into the helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Call the helper on playback ready, playback error, and playback ended for both `VideoPlayerManager` and the audio-service callbacks created in `PlayerManagerInitializer`. Keep the existing skip path when the threshold is not reached; stop slideshow instead when the helper says the resource is unavailable.

**Verification:**

- `Grep` — `slideshowResourceAvailabilityManager.onPlaybackReady()` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`.
- `Grep` — `slideshowResourceAvailabilityManager.handlePlaybackEnded()` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`.
- `Grep` — `slideshowResourceAvailabilityManager.handlePlaybackError` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: PlayerPlaybackCallbackImpl.kt, PlayerManagerInitializer.kt. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 03 only needs user-facing copy, strategic/tactical bookkeeping, changelog entries, catalog regeneration, string audit, and a standard debug build.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
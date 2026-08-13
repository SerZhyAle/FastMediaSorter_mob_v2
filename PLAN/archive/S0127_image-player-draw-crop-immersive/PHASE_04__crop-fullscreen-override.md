# Phase 04 — Crop-to-Fullscreen Override

**Strategic spec:** [`../S0127_image-player-draw-crop-immersive.md`](../S0127_image-player-draw-crop-immersive.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** —
**Steps done:** 2 / 2
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

When `imageEditMode == CROP`, the displayed image must use `FIT_CENTER` scale type so the user can crop the full image surface. The user's `cropImagesToFullscreen` setting is preserved (not mutated).

---

## Prerequisites

- [ ] Phase 03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` | Modified | ≤ 1300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCropDelegate.kt` | Modified | ≤ 200 |

> `ImageLoadingManager.kt` is 1263 LOC — backup required before edit.

---

## Steps

### Step 04.1 — Override scale type when entering Crop

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCropDelegate.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `PlayerCropDelegate.kt`, locate `enterCropMode(mode: ImageCropManager.CropMode)`. Immediately after the existing `imageCropManager.enterCropMode(mode, file, resource, imageCropCallback)` line (and before `showCropOverlay(mode)`), add:
> ```kotlin
> Timber.d("S0127: PlayerCropDelegate forcing FIT_CENTER on photoView/imageView for crop")
> activity.activityBinding.photoView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
> activity.activityBinding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
> ```
> Do not introduce any new branches or remove existing logic.

**Verification:**

- `Grep` — `Timber.d("S0127: PlayerCropDelegate forcing FIT_CENTER` matches once.
- `Grep` — `activity.activityBinding.photoView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER` matches once.
- `Grep` — `activity.activityBinding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCropDelegate.kt (+3 LOC). Dev log recorded.

---

### Step 04.2 — Suppress `cropImagesToFullscreen` while imageEditMode == CROP in `ImageLoadingManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `ImageLoadingManager.kt`, locate the line `currentCropSetting = settings.cropImagesToFullscreen` (it appears once, around line 625). Replace the right-hand side with a guarded expression so that, when the player ViewModel reports CROP edit mode, the setting is treated as `false`:
> ```kotlin
> val isCropEditMode = callback.isImageCropEditMode()
> currentCropSetting = settings.cropImagesToFullscreen && !isCropEditMode
> Timber.d("S0127: ImageLoadingManager.displayImage cropSetting=${settings.cropImagesToFullscreen} isCropEditMode=$isCropEditMode → effective=$currentCropSetting")
> ```
> Then locate the line `val initialScaleType = if (settings.cropImagesToFullscreen && isFullscreenOrSlideshow) {` (it follows shortly after). Replace the condition's `settings.cropImagesToFullscreen` with `currentCropSetting`.
> Then in the same file, locate the existing `interface ImageLoadingCallback {` declaration (around line 77). Add a new method to that interface: `fun isImageCropEditMode(): Boolean`. Place it after the existing `isSlideshowActive()` method.
> Then update the single concrete implementation `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt`. After the existing `isSlideshowActive()` override, add:
> ```kotlin
> override fun isImageCropEditMode(): Boolean =
>     activity.viewModel.state.value.imageEditMode == com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.CROP
> ```
> Do not modify other overrides.

**Verification:**

- `Grep` — `val isCropEditMode = callback.isImageCropEditMode()` matches once in `ImageLoadingManager.kt`.
- `Grep` — `currentCropSetting = settings.cropImagesToFullscreen && !isCropEditMode` matches once.
- `Grep` — `fun isImageCropEditMode(): Boolean` matches once in `ImageLoadingManager.kt`.
- `Grep` — `override fun isImageCropEditMode(): Boolean` matches once in `PlayerImageLoadingCallbackImpl.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt (+3 LOC), app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt (+3 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` BUILD SUCCESSFUL in 29s.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 05 enables pinch-to-zoom passthrough so the user can zoom inside Crop mode.

---

## Rollback Plan

Revert the phase commit(s). The user setting was never mutated.

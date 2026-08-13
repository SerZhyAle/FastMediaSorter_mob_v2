# Phase 05 — Pinch Passthrough

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

`CropOverlayView` must release multi-pointer touch sequences so the underlying `PhotoView` can handle pinch-to-zoom and two-finger pan. Single-pointer drag continues to manipulate the crop rectangle.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/CropOverlayView.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCropDelegate.kt` | Modified | ≤ 200 |

---

## Steps

### Step 05.1 — Inject `pinchPassthroughTarget` into `CropOverlayView`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/CropOverlayView.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `CropOverlayView.kt`, add a public `var pinchPassthroughTarget: View? = null` property near the top of the class (after `cropChangedListener`). Then modify `onTouchEvent(event: MotionEvent)`:
> - Add at the very top of the method body, before the `when (event.actionMasked)` switch:
>   ```kotlin
>   if (event.pointerCount >= 2 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
>       Timber.d("S0127: CropOverlayView routing multi-pointer event to passthrough target")
>       dragTarget = DragTarget.NONE
>       pinchPassthroughTarget?.dispatchTouchEvent(event)
>       return false
>   }
>   ```
> - Add the import `import timber.log.Timber` at the top of the file if it is not already present.
> Do not modify the per-action branches or the existing handle-hit logic.

**Verification:**

- `Grep` — `var pinchPassthroughTarget: View? = null` matches once.
- `Grep` — `event.pointerCount >= 2 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN` matches once.
- `Grep` — `pinchPassthroughTarget?.dispatchTouchEvent(event)` matches once.
- `Grep` — `Timber.d("S0127: CropOverlayView routing multi-pointer event to passthrough target")` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/CropOverlayView.kt (+9 LOC). Dev log recorded.

---

### Step 05.2 — Wire `pinchPassthroughTarget` in `PlayerCropDelegate`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCropDelegate.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> In `PlayerCropDelegate.kt`, locate the existing `showCropOverlay(mode: ImageCropManager.CropMode)` method. Just after the line `val cropView = overlay.findViewById<CropOverlayView>(R.id.crop_overlay_view)` and before `val btnConfirm = ...`, insert:
> ```kotlin
> cropView.pinchPassthroughTarget = activity.activityBinding.photoView
> Timber.d("S0127: PlayerCropDelegate wired pinchPassthroughTarget=photoView")
> ```
> Do not modify other code in this method.

**Verification:**

- `Grep` — `cropView.pinchPassthroughTarget = activity.activityBinding.photoView` matches once.
- `Grep` — `Timber.d("S0127: PlayerCropDelegate wired pinchPassthroughTarget=photoView")` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCropDelegate.kt (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` BUILD SUCCESSFUL in 29s.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Final phase covers FEATURES trilingual update, catalog regen, dev log audit.

---

## Rollback Plan

Revert the phase commit(s); behavior reverts to single-pointer-only crop overlay (existing crop UX still works).

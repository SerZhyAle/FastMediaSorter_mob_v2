# Phase 04 - Apply and Host Wiring

**Strategic spec:** [`../S0679_draw-editor-crop-tool.md`](../S0679_draw-editor-crop-tool.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Wire crop-confirm to the compositor in both image hosts (in-app player and standalone), swap the working image to the cropped composite, reset the canvas, and make crop reversible until the first subsequent stroke (ADR-4). After this phase the full scenario works: select crop -> select region -> confirm -> keep drawing -> save/share emits the cropped result.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (DrawCropCompositor available).
- [ ] Phase 03 ✅ Done (`cropApplyCallback` seam available).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDrawingSaveHelper.kt` | Modified | ≤ 680 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandaloneDrawSaveHelper.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | - |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

> `ImageDrawOverlayManager.kt` is >500 LOC - timestamped backup in `temp/` before editing.

---

## Steps

### Step 04.1 - Canvas reset + reversible-until-first-stroke (ADR-4)

**Files:** `ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up the file first. Add `fun beginCropUndo(restoreBase: () -> Unit)`: snapshot the current `actions` list, clear the canvas actions (they are baked into the new cropped base), store `restoreBase` as a pending crop-undo. The base image swap itself is performed host-side (Step 04.2/04.3). Extend the canvas undo path so that when `actions` is empty and a pending crop-undo exists, `undoLast`/`undoAll` restores the snapshotted actions and invokes `restoreBase()`, then clears the pending crop-undo. The first new draw action added after a crop (`ACTION_DOWN` stroke / shape / text commit) commits the crop by clearing the pending crop-undo. Keep `getOverlayBitmap()` (existing) as the overlay source for the compositor.

**Verification:**

- `Grep` - `fun beginCropUndo(` matches once.
- `Grep` - the undo path references the pending crop-undo field (restore branch) and a commit-on-mutation clear.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification PASS (beginCropUndo present - manager delegate + DrawCanvasView impl; undoLast/undoAll restore via pendingCropRestore; noteMutation commits crop; fk SUCCESSFUL). Also reverts tool to drawing on crop-confirm so the session resumes. File: ui/player/helpers/ImageDrawOverlayManager.kt.

---

### Step 04.2 - Wire crop apply in the in-app player

**Files:** `ui/player/helpers/PlayerDrawingSaveHelper.kt`, `ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `PlayerDrawingSaveHelper`, add `fun setupDrawCropCallback()` that builds a `DrawCropCompositor(activity.imageCropManager, activity.mergeDrawOverlayUseCase)` and assigns `imageDrawOverlayManager.cropApplyCallback`. On callback: read `baseBitmap = activity.viewModel.currentDisplayedBitmap`, `overlay = imageDrawOverlayManager.getOverlayBitmap()`, `displayRect = activity.activityBinding.photoView.displayRect`, `currentFile` + `resource` from the view-model state. Map the normalised overlay rect (callback arg) into canvas/selection coordinates, then `lifecycleScope.launch { compose -> on success swap the displayed image }`. Swap = set the new cropped bitmap into `activity.activityBinding.photoView` and update `activity.viewModel.currentDisplayedBitmap` (add a view-model setter if absent), then call `imageDrawOverlayManager.beginCropUndo { /* restore previous photoView image + currentDisplayedBitmap */ }` capturing the previous base before the swap. On failure show `R.string.draw_crop_failed` and leave the working image unchanged. Call `setupDrawCropCallback()` from `PlayerManagerInitializer` right after `bindToolbar()` / the existing draw-callback setup. Do all bitmap work off the main thread; no broad `catch {}` without a logged fallback.

**Verification:**

- `Grep` - `fun setupDrawCropCallback(` matches once in `PlayerDrawingSaveHelper.kt`.
- `Grep` - `imageDrawOverlayManager.cropApplyCallback` assigned in that function.
- `Grep` - `DrawCropCompositor(` constructed there.
- `Grep` - `setupDrawCropCallback()` called in `PlayerManagerInitializer.kt`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 5/5 PASS (setupDrawCropCallback x1; cropApplyCallback assigned; DrawCropCompositor constructed; called in PlayerManagerInitializer; fk SUCCESSFUL). Uses actionCurrentFile/Resource; swaps photoView + currentDisplayedBitmap; beginCropUndo restores previous base. Files: PlayerDrawingSaveHelper.kt, PlayerManagerInitializer.kt.

---

### Step 04.3 - Mirror crop apply in the standalone image host

**Files:** `ui/player/standalone/StandaloneDrawSaveHelper.kt`, `ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Mirror Step 04.2 for the standalone host (players are a family - per-host glue must be mirrored manually). Wire `imageDrawOverlayManager.cropApplyCallback` to the same `DrawCropCompositor`, using the standalone host's base bitmap accessor, its image view, and its source `MediaFile`/`MediaResource`. Swap the standalone image view to the cropped composite, update its displayed-bitmap reference, and call `beginCropUndo { .. }` with the standalone restore. Register the wiring where the standalone sets up its draw callbacks (alongside the existing `cropDelegate` / draw setup in `PhotoVideoStandaloneActivity`). Reuse `R.string.draw_crop_failed` on failure.

**Verification:**

- `Grep` - `cropApplyCallback` assigned in `StandaloneDrawSaveHelper.kt` (or `PhotoVideoStandaloneActivity.kt`).
- `Grep` - `DrawCropCompositor(` constructed in the standalone wiring.
- `Grep` - `beginCropUndo(` referenced in the standalone wiring.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification PASS (cropApplyCallback assigned; DrawCropCompositor constructed; `manager.beginCropUndo { .. }` trailing-lambda form referenced; fk SUCCESSFUL). Added imageCropManager + setDisplayedBitmap params; currentResource null (no resource context). Files: StandaloneDrawSaveHelper.kt, PhotoVideoStandaloneActivity.kt.

---

### Step 04.4 - Crop-failure string (trilingual)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add `draw_crop_failed` via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key draw_crop_failed -En "Couldn't crop the image" -Ru "Не удалось обрезать изображение" -Uk "Не вдалося обрізати зображення"`. Calm, plain, no jargon - must pass COMMUNICATION_POLICY §6.

**Verification:**

- `Grep` - `name="draw_crop_failed"` matches once in each of the three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_crop_failed"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 3/3 PASS (draw_crop_failed in EN/RU/UK; parity exit 0; calm plain copy). Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` SUCCESSFUL.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] MANUAL (BlockNeedUserTest) - On-device: select Crop in draw mode -> adjust rectangle -> confirm -> image (and prior annotations) cropped -> draw again -> Save/Share/Save-in-place emits the cropped result. Crop undo works before the first new stroke. Debug tags `S0679:` inserted at crop-apply entries (both hosts).
- [ ] Dev log entry added for every file in "Files Touched". (batched at ticket close)
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 05).

---

## Handoff Notes to Next Phase

Feature is functionally complete in both hosts. Phase 05 regenerates the catalog, records the capability in `docs/ALL_FEATURES.jsonl`, and finalises dev logs. The on-device verification above is the basis for the `BlockNeedUserTest` note `/spec-dev` will set.

---

## Rollback Plan

Revert phase commit(s). The compositor and overlay from Phases 02/03 remain inert without the callback wiring; no data migration or persisted change.

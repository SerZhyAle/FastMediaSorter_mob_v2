# Phase 02 - Crop Compositor

**Strategic spec:** [`../S0679_draw-editor-crop-tool.md`](../S0679_draw-editor-crop-tool.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Add the bitmap engine that turns a crop selection into a new cropped working composite: full resolution for local files (region-decode the source), display-resolution fallback otherwise, with existing annotations baked in. No UI.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Read [`research/01__crop-resolution-strategy.md`](research/01__crop-resolution-strategy.md) before implementing - it fixes the resolution strategy (§6.1).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageCropManager.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawCropCompositor.kt` | New | ≤ 220 |

---

## Steps

### Step 02.1 - Add region-decode-to-bitmap to the crop engine

**Files:** `ui/player/helpers/ImageCropManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a public `suspend fun decodeCroppedRegionBitmap(screenRect: RectF, viewWidth: Int, viewHeight: Int, currentFile: MediaFile, currentResource: MediaResource?): Bitmap` that returns the cropped region as an in-memory bitmap WITHOUT writing any file. Reuse the existing private pipeline: `ensureLocalSource` -> `mapScreenRectToOriginal` -> `decodeRegion` -> `rotateBitmapIfNeeded(readExifDegrees)`. Clean up any temp source file in a `finally`. This is the full-resolution path for local/network-materialised sources. Throwing is acceptable - the caller (DrawCropCompositor) catches and falls back.

**Verification:**

- `Grep` - `fun decodeCroppedRegionBitmap(` matches once in `ImageCropManager.kt`.
- `Grep` - the new function body references `decodeRegion(` and `rotateBitmapIfNeeded(`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 3/3 PASS (decodeCroppedRegionBitmap x1; refs decodeRegion + rotateBitmapIfNeeded; fk exit 0). File: ui/player/helpers/ImageCropManager.kt.

---

### Step 02.2 - Create `DrawCropCompositor`

**Files:** `ui/player/helpers/DrawCropCompositor.kt` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class DrawCropCompositor(private val cropManager: ImageCropManager, private val mergeDrawOverlayUseCase: MergeDrawOverlayUseCase)`. Expose `suspend fun composeCroppedWorkingImage(...)` returning the new working composite `Bitmap`. Inputs: the current base bitmap, the draw-canvas overlay bitmap, the image `displayRect` (RectF, canvas coords), the user crop selection rect (RectF, canvas coords), canvas width/height, and the source `MediaFile` + `MediaResource?`. Algorithm: (1) intersect the selection with `displayRect` and clamp; (2) normalise the intersection to image space (0..1 over `displayRect`); (3) if the source is a local file, call `cropManager.decodeCroppedRegionBitmap(normalisedRect, ..)` for the full-res base, else crop the base bitmap by the normalised rect; (4) crop the overlay bitmap to the same selection region and scale it to the base-crop dimensions; (5) merge via `mergeDrawOverlayUseCase.execute(croppedBase, scaledOverlay, format)` and return the result. On any failure in the full-res path, fall back to cropping the working base bitmap (display resolution). Do all bitmap work off the main thread (`Dispatchers.Default`/`IO`); recycle intermediate bitmaps that are not returned. No `catch {}` without a logged fallback (Rule 19).

**Verification:**

- `Glob` - `ui/player/helpers/DrawCropCompositor.kt` exists.
- `Grep` - `class DrawCropCompositor` matches once.
- `Grep` - `suspend fun composeCroppedWorkingImage(` present.
- `Grep` - body references `decodeCroppedRegionBitmap(` and `mergeDrawOverlayUseCase`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 5/5 PASS (file exists; class x1; composeCroppedWorkingImage x1; refs decodeCroppedRegionBitmap + mergeDrawOverlayUseCase; fk exit 0). New file: ui/player/helpers/DrawCropCompositor.kt. Merge uses PNG intermediate (lossless), guards bitmap aliasing on whole-image fallback.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched". (batched at ticket close)
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (deferred to Phase 05; new class `DrawCropCompositor`).

---

## Handoff Notes to Next Phase

`DrawCropCompositor.composeCroppedWorkingImage` is the single entry point Phase 04 calls on crop-confirm. It returns a ready-to-display cropped working bitmap with annotations baked in. The selection rect it expects is in canvas coordinates (same space as `photoView.displayRect`).

---

## Rollback Plan

Revert phase commit(s) - new class and one additive method; no call sites yet, no user-facing surface.

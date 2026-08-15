# Phase 03 - Selection Overlay

**Strategic spec:** [`../S0679_draw-editor-crop-tool.md`](../S0679_draw-editor-crop-tool.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

When the CROP tool is active in draw mode, show the draggable rectangle selection overlay (reused from the player crop) with confirm/cancel affordances, and expose a confirm callback. No crop is applied yet (callback unwired until Phase 04).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawCropOverlayController.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 860 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/CropOverlayView.kt` | Modified | ≤ 320 |

> `ImageDrawOverlayManager.kt` is >500 LOC - timestamped backup in `temp/` before editing.
> Reused layout `app_v2/src/main/res/layout/player_crop_overlay_content.xml` already provides `crop_overlay_view`, `btn_crop_confirm`, `btn_crop_cancel` - no portrait/landscape layout edit needed (overlay is a content layer, not an orientation-specific screen).

---

## Steps

### Step 03.1 - Accessibility parity for the reused crop overlay view

**Files:** `ui/player/views/CropOverlayView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The player `CropOverlayView` is touch-only. Add keyboard / D-pad adjustment and a content description so the crop selection meets Strict Rule 16/17 (mirror the camera-OCR `CropOverlayView` which already handles `KEYCODE_DPAD_*` and sets `contentDescription`). Make the view focusable in touch mode, move/resize the rect on arrow keys, toggle move/resize on D-pad center, and set a content description from a string resource. Reuse the existing `draw_tool_crop`-adjacent string set; if a new description string is needed, add it trilingually via `set-android-string.ps1 -Action add` and pass COMMUNICATION_POLICY §6.

**Verification:**

- `Grep` - `onKeyDown` present in `ui/player/views/CropOverlayView.kt`.
- `Grep` - `isFocusableInTouchMode` (or `isFocusable = true`) present.
- `Grep` - `contentDescription` set in that file.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 4/4 PASS (onKeyDown x1; isFocusableInTouchMode; contentDescription; fk SUCCESSFUL). Added `draw_crop_frame_desc` (EN/RU/UK), KeyEvent import, focus init, D-pad nudge. File: ui/player/views/CropOverlayView.kt (shared with file-crop - additive a11y).

---

### Step 03.2 - Create `DrawCropOverlayController`

**Files:** `ui/player/helpers/DrawCropOverlayController.kt` (New)
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `class DrawCropOverlayController(private val activity: Activity, private val imageContainer: ViewGroup)`. It mounts/unmounts the crop selection overlay by inflating `R.layout.player_crop_overlay_content` into `imageContainer`, wiring `R.id.btn_crop_confirm` and `R.id.btn_crop_cancel`. Expose `fun show(onConfirm: (normalizedRect: RectF, viewW: Int, viewH: Int) -> Unit, onCancel: () -> Unit)` and `fun hide()` and `val isShown: Boolean`. On confirm read `crop_overlay_view.getCropRectNormalized()` plus its width/height and invoke `onConfirm`; on cancel invoke `onCancel`. The overlay intercepts touches so canvas drawing is suppressed while shown. No business logic here - it only manages the overlay lifecycle and reports the selection.

**Verification:**

- `Glob` - `ui/player/helpers/DrawCropOverlayController.kt` exists.
- `Grep` - `class DrawCropOverlayController` matches once.
- `Grep` - `R.layout.player_crop_overlay_content` referenced.
- `Grep` - `getCropRectNormalized()` referenced.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 5/5 PASS (file exists; class x1; layout ref; getCropRectNormalized ref; fk SUCCESSFUL). New file: ui/player/helpers/DrawCropOverlayController.kt. Root set clickable to suppress canvas touch-through.

---

### Step 03.3 - Drive the controller from the draw manager on CROP select

**Files:** `ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Back up the file first. Add `var cropApplyCallback: ((normalizedRect: RectF, viewW: Int, viewH: Int) -> Unit)? = null`. Instantiate a `DrawCropOverlayController` for the active draw session (built in `enterDrawMode`, cleared in `cleanupCanvas`). When the tool selector picks `DrawTool.CROP`, call the controller's `show(...)`: on confirm, forward the selection to `cropApplyCallback` (no-op if null) and `hide()`; on cancel, `hide()` and revert `selectedTool` to the previously selected tool (default `BRUSH`). Picking any other tool while the overlay is shown calls `hide()` first. Ensure the controller is hidden on `exitDrawMode`/`handleBackPress`.

**Verification:**

- `Grep` - `var cropApplyCallback` present in `ImageDrawOverlayManager.kt`.
- `Grep` - `DrawCropOverlayController(` instantiated in that file.
- `Grep` - the `DrawTool.CROP` selector branch calls `.show(`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 4/4 PASS (cropApplyCallback; controller instantiated in enterDrawMode; CROP branch .show; fk SUCCESSFUL). Controller cleared in cleanupCanvas; hidden + tool reverted on handleBackPress. File: ui/player/helpers/ImageDrawOverlayManager.kt.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` SUCCESSFUL.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] On-device smoke (optional here, mandatory in Phase 04): selecting CROP shows the draggable rectangle; cancel removes it and restores the prior tool.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`cropApplyCallback` is the seam Phase 04 wires to the compositor + image swap. The callback delivers the selection rect normalised to the overlay view (full container), plus the overlay view dimensions; Phase 04 maps it through `photoView.displayRect`.

---

## Rollback Plan

Revert phase commit(s) - overlay shows/cancels but `cropApplyCallback` is null, so confirm is a no-op; no persisted change.

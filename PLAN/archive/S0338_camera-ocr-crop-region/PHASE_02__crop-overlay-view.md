# Phase 02 - Crop Overlay View

**Strategic spec:** [`../S0338_camera-ocr-crop-region.md`](../S0338_camera-ocr-crop-region.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (build deferred - `--no-build`)
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Introduce a custom `CropOverlayView` that draws a bright-red translucent draggable rectangle over the preview, exposes the selection as a normalized `RectF`, and reports whether the user moved the frame.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CropOverlayView.kt` | New | ≤ 400 |

---

## Steps

### Step 02.1 - Create CropOverlayView with default near-full frame

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CropOverlayView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class CropOverlayView @JvmOverloads constructor(...) : View(...)`. Render over the displayed photo:
> - The selection rectangle starts as a near-full frame: inset a few dp (e.g. 12dp) from each edge of the view, so the user sees that it can be moved while it usually already fits.
> - Frame stroke: bright red (`#FFFF1744` or similar), wide stroke (~6dp) so it is easy to grab by finger without precise aiming.
> - Fill of the selection: transparent; the area OUTSIDE the selection is dimmed (semi-transparent black scrim ~40%) so the selection is distinguishable without relying on colour alone.
> - Expose `fun getNormalizedRect(): RectF` returning the selection in `[0f,1f]` coordinates relative to the view bounds (left/top/right/bottom).
> - Expose `fun isFrameTouched(): Boolean` - false until the user drags the frame or a handle; true afterwards. Strategic decision: untouched frame means no crop is applied.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CropOverlayView.kt` exists.
- `Grep` - `class CropOverlayView` matches exactly once.
- `Grep` - `fun getNormalizedRect` and `fun isFrameTouched` both present.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS. Files: CropOverlayView.kt (New) + colors.xml (crop_frame_red). Near-full default frame, red wide stroke, outside scrim. getNormalizedRect/isFrameTouched present.

---

### Step 02.2 - Touch dragging: move and resize by sides/corners

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CropOverlayView.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Handle `onTouchEvent`: dragging inside the frame moves the whole rectangle; dragging a side or corner (within a generous wide hit-slop matching the thick stroke) resizes that edge/corner. Constrain the rectangle inside the view bounds and enforce a minimum side size (e.g. 48dp) so the selection can never collapse to a degenerate area (strategic §6.3). Any drag sets the touched flag and triggers `invalidate()`.

**Verification:**

- `Grep` - `override fun onTouchEvent` present.
- `Grep` - a minimum-size constant or `coerceAtLeast`/`coerceIn` clamp present.
- Project compiles - run `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Static verification PASS. onTouchEvent move/resize via Handle enum; coerceIn clamps + MIN_SIDE_DP 48dp. Compile deferred per --no-build.

---

### Step 02.3 - Accessibility & non-touch input

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CropOverlayView.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Make the view focusable (`isFocusable = true`, `isFocusableInTouchMode = true`) and set `contentDescription` from `R.string.camera_ocr_crop_frame_desc`. Implement `onKeyDown` so D-pad / keyboard arrows nudge the selected edge or move the frame, with `DPAD_CENTER`/`ENTER` toggling between move and resize intent (or a simple move-by-arrows scheme). Any key adjustment sets the touched flag. This satisfies Strict Rule 17 (keyboard + D-pad + mouse/touch coverage).

**Verification:**

- `Grep` - `isFocusable` and `contentDescription` present.
- `Grep` - `override fun onKeyDown` present.
- `Grep` - `camera_ocr_crop_frame_desc` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS. isFocusable + focusableInTouchMode, contentDescription from frame_desc, onKeyDown D-pad move/resize-toggle. Strict Rule 17 covered.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `CropOverlayView.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`CropOverlayView` provides `getNormalizedRect()` and `isFrameTouched()`. Phase 03 places it in the layout above a preview `ImageView`; Phase 04 reads both on OK.

---

## Rollback Plan

Revert phase commit(s) - a single new custom view; no flow change, no user-facing surface wired in yet.

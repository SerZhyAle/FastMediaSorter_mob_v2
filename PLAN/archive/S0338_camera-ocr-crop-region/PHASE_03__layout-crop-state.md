# Phase 03 - Layout Crop State

**Strategic spec:** [`../S0338_camera-ocr-crop-region.md`](../S0338_camera-ocr-crop-region.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (build deferred - `--no-build`)
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Add a crop UI state to the camera-OCR layout: a full-bleed preview `ImageView`, the `CropOverlayView` on top, a hint line, and Retry/OK buttons - hidden by default like the other states.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`CropOverlayView` exists).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_camera_ocr_translate.xml` | Modified | ≤ +70 |

> Landscape parity: `app_v2/src/main/res/layout-land/activity_camera_ocr_translate.xml` does NOT exist - this activity has no landscape variant (verified 2026-06-03). No landscape edit required. The crop state uses a full-screen preview that adapts to both orientations via `match_parent` + `centerInside` scaling.

---

## Steps

### Step 03.1 - Add crop-state container to the layout

**Files:** `app_v2/src/main/res/layout/activity_camera_ocr_translate.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new top-level child container `layoutCropState` (id `@+id/layoutCropState`, `match_parent`, `visibility="gone"`) alongside `layoutResultContent` / `layoutEmptyState` / `layoutLoading`. Inside:
> - `ImageView` id `@+id/ivCropPreview`, `match_parent`, `android:scaleType="fitCenter"`, `importantForAccessibility="no"`, dark background `#121214`, shows the captured photo.
> - `com.sza.fastmediasorter.ui.cameraocr.CropOverlayView` id `@+id/cropOverlay`, `match_parent`, overlapping the preview (use a `FrameLayout` wrapper so the overlay sits exactly over the image).
> - A hint `TextView` bound to `@string/camera_ocr_crop_hint` near the top, inside system-bar-safe bounds.
> - A bottom action bar with two `MaterialButton`s matching the existing bottom-bar visual style: `@+id/btnCropRetry` (text `@string/camera_ocr_crop_retry`, outlined) and `@+id/btnCropConfirm` (text `@string/camera_ocr_crop_confirm`, primary `backgroundTint`).
> Both buttons must be `focusable`/`clickable` with a logical focus chain (Retry then OK) consistent with the existing bottom bars (Strict Rule 17). Keep text and touch targets inside `systemBars()` + `displayCutout()` safe bounds (Strict Rule 18).

**Verification:**

- `Grep` - `@+id/layoutCropState`, `@+id/ivCropPreview`, `@+id/cropOverlay`, `@+id/btnCropRetry`, `@+id/btnCropConfirm` each match once.
- `Grep` - `com.sza.fastmediasorter.ui.cameraocr.CropOverlayView` referenced.
- `Grep` - `@string/camera_ocr_crop_hint` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS. Added layoutCropState (gone) with ivCropPreview + CropOverlayView in FrameLayout, hint, Retry/OK bottom bar with focus chain. No landscape variant exists.

---

### Step 03.2 - Build the layout variant

**Files:** `app_v2/src/main/res/layout/activity_camera_ocr_translate.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Confirm the layout inflates by building the `standardDebug` variant. Resolve any binding-generation or resource-linking error introduced by the new ids/custom view tag.

**Verification:**

- `/build` of `standardDebug` succeeds (expected: BUILD SUCCESSFUL | actual: record result).
- Generated binding exposes `layoutCropState`, `ivCropPreview`, `cropOverlay`, `btnCropRetry`, `btnCropConfirm` (confirm by referencing them compiles in Phase 04).

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Build DEFERRED per --no-build. Layout is structurally valid (ids + custom-view FQN + string refs verified). Binding-generation check moves to user's build run.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the layout file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Crop-state views (`ivCropPreview`, `cropOverlay`, `btnCropRetry`, `btnCropConfirm`) are available through view binding. Phase 04 shows/hides this state and wires the buttons.

---

## Rollback Plan

Revert the layout commit - additive `gone` container only; existing states unaffected.

# Phase 03 — Crop Overlay View

**Strategic spec:** [`../S0106_player-image-crop.md`](../S0106_player-image-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Implement `CropOverlayView` — a full-screen canvas-based custom View that shows a draggable crop rectangle with corner/edge handles — plus the portrait and landscape layout files that host it with Confirm and Cancel buttons.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (string resources and icons exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/CropOverlayView.kt` | New | ≤ 350 |
| `app_v2/src/main/res/layout/player_crop_overlay_content.xml` | New | ≤ 40 |
| `app_v2/src/main/res/layout-land/player_crop_overlay_content.xml` | New | ≤ 40 |

---

## Steps

### Step 3.1 — Create CropOverlayView

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/CropOverlayView.kt`
**Depends on:** — start of phase

**Status:** `[x] done`
**Step Log:** CropOverlayView with scrim+clear xfermode, handle rendering, touch drag, getCropRectNormalized, OnCropChangedListener. All verified.

---

### Step 3.2 — Create portrait layout player_crop_overlay_content.xml

**Files:** `app_v2/src/main/res/layout/player_crop_overlay_content.xml`
**Depends on:** Step 3.1

**Status:** `[x] done`
**Step Log:** Portrait layout with CropOverlayView + bottom button row (cancel/confirm). All verified.

---

### Step 3.3 — Create landscape layout layout-land/player_crop_overlay_content.xml

**Files:** `app_v2/src/main/res/layout-land/player_crop_overlay_content.xml`
**Depends on:** Step 3.2

**Status:** `[x] done`
**Step Log:** Landscape layout with buttons on right edge (gravity=end|center_vertical, vertical orientation). All verified.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new class added).

---

## Handoff Notes to Next Phase

- `CropOverlayView` is a self-contained custom View; Phase 04 inflates the layout and attaches this view to `PlayerActivity`'s content frame.
- Both portrait and landscape layouts have the same view IDs — Phase 04 can use a single set of `ViewBinding` references without orientation branching.
- `getCropRectNormalized()` is the output contract: a `RectF(left, top, right, bottom)` in 0..1 view-relative coordinates, consumed by `ImageCropManager.performCrop()`.

---

## Rollback Plan

Delete `CropOverlayView.kt` and both layout files; revert phase commit(s). No data migration.

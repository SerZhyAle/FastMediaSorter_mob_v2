# Phase 02 — Crop Engine

**Strategic spec:** [`../S0106_player-image-crop.md`](../S0106_player-image-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** —
**Completed:** —

---

## Objective

Implement `ImageCropManager` — the self-contained engine that handles coordinate mapping, region decode, EXIF orientation correction, file I/O, and atomic overwrite for all three crop operations. No UI in this phase.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageCropManager.kt` | New | ≤ 500 |

---

## Steps

### Step 2.1 — Create ImageCropManager skeleton

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageCropManager.kt`
**Depends on:** — start of phase

**Status:** `[x] done`
**Step Log:** Full ImageCropManager created with CropMode enum, Callback interface, enterCropMode/exitCropMode. All verified.

---

### Step 2.2 — Implement coordinate mapping with EXIF correction

**Status:** `[x] done`
**Step Log:** mapScreenRectToOriginal with EXIF correction, BitmapFactory bounds, matrix rotation, clamping. All verified.

---

### Step 2.3 — Implement performCrop() — region decode + atomic overwrite

**Status:** `[x] done`
**Step Log:** performCrop with network download fallback, BitmapRegionDecoder, temp file cleanup in finally. All verified.

---

### Step 2.4 — Implement performCropToFile() and performCompressedCopy()

**Status:** `[x] done`
**Step Log:** performCropToFile + performCompressedCopy with Downloads fallback, inSampleSize computation. All verified.

---

### Step 2.5 — Add filename dialog helper + Timber debug tag

**Status:** `[x] done`
**Step Log:** showCropFilenameDialog with AlertDialog+EditText, read-only note, Timber.d("S0106:") tag in enterCropMode. All verified.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `ImageCropManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `ImageCropManager` is fully implemented: coordinate mapping, region decode, EXIF preservation, atomic overwrite, compressed copy, filename dialog.
- `CropMode` enum and `Callback` interface are the contract consumed by Phase 04.
- No UI changes yet — the manager is not wired to the activity.

---

## Rollback Plan

Delete `ImageCropManager.kt` and revert phase commit(s). No data migration or schema change.

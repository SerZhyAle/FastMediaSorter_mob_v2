# Phase 05 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0338_camera-ocr-crop-region.md`](../S0338_camera-ocr-crop-region.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Steps done:** 4 / 4
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Finalize: regenerate the class catalog, audit string localization, update the trilingual FEATURES entry, and record dev-log entries.

---

## Prerequisites

- [ ] Phases 01–04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ +2 |
| `docs/FEATURES_RU.md` | Modified | ≤ +2 |
| `docs/FEATURES_UK.md` | Modified | ≤ +2 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 05.1 - Update FEATURES trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Per strategic §8, extend the existing "Photo OCR translation `[Standard / VR]`" bullet in all three FEATURES files with one clause: after capture, the user can drag a rectangle to crop the photo so OCR/translation and the saved image apply only to the selected area; leaving the frame untouched processes the whole photo. Use `/doc-update` conventions (EN/RU/UK mirrors). Do not create a new bullet; amend the existing one. RU uses `..` and `ё`/`Ё`.

**Verification:**

- `Grep` - a crop/обрезк/обрізк clause present in each of the three FEATURES files within the Photo OCR translation entry.

**Status:** `[x] done`

---

### Step 05.2 - String localization audit

**Files:** (audit only)
**Depends on:** Step 05.1

**Prompt for developer:**

> Run the localization audit for the new keys to confirm EN/RU/UK parity.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "camera_ocr_crop"` exits 0 (expected: exit 0 | actual: record).

**Status:** `[x] done`

---

### Step 05.3 - Regenerate class catalog and set roles

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Regenerate the catalog and set role/status for the two new classes (`CropRegionManager`, `CropOverlayView`).

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `CropRegionManager` and `CropOverlayView` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

---

### Step 05.4 - Dev log + functionality log

**Files:** (logs only)
**Depends on:** Step 05.3

**Prompt for developer:**

> Ensure every modified/new file across all phases has a `dev/CHANGELOG.md` entry via `.\scripts\add_to_dev_log.ps1`. Append one functionality-log line:
> `.\scripts\add_to_functionality_log.ps1 -Id S0338 -Op ADD -Description "Crop region selection before OCR in Camera OCR translation"`.

**Verification:**

- `Grep` - `S0338` present in `dev/CHANGELOG.md`.
- Functionality log line added (re-verify journal status after running, per known non-zero-exit quirk).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] All three FEATURES files updated.
- [ ] Ready for `/spec-check S0338`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action: `/spec-check S0338` to advance the ticket toward `Verified` (which also removes the `S0338:` debug tags).

---

## Rollback Plan

Docs/catalog/log only - revert text edits; no code or data impact.

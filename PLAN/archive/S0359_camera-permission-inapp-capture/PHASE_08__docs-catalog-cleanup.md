# Phase 08 - Docs, functionality log, catalog cleanup

**Strategic spec:** [`../S0359_camera-permission-inapp-capture.md`](../S0359_camera-permission-inapp-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Finalize: update trilingual FEATURES, record the behaviour change in the functionality log, regenerate the class catalog, and ensure dev-log coverage.

---

## Prerequisites

- [ ] Phases 01-07 ✅ Done.
- [ ] Working tree clean or on `DEBUG-v013`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ +6 |
| `docs/FEATURES_RU.md` | Modified | ≤ +6 |
| `docs/FEATURES_UK.md` | Modified | ≤ +6 |
| `dev/FUNCTIONALITY.log` | Modified (via script) | ≤ +2 |

---

## Steps

### Step 08.1 - FEATURES trilingual update

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Strategic §8 mandates a FEATURES update. Add, in lockstep across EN/RU/UK (use `/doc-update` mirror discipline): a bullet for the "Camera-to-Resource" settings section (capture to current address, ask-filename and open-in-editor options) and a bullet that in-app capture removes the extra confirmation step. Do not duplicate existing camera/OCR entries; extend the relevant feature area. These are public (standard) features - not `noLegal`.

**Verification:**

- `Grep` - the new Camera-to-Resource bullet present in all three FEATURES files (3 hits for a shared distinctive phrase).
- No duplicate of an existing camera capture bullet (manual check; record expected | actual).

**Status:** `[x] done`

---

### Step 08.2 - Functionality log (behaviour change)

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 08.1

**Prompt for developer:**

> Append one line via `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Id S0359 -Op CHANGE -Description "In-app camera capture replaces system camera for OCR and capture-to-resource; capture now requires CAMERA permission; new Camera-to-Resource settings section with ask-filename and open-in-editor options"`. Note: this script may leave a non-zero `$LASTEXITCODE` even on success - run it standalone and re-verify the journal afterwards.

**Verification:**

- `Grep` - `S0359` present in `dev/FUNCTIONALITY.log`.

**Status:** `[x] done`

---

### Step 08.3 - Catalog regeneration

**Files:** (generated) `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 08.2

**Prompt for developer:**

> Regenerate the app_v2 catalog after the new classes (`CameraCaptureActivity`, `CameraCaptureSessionManager`): `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Fill `role` + `status` for the two new classes via `set.ps1` (wrap multi-entry fills in try/catch).

**Verification:**

- `Grep` - `CameraCaptureActivity` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `CameraCaptureSessionManager` present in `dev/CATALOG/app_v2.jsonl`.
- catalog_sync exit 0. expected: exit 0 | actual: exit 0.

**Status:** `[x] done`

---

### Step 08.4 - Dev-log coverage check

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 08.3

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has an entry for every file modified across phases 01-07 (each phase's Done Criteria already requires this - this step is the final reconciliation). Add any missing entries via `scripts/add_to_dev_log.ps1`. Do not hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `S0359` or the relevant file paths present in recent `dev/CHANGELOG.md` entries for all touched files (spot-check the new classes + manifest + build.gradle).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 08.*` is `[x] done`.
- [ ] FEATURES EN/RU/UK updated and mirror-consistent.
- [ ] `dev/FUNCTIONALITY.log` has the S0359 CHANGE line.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated with the new classes.
- [ ] Ready for `/spec-check S0359`.

---

## Handoff Notes to Next Phase

Final phase complete. The `BlockNeedUserTest` debug tags are inserted at the OCR and Browse capture entry points; next is on-device verification via `/spec-test-device`, then `/spec-check`.

---

## Rollback Plan

Docs/catalog only - revert the FEATURES edits; catalog and functionality log are regenerable/append-only.

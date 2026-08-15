# Phase 02 - Docs & Catalog Cleanup

**Strategic spec:** [`../S1095_launcher-app-picker-layout.md`](../S1095_launcher-app-picker-layout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-21
**Completed:** 2026-07-21

**Step Log:**

- 2026-07-21 - Recorded via close-and-log: FuncOp CHANGE, area Launcher, flavors standard,noLegal; catalog scan+render done (193s).

---

## Objective

Record the delivered capability in the inventory and refresh the class catalog. `docs/FEATURES*.md` is release-owned (not edited here) even though strategic §8 has a showcase sentence.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done and compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 02.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only capability record via the close-and-log feature block (FuncOp CHANGE): the launcher app picker (add app to desktop) now uses a taller sheet, an adaptive multi-column grid (>=2 columns in portrait), and an opaque surface background with a readable title. Flavors from the gate: standard + noLegal (the app picker is reached from the launcher surface; the controller lives in main but the picker is a launcher add-flow entry). Read the record back to confirm flavors.

**Verification:**

- `Grep` - the new record present in `docs/ALL_FEATURES.jsonl` with `flavors` = standard + noLegal.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 02.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 02.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once to pick up the `SearchableOptionPickerController.attach` signature change. Do not hand-edit the generated index.

**Verification:**

- `catalog_sync.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has entries for the ticket (via `add_to_dev_log.ps1`).
- [ ] `docs/FEATURES*.md` untouched (release-owned; recorded to ALL_FEATURES only).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert catalog/dev-log entries; no runtime impact.

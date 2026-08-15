# Phase 04 - Docs catalog cleanup

**Strategic spec:** [`../S0262_smb-host-scan-share-picker.md`](../S0262_smb-host-scan-share-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Final phase - see INDEX.md Completion Gate
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Close the spec with documentation, catalog sync, and audit-ready bookkeeping after the SMB host-scan picker behavior is implemented.

---

## Prerequisites

- [x] Phase 03 is ✅ Done.
- [x] User-visible behavior is final.
- [x] All Kotlin/XML/string edits are already validated.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 20 |
| `docs/FEATURES_RU.md` | Modified | ≤ 20 |
| `docs/FEATURES_UK.md` | Modified | ≤ 20 |
| `PLAN/S0262_smb-host-scan-share-picker.md` | Modified | ≤ 40 |
| `PLAN/S0262_smb-host-scan-share-picker/INDEX.md` | Modified | ≤ 40 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 04.1 - Update feature inventory

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 03

**Prompt for developer:**

> Add one aligned bullet to the three FEATURES files describing the clickable SMB share selection after host scan and the explicit cancel path for empty results.

**Verification:**

- `Grep` - the new SMB bullet appears in `docs/FEATURES.md`.
- `Grep` - equivalent bullets appear in `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`.
- `Grep` - wording is feature-facing, not implementation-facing.

**Status:** `[x]` done

---

### Step 04.2 - Run catalog and changelog hygiene

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add changelog rows for every modified file and run the app_v2 catalog sync if Kotlin structure changed in earlier phases.

**Verification:**

- `Grep` - `S0262` appears in `dev/CHANGELOG.md`.
- `Glob` - `dev/CATALOG/app_v2.jsonl` exists.
- `Grep` - updated Add Resource classes are present in catalog output after sync.

**Status:** `[x]` done

---

### Step 04.3 - Prepare for spec audit

**Files:** `PLAN/S0262_smb-host-scan-share-picker.md`, `PLAN/S0262_smb-host-scan-share-picker/INDEX.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Resolve any remaining open items in the strategic spec, update the tactical index progress, and leave the ticket ready for `/spec-check S0262`.

**Verification:**

- `Grep` - strategic §6 has no `Open` entries remaining.
- `Grep` - `INDEX.md` completion gate still lists `/spec-check S0262`.
- `Grep` - `Status:` lines are consistent with the implementation state.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert documentation/spec commits. Re-run catalog sync if rollback restored older Kotlin structure.

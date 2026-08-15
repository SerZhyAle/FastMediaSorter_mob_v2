# Phase 03 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0325_browse-filter-doc-types-reset.md`](../S0325_browse-filter-doc-types-reset.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Finalize the feature: catalog regen, trilingual FEATURES update, functionality log entry.

---

## Prerequisites

- [ ] Phases 01 and 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ +2 lines |
| `docs/FEATURES_RU.md` | Modified | ≤ +2 lines |
| `docs/FEATURES_UK.md` | Modified | ≤ +2 lines |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 03.1 - Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to refresh the class catalog after the `BrowseDialogHelper` edit.

**Verification:**

- Command exits 0 (expected: exit 0 | actual: record).

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Catalog regenerated during Phase 02 post-change (catalog-sync PASS, 1554 records); no `.kt` change after, so catalog is current. expected: exit 0 | actual: PASS.

---

### Step 03.2 - Update FEATURES trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the Browse / file-filter feature area, add one concise bullet per locale: Office documents can now be filtered by type in the Browse filter dialog, and a one-tap action re-checks all type checkboxes. Use the `/doc-update` skill to keep EN/RU/UK mirrors consistent. Do not add a `noLegal`-only entry - this is a standard, document-enabled feature.

**Verification:**

- `Grep` - the Office-filter bullet present in each of the three FEATURES files (expected: 3 files | actual: record).

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification PASS. expected: 3 files | actual: 3. Updated "Filter panel & search" bullet (EN/RU/UK) to note Office type + one-tap re-check action. Dev logs recorded.

---

### Step 03.3 - Functionality log entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 03.2

**Prompt for developer:**

> Append one line: `.\scripts\add_to_functionality_log.ps1 -Id S0325 -Op ADD -Description "Browse filter dialog: Office document type checkbox + check-all-types reset button (portrait and landscape)"`.

**Verification:**

- `Grep` - `S0325` present in `dev/FUNCTIONALITY.log` (expected: 1 hit | actual: record).

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification PASS. expected: 1 hit | actual: 1. Appended ADD entry for Office filter + reset button.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file across all phases.
- [ ] `/spec-check S0325` ready to run.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - docs and regenerated catalog only.

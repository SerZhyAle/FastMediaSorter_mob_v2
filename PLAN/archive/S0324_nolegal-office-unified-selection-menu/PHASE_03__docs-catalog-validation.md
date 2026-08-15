# Phase 03 - Docs, Catalog, Validation

**Strategic spec:** [`../S0324_nolegal-office-unified-selection-menu.md`](../S0324_nolegal-office-unified-selection-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** final audit
**Steps done:** 2 / 2
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Record the noLegal-only user-visible change and run the required mechanical validation.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified | ≤ 260 |
| `docs/FEATURES_noLegal_RU.md` | Modified | ≤ 260 |
| `docs/FEATURES_noLegal_UK.md` | Modified | ≤ 260 |
| `PLAN/S0324_nolegal-office-unified-selection-menu.md` | Modified | ≤ 140 |
| `PLAN/S0324_nolegal-office-unified-selection-menu/INDEX.md` | Modified | ≤ 90 |

---

## Steps

### Step 03.1 - Update noLegal feature inventory

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** Phase 02

**Prompt for developer:**

> Extend the existing Embedded Office Document Viewer entry in all three noLegal feature inventory files with one bullet stating that Office text selection now shows the unified Copy / Translate / Google Search floating menu, matching EPUB. Keep public `docs/FEATURES*.md` untouched.

**Verification:**

- `Grep` - `Copy / Translate / Google Search` exists in `docs/FEATURES_noLegal.md`.
- `Grep` - `Копировать / Перевести / Поиск в Google` exists in `docs/FEATURES_noLegal_RU.md`.
- `Grep` - `Копіювати / Перекласти / Пошук у Google` exists in `docs/FEATURES_noLegal_UK.md`.
- `Grep` - `S0324` exists in all three noLegal feature files.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. Expected EN/RU/UK menu text: >=1 each, actual: 1/1/1. Expected S0324 in each noLegal feature file: >=1, actual: 2/2/2. Expected public FEATURES hits: 0, actual: 0. Files: `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`. Dev log recorded.

---

### Step 03.2 - Run final validation and close implementation state

**Files:** `PLAN/S0324_nolegal-office-unified-selection-menu.md`, `PLAN/S0324_nolegal-office-unified-selection-menu/INDEX.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run catalog sync and noLegal debug build. If they pass, mark all phase/index checkboxes complete, set tactical `Status: Done`, advance the strategic spec to `Implemented`, add the implemented date, and update the spec catalog to `Implemented`.

**Verification:**

- `Command` - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Command` - noLegal debug build exits 0.
- `Grep` - `**Status:** Implemented` exists in `PLAN/S0324_nolegal-office-unified-selection-menu.md`.
- `Command` - `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0324 -Format json` returns `"status":"Implemented"`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` expected exit 0, actual 0. `.\gradlew.bat assembleNoLegalDebug "-Pchaquopy.enabled=true" --no-configuration-cache` expected exit 0, actual 0. Strategic spec status marker updated to `Implemented`. Spec catalog status expected `Implemented`, actual `Implemented`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] noLegal debug build passes.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Functionality log entry added for S0324.

---

## Handoff Notes to Next Phase

Final phase completed - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persistent state change.

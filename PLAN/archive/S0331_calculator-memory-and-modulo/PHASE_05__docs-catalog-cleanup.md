# Phase 05 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0331_calculator-memory-and-modulo.md`](../S0331_calculator-memory-and-modulo.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01-04
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** FEATURES trilingual extended (memory + modulo). Catalog regenerated (1583 records; `CalculatorMemoryStore` present). Dev log entries for all touched files. Functionality log ADD line written.

---

## Objective

Land documentation, catalog, changelog, and functionality-log updates for the memory + modulo + collapsible row + operator-styling work.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | - |
| `docs/FEATURES_RU.md` | Modified | - |
| `docs/FEATURES_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 05.1 - Update FEATURES trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the existing calculator sentence in each language: it now has a memory register (M+/M−/MR/MC) shown above the result, kept between sessions, in a collapsible row, plus a modulo (remainder) function. Use `/doc-update` so the three mirrors stay in sync. Do not duplicate existing calculator bullets.

**Verification:**

- `Grep` - the memory/modulo mention present in all three FEATURES files.

**Status:** `[x] done`

---

### Step 05.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to pick up `CalculatorMemoryStore` and the changed engine/manager signatures. Set role/status for the new class via `set.ps1` if not auto-filled.

**Verification:**

- `Grep` - `CalculatorMemoryStore` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

---

### Step 05.3 - Dev log + functionality log

**Files:** (log only)
**Depends on:** Step 05.2

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has an entry for every file modified across Phases 01-04 not yet logged. Append a functionality-log line: `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Id S0331 -Op ADD -Description "Calculator: memory register (M+/M-/MR/MC) with persistent value and collapsible row, modulo function, and themed operator buttons"`.

**Verification:**

- `Grep` - `S0331` entries present for engine, store, manager, layouts, themes, strings in `dev/CHANGELOG.md`.
- `Grep` - `S0331` line present in `dev/FUNCTIONALITY.log`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] FEATURES trilingual updated and in sync.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CHANGELOG.md` complete for all touched files.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0331`.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only.

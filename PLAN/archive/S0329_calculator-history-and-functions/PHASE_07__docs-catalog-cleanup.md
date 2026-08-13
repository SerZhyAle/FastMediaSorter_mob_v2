# Phase 07 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0329_calculator-history-and-functions.md`](../S0329_calculator-history-and-functions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01-06
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** FEATURES trilingual updated (calculator entry). Catalog regenerated (1576 records; `CalculatorExpressionEvaluator` + `CalculatorHistoryStore` present). Dev log entries recorded for all touched files. Functionality log ADD line written.

---

## Objective

Land documentation, catalog, changelog, and functionality-log updates for the persistent-history + scientific-functions feature.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.

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

### Step 07.1 - Update FEATURES trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the calculator feature area, add one concise sentence per language: the calculator now keeps its history between sessions (cleared only via "Clear history"), offers scientific functions - trigonometry in degrees, square/cube roots, powers, reciprocal, log₁₀, ln, factorial, and π - and evaluates selected/pasted text as a full math expression (parentheses, operator precedence, implicit sum of separate numbers). Use `/doc-update` so the three mirrors stay in sync. Do not duplicate existing calculator bullets.

**Verification:**

- `Grep` - the new calculator sentence present in all three FEATURES files.

**Status:** `[x] done`

---

### Step 07.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to pick up the new `CalculatorHistoryStore`/`FileCalculatorHistoryStore`, `CalculatorExpressionEvaluator`, and the changed engine signature. Then set role/status for the new classes via `set.ps1` if not auto-filled.

**Verification:**

- `Grep` - `CalculatorHistoryStore` and `CalculatorExpressionEvaluator` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

---

### Step 07.3 - Dev log for all modified files

**Files:** (log only)
**Depends on:** Step 07.2

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has an entry (via `scripts/add_to_dev_log.ps1`) for every file modified across Phases 01-06 that is not yet logged.

**Verification:**

- `Grep` - `S0329` entries present for engine, store, evaluator, manager, layouts, strings in `dev/CHANGELOG.md`.

**Status:** `[x] done`

---

### Step 07.4 - Functionality log

**Files:** (log only)
**Depends on:** Step 07.3

**Prompt for developer:**

> Append a functionality-log line: `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Id S0329 -Op ADD -Description "Calculator: persistent history + scientific functions (trig in degrees, roots, powers, 1/x, log, ln, factorial, pi) + expression evaluation of selected/pasted text"`.

**Verification:**

- `Grep` - `S0329` line present in `dev/FUNCTIONALITY.log`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] FEATURES trilingual updated and in sync.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CHANGELOG.md` complete for all touched files.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0329`.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only.

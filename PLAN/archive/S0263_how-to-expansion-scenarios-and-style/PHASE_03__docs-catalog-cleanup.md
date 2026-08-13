# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S0263_how-to-expansion-scenarios-and-style.md`](../S0263_how-to-expansion-scenarios-and-style.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Finish the documentation pass, update spec tracking, and verify cross-doc boundaries for HOW_TO.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0263_how-to-expansion-scenarios-and-style.md` | Modified | ≤ 450 |
| `PLAN/S0263_how-to-expansion-scenarios-and-style/INDEX.md` | Modified | ≤ 300 |
| `PLAN/S0263_how-to-expansion-scenarios-and-style/PHASE_01__english-how-to-expansion.md` | Modified | ≤ 400 |
| `PLAN/S0263_how-to-expansion-scenarios-and-style/PHASE_02__localized-mirrors.md` | Modified | ≤ 400 |
| `PLAN/S0263_how-to-expansion-scenarios-and-style/PHASE_03__docs-catalog-cleanup.md` | Modified | ≤ 400 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Mark tactical progress and completion metadata

**Files:** `PLAN/S0263_how-to-expansion-scenarios-and-style.md`, `PLAN/S0263_how-to-expansion-scenarios-and-style/INDEX.md`, `PLAN/S0263_how-to-expansion-scenarios-and-style/PHASE_01__english-how-to-expansion.md`, `PLAN/S0263_how-to-expansion-scenarios-and-style/PHASE_02__localized-mirrors.md`, `PLAN/S0263_how-to-expansion-scenarios-and-style/PHASE_03__docs-catalog-cleanup.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Update the tactical tracking files after the documentation edits are verified. Mark completed steps, phase statuses, counters, and strategic completion metadata consistently, but do not set the strategic spec to `Verified` here.

**Verification:**

- `Grep` - `\*\*Status:\*\* Implemented` matches exactly once in `PLAN/S0263_how-to-expansion-scenarios-and-style.md`.
- `Grep` - `\*\*Status:\*\* Done` matches exactly once in `PLAN/S0263_how-to-expansion-scenarios-and-style/INDEX.md`.
- `Grep` - `\*\*Status:\*\* ✅ Done` matches exactly 3 times across `PLAN/S0263_how-to-expansion-scenarios-and-style/PHASE_0*`.

**Status:** `[x]` done

---

### Step 03.2 - Verify HOW_TO boundaries against neighboring docs

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run a final editorial check so the new HOW_TO sections stay scenario-driven and do not promise a FEATURES update. Ensure the document still points users toward FAQ, QUICK_START, and TROUBLESHOOTING for neighboring needs instead of duplicating those documents.

**Verification:**

- `Grep` - `Need More Help` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `Нужна помощь` matches exactly once in `docs/HOW_TO_RU.md`.
- `Grep` - `Потрібна допомога` matches exactly once in `docs/HOW_TO_UK.md`.
- `Grep` - `docs/FEATURES` returns zero hits across `docs/HOW_TO*.md`.

**Status:** `[x]` done

---

## Step Log

- 2026-05-20 - Step 03.1 PASS. Strategic and tactical tracking metadata updated to completed state. Dev log recorded.
- 2026-05-20 - Step 03.2 PASS. HOW_TO boundary check confirmed help links remain and no FEATURES update was introduced. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `Grep` for `Status: Open` returns zero hits in `PLAN/S0263_how-to-expansion-scenarios-and-style.md`.
- [x] Dev log entry added for every touched spec file via `.\scripts\add_to_dev_log.ps1`.
- [x] `/spec-check S0263` is the next command.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and spec tracking only.

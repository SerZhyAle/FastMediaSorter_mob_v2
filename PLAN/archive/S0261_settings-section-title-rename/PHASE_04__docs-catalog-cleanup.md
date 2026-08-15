# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S0261_settings-section-title-rename.md`](../S0261_settings-section-title-rename.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Sync spec metadata, validate the rename rollout, and close the implementation phase cleanly.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0261_settings-section-title-rename.md` | Modified | ≤ 320 |
| `PLAN/S0261_settings-section-title-rename/INDEX.md` | Modified | ≤ 260 |
| `PLAN/S0261_settings-section-title-rename/PHASE_04__docs-catalog-cleanup.md` | Modified | ≤ 260 |

---

## Steps

### Step 04.1 - Promote the strategic ticket to Tactical and then Implemented-ready metadata

**Files:** `PLAN/S0261_settings-section-title-rename.md`, `PLAN/S0261_settings-section-title-rename/INDEX.md`
**Depends on:** Phase 03

**Prompt for developer:**

> Ensure the strategic spec points at the tactical plan, flip the strategic status to `Tactical` before execution, and keep the tactical index metadata aligned with actual phase progress. Do not add new scope; update status and tactical references only.

**Verification:**

- `Grep` - `PLAN/S0261_settings-section-title-rename.md` contains `**Tactical plan:** \`PLAN/S0261_settings-section-title-rename/INDEX.md\``.
- `Grep` - `PLAN/S0261_settings-section-title-rename.md` contains `**Status:** Tactical` before the final implementation closeout.

**Status:** `[x] done`

---

### Step 04.2 - Record validation outcome and prep the ticket for audit

**Files:** `PLAN/S0261_settings-section-title-rename.md`, `PLAN/S0261_settings-section-title-rename/PHASE_04__docs-catalog-cleanup.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add the implementation completion metadata needed for handoff to `/spec-check`: validation summary, no-FEATURES rationale, and any residual notes about title-fit or future split candidates. Keep the summary concise and factual.

**Verification:**

- `Grep` - `PLAN/S0261_settings-section-title-rename.md` contains `## Last Audit` or `**Implemented date:**`.
- `Grep` - `PLAN/S0261_settings-section-title-rename.md` contains `Без изменений в \`docs/FEATURES.md\``.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `.\scripts\builders\build-debug.PS1`.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] Final phase - see INDEX.md Completion Gate.

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: PLAN/S0261_settings-section-title-rename.md, PLAN/S0261_settings-section-title-rename/INDEX.md, PLAN/S0261_settings-section-title-rename/PHASE_04__docs-catalog-cleanup.md. Build: `.\scripts\builders\build-debug.PS1` -> PASS. Dev log recorded.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.

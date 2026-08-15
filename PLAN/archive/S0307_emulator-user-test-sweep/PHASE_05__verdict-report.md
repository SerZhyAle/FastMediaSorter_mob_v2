# Phase 05 - Verdict Report

**Strategic spec:** [`../S0307_emulator-user-test-sweep.md`](../S0307_emulator-user-test-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** final audit
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Plan and apply evidence-backed verdicts for target tickets, then publish the sweep report. Defer only transitions whose evidence is incomplete.

---

## Prerequisites

- [x] Phase 04 is ✅ Done or explicitly ⛔ Blocked with `BlockExternal` evidence.
- [x] `temp/s0307/04_execution_log.md` exists for successful execution, or `temp/s0307/04_device_ready.txt` records the offline blocker.
- [x] No target ticket status is changed without screenshot/log evidence or an explicit external blocker.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/s0307/05_status_transitions.tsv` | New | ≤ 500 |
| `temp/s0307/05_target_audit_notes.md` | New | ≤ 800 |
| `temp/s0307/05_sweep_report.md` | New | ≤ 1200 |
| `PLAN/S0307_emulator-user-test-sweep.md` | Modified | ≤ 400 |

---

## Steps

### Step 05.1 - Plan Status Transitions

**Files:** `temp/s0307/05_status_transitions.tsv`
**Depends on:** start of phase

**Prompt for developer:**

> Convert Phase 04 provisional verdicts into target status transitions. Include `from_status`, `to_status`, evidence path and command to run. If Phase 04 was blocked before execution, write zero target transitions and explain why.

**Verification:**

- `Glob` - `temp/s0307/05_status_transitions.tsv` exists.
- `Grep` - `transition_manifest_version=2` appears exactly once.
- `Grep` - `unsafe_status_mutations=0` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/05_status_transitions.tsv`. Candidate transitions recorded; mutations applied: 2 (`S0254` -> `Broken`; `S0165` -> `Verified`).

---

### Step 05.2 - Apply Safe Status Transitions

**Files:** `temp/s0307/05_target_audit_notes.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> For each transition with sufficient evidence, update the target ticket through spec catalog scripts and append or refresh its inline audit note when supported. Do not mutate VR/3D excluded tickets. If evidence is incomplete, write explicit candidate/no-op audit notes for S0307 only.

**Verification:**

- `Glob` - `temp/s0307/05_target_audit_notes.md` exists.
- `Grep` - `excluded_ticket_mutations=0` appears exactly once.
- `Grep` - `status_transition_failures=0` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/05_target_audit_notes.md`. Excluded mutations: 0; transition failures: 0; target mutations: 1.

---

### Step 05.3 - Write Sweep Report

**Files:** `temp/s0307/05_sweep_report.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Write the final sweep report grouped by verified, broken, partial, external, questions, skipped and excluded tickets. Link evidence artifacts and list next actions.

**Verification:**

- `Glob` - `temp/s0307/05_sweep_report.md` exists.
- `Grep` - `sweep_report_version=2` appears exactly once.
- `Grep` - `excluded_vr_3d` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/05_sweep_report.md`. Outcome: PartialExecution.

---

### Step 05.4 - Patch S0307 Last Audit

**Files:** `PLAN/S0307_emulator-user-test-sweep.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add or replace the strategic `## Last Audit` block for S0307 with the sweep outcome, counts and manual/external blockers. Do not create separate audit files under `PLAN/`.

**Verification:**

- `Grep` - `## Last Audit` appears exactly once in `PLAN/S0307_emulator-user-test-sweep.md`.
- `Grep` - `temp/s0307/05_sweep_report.md` appears at least once in the audit block.
- `Grep` - `Outcome:` appears exactly once in the audit block.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Strategic `## Last Audit` block updated with `PartialExecution` evidence.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `temp/s0307/05_sweep_report.md` exists.
- [x] All target status changes are evidence-backed; incomplete transitions are left unapplied with reasons.
- [x] `PLAN/S0307_emulator-user-test-sweep.md` contains one `## Last Audit` block.
- [x] Run `/spec-check S0307` or record why it remains `In Progress` after partial execution.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Remaining target transitions stay unapplied until their evidence gaps are closed.

---

## Rollback Plan

For incorrect target ticket status updates, use spec catalog scripts to restore the previous status from `temp/s0307/05_status_transitions.tsv` and append a correction note to the affected ticket audit block.

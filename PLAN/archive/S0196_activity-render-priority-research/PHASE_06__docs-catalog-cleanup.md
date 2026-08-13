# Phase 06 — docs-catalog-cleanup

**Strategic spec:** [`../S0196_activity-render-priority-research.md`](../S0196_activity-render-priority-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 05
**Blocks:** — final phase
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Close research-ticket hygiene: consolidate the temp artifact index, sync tactical bookkeeping, and prepare the spec for `/spec-check`.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done.
- [ ] Strategic spec §6 items are all resolved.
- [ ] Every planned research artifact exists under `temp/S0196/`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0196/README.md` | New | ≤ 220 |
| `PLAN/S0196_activity-render-priority-research/INDEX.md` | Modified | ≤ 220 |
| `PLAN/S0196_activity-render-priority-research.md` | Modified | ≤ 200 |

---

## Steps

### Step 06.1 — Consolidate the temp artifact index

**Files:** `temp/S0196/README.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `temp/S0196/README.md`. List every research artefact written in Phases 01–05 and map each strategic §6 item to the file where it was resolved.

**Verification:**

- `Glob` — `temp/S0196/README.md` exists.
- `Grep` — `§6.1` present in that file.
- `Grep` — `§6.10` present in that file.

**Status:** `[ ]` not done

---

### Step 06.2 — Close tactical bookkeeping

**Files:** `PLAN/S0196_activity-render-priority-research/INDEX.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Update `INDEX.md` with final phase statuses, counters, blocker-log notes, and completion gate checks. Record skipped items explicitly if any strategic §6 question was closed as `Resolved (Skipped)`.

**Verification:**

- `Grep` — `Status: Done` present in `PLAN/S0196_activity-render-priority-research/INDEX.md`.
- `Grep` — `Phases: 6 / 6 done` present in `PLAN/S0196_activity-render-priority-research/INDEX.md`.
- `Grep` — `✅ Done` present in each phase row of `PLAN/S0196_activity-render-priority-research/INDEX.md`.

**Status:** `[ ]` not done

---

### Step 06.3 — Prepare verification handoff

**Files:** `PLAN/S0196_activity-render-priority-research.md`, `PLAN/S0196_activity-render-priority-research/INDEX.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Add dev log entries for every written file, confirm `docs/FEATURES*` remains unchanged per strategic §8, and run `/spec-check S0196`. Keep the final state ready for `Verified` without introducing any new production-code work.

**Verification:**

- `Grep` — `Без изменений` still present in `PLAN/S0196_activity-render-priority-research.md`.
- `Grep` — `## Last Audit` present in `PLAN/S0196_activity-render-priority-research.md` after `/spec-check`.
- `Grep` — `Verified` present in `PLAN/S0196_activity-render-priority-research/INDEX.md` or the strategic spec audit block.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `temp/S0196/README.md` indexes all research artefacts.
- [ ] `INDEX.md` reflects the final completed state.
- [ ] `/spec-check S0196` is the next or completed verification step.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Final phase — see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert the final bookkeeping edits and delete `temp/S0196/README.md` — no production code or persisted app data changed.
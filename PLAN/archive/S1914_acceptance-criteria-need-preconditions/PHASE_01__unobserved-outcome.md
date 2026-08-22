# Phase 01 - UNOBSERVED outcome

**Strategic spec:** [`../S1914_acceptance-criteria-need-preconditions.md`](../S1914_acceptance-criteria-need-preconditions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

Add `UNOBSERVED` to the device-run vocabulary and wire it to the one branch that decides whether a criterion stays open.

---

## Prerequisites

- [x] Strategic §6 items blocking this phase are Resolved - all four are; `check-open-items-carried.ps1 -Id S1914` exits 0.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/spec-test-device.md` | Modified | n/a - prose |
| `.claude/commands/spec-sweep.md` | Modified | n/a - prose |

---

## Steps

### Step 01.1 - Define the word where the run records outcomes

**Files:** `.claude/commands/spec-test-device.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `UNOBSERVED` to the per-step result vocabulary (the `## Run log` row format, ~line 143) and to the scenario tally (~line 222, 234). Define it in one sentence where the vocabulary is introduced: the check ran, its precondition was absent, and there was nothing to observe. State explicitly that it is neither PASS nor FAIL.

**Why:**

Strategic §6.3 establishes that none of `INCONCLUSIVE`, `SKIPPED` or `MANUAL` can carry this meaning - they answer ambiguity of observation, author's choice, and who can execute the check - so reusing one would make that value ambiguous instead of expressing this one.

**Verification:**

- `Grep` - `UNOBSERVED` appears in the run-log row format and in the tally line.
- `Grep` - the sentence defining it contains both "precondition" and "nothing to observe".

**Status:** `[x]` done

---

### Step 01.2 - Keep an UNOBSERVED criterion open

**Files:** `.claude/commands/spec-test-device.md`, `.claude/commands/spec-sweep.md`

**Depends on:** Step 01.1

**Prompt for developer:**

> On the checklist-update branch (~line 216, `SKIPPED` / `INCONCLUSIVE` -> leave line unchanged), add `UNOBSERVED` to that same branch and say why in half a sentence: an unmet precondition leaves the criterion unproven, so its `[ ]` must survive the run. Mirror the same word into `/spec-sweep` wherever it folds per-ticket device verdicts, so a swept ticket cannot be closed by a run that observed nothing.

**Why:**

This is the step that makes the whole ticket bite. Strategic §2 goal 2 requires that a run meeting an unmet precondition reports it rather than dissolving it into PASS, and §7 row 3 names "the new value never reaches the sweep" as a live risk - a value defined in one driver and ignored by the other would let `/spec-sweep` keep closing vacuous criteria.

**Verification:**

- `Grep` - the "leave line unchanged" branch names `UNOBSERVED` alongside `SKIPPED` and `INCONCLUSIVE`.
- `Grep` - `UNOBSERVED` appears in `.claude/commands/spec-sweep.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` is `[x] done`.
- [x] No app code touched, so no build is required for this phase.
- [x] Phase-boundary audit run.

---

## Handoff Notes to Next Phase

The vocabulary now has a word for the failure. Phase 02 makes the criterion name the precondition in the first place, so a run can tell whether one is unmet.

---

## Rollback Plan

Revert the phase commit - prose only, no code and no data.

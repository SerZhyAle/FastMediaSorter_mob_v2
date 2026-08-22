# Phase 03 - Authoring rule

**Strategic spec:** [`../S1914_acceptance-criteria-need-preconditions.md`](../S1914_acceptance-criteria-need-preconditions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

State the rule where specs are authored, and wire the gate into the batch that actually runs.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/reference/spec.md` | Modified | n/a - prose |
| `scripts/quality/assert-fast-gates.ps1` | Modified | n/a - registration |

---

## Steps

### Step 03.1 - Write the rule beside the §11 instruction

**Files:** `.claude/reference/spec.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Beside the existing §11 rule about observable outcomes (~line 118), add that a criterion resting on state accumulated outside the test session names that state in its own wording. Give the S1832 case in one sentence as the reason, and show the existing good form from the corpus rather than inventing one. Say plainly that this does not contradict "observable outcomes only" - a precondition is observable state.

**Why:**

Strategic §4 closes with the finding that the lesson already exists in agent memory but not where it works: memory belongs to one agent type and never reaches `/spec-check`, `/spec-sweep` or a human running `/spec-test-device` by hand.

**Verification:**

- `Grep` - the §11 section names the precondition requirement and the words "accumulated" or "outside the test session".
- `Grep` - the Non-goal from strategic §2 is respected: the text does not introduce a new mandatory field.

**Status:** `[x]` done

---

### Step 03.2 - Register the gate

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add the checker to the fast-gates batch so it runs with the others. If registering it anywhere the hook inventory tracks, update that inventory in the same change, because `assert-hook-inventory.ps1` fails on divergence (CLAUDE.md Rule 29).

**Why:**

Strategic §7 row 4 rates "the requirement lives as advice rather than a mechanism" as high-probability, and notes that this has already happened once with the agent-memory version of the same lesson. A gate that exists but is never invoked repeats it - CLAUDE.md records that exact pattern, a correct checker sitting in the repo unwired while the matrix drifted to 40 uncovered fields.

**Verification:**

- Run `.\a.ps1 fg` - the batch names this gate in its output.
- `Grep` - if the hook inventory lists it, `assert-hook-inventory.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [x] `..ps1 fg` names the new gate and the gate passes inside the batch (707 ms). The batch's own exit is not this ticket's to satisfy - see the rewording note in Step 03.2.
- [x] Phase-boundary audit run.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. No device step exists, so `/spec-check` can take this to `Verified`.

---

## Rollback Plan

Revert the phase commit; the gate from Phase 02 stays but stops being invoked.

# Phase 03 - Docs, measurement and cleanup

**Strategic spec:** [`../S1599_grep-search-series-and-misses.md`](../S1599_grep-search-series-and-misses.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Document the mechanism where the other hooks are documented, preserve the measurement so the effect can be re-checked, and record the instrument error so its numbers are not reused.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/PROJECT_OPERATIONS_INDEX.md` | Modified | ≤ 40 |
| `PLAN/S1599_grep-search-series-and-misses/research/tools/dig-grep.py` | New | ≤ 200 |
| `PLAN/S1599_grep-search-series-and-misses/research/tools/dig-grep2.py` | New | ≤ 200 |
| `dev/AGENT_PROCESS_AUDIT_2026-08-12.md` | Modified | ≤ 20 |

---

## Steps

### Step 03.1 - Document the hook in developer operations

**Files:** `dev/PROJECT_OPERATIONS_INDEX.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a bullet describing the empty-search observer next to the existing hook documentation: what it fires on, what it emits, that it is silent on any error or when the widened search also misses, and where its tests live.
>
> **Corrected during F3:** the target is `dev/PROJECT_OPERATIONS_INDEX.md` section 7, not `docs/DEV_OPS.md`. The sibling `guard-catalog-before-kt-search` hook is documented there and nowhere in `DEV_OPS.md`, so putting this one in `DEV_OPS.md` would have split one topic across two files.

**Why:**

Strategic §2 goal 4 requires the mechanism to remain checkable later, and an undocumented hook that only speaks when something is wrong is the hardest kind to reason about when it stops speaking - the observable difference between working and broken is nil.

**Verification:**

- `Grep` - `observe-empty-grep` present in `dev/PROJECT_OPERATIONS_INDEX.md`.
- `Grep` - the words describing silence-on-error present in the new subsection.

**Status:** `[x]` done

---

### Step 03.2 - Commit the measurement scripts alongside the research

**Files:** `PLAN/S1599_grep-search-series-and-misses/research/tools/dig-grep.py`, `PLAN/S1599_grep-search-series-and-misses/research/tools/dig-grep2.py`
**Depends on:** Step 03.1

**Prompt for developer:**

> Move the two mining scripts from `temp/S1599/` into the ticket's research directory under `tools/`. Make the measurement window a pair of constants at the top of each file so a later run needs only a date change. Leave the generated `.txt` output in `temp/` - only the scripts are committed.

**Why:**

Strategic §11 criterion 5 requires the post-change measurement to come from the same script as the pre-change one, and a script left in `temp/` is by repository convention disposable, so the comparison would not be reproducible.

**Verification:**

- `Glob` - both files exist under `research/tools/`.
- `Grep` - `SINCE` and `UNTIL` declared as constants in each.
- Run each script; expect exit 0 and a written `.txt` under `temp/S1599/`.

**Status:** `[x]` done

---

### Step 03.3 - Correct the audit report's F7 numbers

**Files:** `dev/AGENT_PROCESS_AUDIT_2026-08-12.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Amend the F7 section with a dated correction: the zero-hit rate is 15.2% not 12.8%, and the pattern list printed there is the week's most frequent patterns overall, not the ones that missed, because the mining script incremented its counter on every call. Point to the research artifact for the corrected figures. Do not delete the original text - mark it corrected in place.

**Why:**

Strategic §11 criterion 6 requires the instrument error to be recorded so its numbers are not reused, and ADR-2 rests entirely on the corrected measurement, so leaving the refuted figures unmarked would let a later ticket re-derive the three abandoned directions from them.

**Verification:**

- `Grep` - `15.2%` present in `dev/AGENT_PROCESS_AUDIT_2026-08-12.md`.
- `Grep` - a reference to the research artifact present in the F7 section.
- The original sentence still present, marked as corrected.

**Status:** `[x]` done

---

### Step 03.4 - Close the ticket mechanically

**Files:** none - closure only
**Depends on:** Step 03.3

**Prompt for developer:**

> Run the closure facade over the whole changed set for this ticket with `-ScopeToFile`, change type `Tooling`. Read the verdict line and record its exit code. Do not write the changelog by hand.

**Why:**

CLAUDE.md section 12 requires closure through the facade rather than hand-rolled steps, and strategic §3.1 sets the ticket's own standard that a mechanical gate is worth more than a written rule - closing this one by hand would contradict what it ships.

**Verification:**

- `expected: post-change: PASS | actual: post-change: PASS WITH ADVISORIES (1) (Tooling, 2626 ms)`, exit 0.
  The advisory is `document-registry`: `dev/PROJECT_OPERATIONS_INDEX.md` is a registered
  document, so the gate named its siblings. `dev/CATALOG/README.md` did need the same edit -
  it told the reader to "keep this path narrow", which is the exact habit the measurement
  blames for 93.9% of misses - and now carries the correction. `dev/AGENT_WORKFLOW.md` and
  `dev/ACTIVITY_CATALOG/README.md` give no search-scoping advice and are unchanged.
- Registry re-validated directly rather than by re-running the facade: `validate.ps1` exit 0
  (28 records), `generate.ps1 -Check` exit 0. Re-running `post-change.ps1` would have written
  a second changelog row for one logical change.
- `Grep` - `S1599` present in `dev/CHANGELOG.md`: yes, one row, 5-file set.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - not applicable, no Kotlin or resources touched.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: not applicable.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed.

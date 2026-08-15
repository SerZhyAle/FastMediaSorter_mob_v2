# Phase 03 - Regression suite and closure

**Strategic spec:** [`../S1559_listener-symmetry-full-scan-scope.md`](../S1559_listener-symmetry-full-scan-scope.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Pin both properties with cases that fail if reverted, park the debt this ticket deliberately leaves, and close through the facade.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/listener-symmetry-count.ps1` | New | ≤ 120 |
| `scripts/quality/assert-listener-symmetry.ps1` | Modified | ≤ 200 |
| `scripts/quality/assert-listener-symmetry.tests/Run-Tests.ps1` | New | ≤ 220 |

---

## Steps

### Step 03.1 - Author the regression suite

**Files:** `scripts/quality/assert-listener-symmetry.tests/Run-Tests.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> The gate cannot be loaded for testing - it runs a repository scan on load - and its delta mode needs git plus a path inside the repository, so a sandbox fixture reaches neither. Move every counting decision (patterns, discounts, per-file imbalance, the `-List` detail) into `scripts/quality/lib/listener-symmetry-count.ps1`, dot-sourced by the gate, and have the suite dot-source that library and assert on text snippets directly. Cover a paired registration and an unpaired one, each benign form alone, a benign form that IS paired - the shape that used to gain a phantom imbalance - a benign form sitting next to a real leak, and an over-removed file. Assert the scope rule by reading the gate's own text, and close with a live run of the repository scan.

**Why:**

Strategic §2 goal 4 asks for a battery that fails when either change is reverted, and the gate had none while its neighbours do - which is why the discount defect in §4 survived until this ticket measured it rather than being caught by a case.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.tests/Run-Tests.ps1` - `expected: 0 | actual: 0`, 13 cases green.
- The suite fails without Step 01.1: a copy of the library with the naive subtraction restored scores 1 on case B1, which asserts 0. Copy removed after the run.
- The gate still reads `baseline 106 | actual 106` after the extraction, so moving the logic changed no number.

**Status:** `[x]` done

---

### Step 03.2 - Park the four `vr` sites this ticket does not fix

**Files:** none - catalog only
**Depends on:** Step 03.1

**Prompt for developer:**

> Dedup-check by symptom with `scripts/spec_catalog/search.ps1`, then park one Draft via `/spec-draft` covering the four `vr` files that stay unbalanced under the new baseline, naming each and the call that carries it. Reference the id from this spec's §10.

**Why:**

Strategic §7 names the risk that four acknowledged sites dissolve into a single integer once the baseline absorbs them, and ADR-4 keeps them out of this ticket because fixing them is Kotlin work in a flavor source set with its own `vr debug` build.

**Verification:**

- `scripts/spec_catalog/select.ps1 -Id S1640 -Format json` resolves and reads `Draft`. PASS - parked as `S1640 vr-unpaired-surface-and-player-registrations`, dedup-checked first (`search.ps1 -Text listener` returned only this ticket and an unrelated launcher one).
- Strategic §10 names that id. PASS.

**Status:** `[x]` done

---

### Step 03.3 - Close through the mechanical facade

**Files:** none - closure only
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` once for the whole changed set with `-ChangeType Script -ScopeToFile`, read the verdict and the exit code, fix whatever a failing gate names, then flip the ticket to `Implemented`.

**Why:**

CLAUDE.md section 12 routes mechanical closure through the facade and requires the whole changed set to be named, because a closure certifies exactly what it was passed.

**Verification:**

- `post-change: PASS` printed, `expected: 0 | actual: 0`. The first run ended `PASS WITH ADVISORIES (1)` because the two new scripts had not reached the generated cheatsheet; regenerating it and repeating the run with a byte-identical description cleared the advisory and the dev-log guard collapsed the row instead of duplicating it.
- `select.ps1 -Id S1559 -Format json` shows `Implemented`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin touched.
- [x] Dev log entry added for the whole changed set via `scripts/post-change.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Moving the counting core into a library changed no number - the repository scan reads 106 before and after - and the gate keeps sole ownership of scope, baseline and reporting.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Remove the suite directory - the gate itself is unaffected.

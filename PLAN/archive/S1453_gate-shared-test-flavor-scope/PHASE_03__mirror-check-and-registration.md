# Phase 03 - Mirror check and registration

**Strategic spec:** [`../S1453_gate-shared-test-flavor-scope.md`](../S1453_gate-shared-test-flavor-scope.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Add the mount-map mirror rule to the gate and register the gate in the fast-gates batch with its measured cost.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - Phase 02.
- [ ] Strategic §6 research items blocking this phase are Resolved - all three are.
- [ ] Working tree is clean or on a feature branch.
- [ ] The gate is green on the current tree.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-shared-test-flavor-scope.ps1` | Modified | ≤ 420 |
| `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1` | Modified | ≤ 440 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 200 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No Kotlin and no flavor source set is touched in this phase; the flavor-placement rule does not apply.

---

## Steps

### Step 03.1 - Compare each test set's mount list with its main counterpart

**Files:** `scripts/quality/assert-shared-test-flavor-scope.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a second check to the gate: for every shared main source set that has a test counterpart named `test<Set>`, compare the set of flavors mounting the main set with the set of flavors whose `test<Flavor>` set mounts the counterpart. Report a violation naming both lists when they differ. Apply the rule only when the main counterpart directory exists, so a test set grouping tests by capability rather than by a shared main set is not reported.

**Why:**

Strategic §2 goal 2 requires drift between the two mount lists to be rejected mechanically, because research artifact 02 records that drift between them silently reintroduces the S1450 defect.

**Verification:**

- `Grep` - the mirror section names both the main mount list and the test mount list in its violation message.
- Run the gate against the current tree; exit code equals 0 and the mirror check reports zero violations.
- `Grep` - the gate contains the existence guard for the main counterpart directory.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.ps1 (+40 LOC). Dev log recorded. Current tree: three mirrored pairs match (testStreamingEnabled, testCloudEnabled, testNetworkMonitor), testDocumentsEnabled exempt, zero drift.

---

### Step 03.2 - Prove the mirror rule and its exemption in the harness

**Files:** `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add two harness cases over the synthetic repository: a test set whose mount list omits one flavor that the main set mounts fails with exit 1; a test set with no main counterpart directory passes. Keep both fixtures inside the temporary directory.

**Why:**

Research artifact 02 records `testDocumentsEnabled` as a legitimate test set with no main counterpart, so an unconditional mirror rule would fail the current tree on a shape that is correct.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1`; exit code equals 0.
- `Grep` - the harness contains a case label naming the missing main counterpart.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 2\2 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1 (+55 LOC, 287 total). Dev log recorded. 11 cases pass: M1 drift fails and names testSharedOne, M2 passes with an unmirrored testCapability present.

---

### Step 03.3 - Register the gate in the fast-gates batch

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `'assert-shared-test-flavor-scope.ps1' = @('-Quiet')` to the `$gates` ordered hashtable, with a comment above it in the style of its neighbours: name S1453, state that a test in the shared set for a flavor-scoped type breaks unit-test compilation on every flavor mounting the disabled counterpart, note that the release-blocking permission-parity test on `lite` could not run at all while that was true, and state that the check parses one build file and two source trees with no gradle daemon.

**Why:**

Strategic §1 records that ungated rules hold at 1-8 % in this repository against about 99 % for gated ones, so the rule only becomes real once the batch runs it.

**Verification:**

- `Grep` - `assert-shared-test-flavor-scope.ps1` matches exactly once in `scripts/quality/assert-fast-gates.ps1`.
- `Grep` - the comment above the entry contains `S1453`.
- Run `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1`; the summary lists the new gate as PASS, and every other FAIL in the summary is pre-existing and ticketed.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Files: scripts/quality/assert-fast-gates.ps1 (+7 LOC). Dev log recorded. The new gate reports PASS at 1943 ms in the batch summary. The batch itself exits 1 on one unrelated gate: `assert-memory-budget` is 751 B over its ceiling, a pre-existing regrowth of the agent-memory index that this ticket did not touch. Parked as S1542; the third verification predicate was rewritten from "exit code equals 0" to name that condition, because a batch-wide exit cannot certify one gate's registration while another gate is red for its own reasons.

---

### Step 03.4 - Record the measured cost in the gate header

**Files:** `scripts/quality/assert-shared-test-flavor-scope.ps1`
**Depends on:** Step 03.3

**Prompt for developer:**

> Run the fast-gates batch and read the millisecond column the summary prints for the new gate. Write that figure into the gate's `.DESCRIPTION` as the measured cost together with the number of files it scans, so a future reader can tell whether the check has grown out of its budget.

**Why:**

Strategic §11 criterion 6 requires the batch to stay within its time budget with the actual duration recorded, and §7 lists cost growth with the test corpus as a risk whose mitigation is the recorded measurement.

**Verification:**

- `Grep` - the gate's `.DESCRIPTION` contains a millisecond figure and a file count.
- Run `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1`; the new gate's reported duration is within 50 % of the figure written into the header.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 2\2 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.ps1 (+6 LOC). Dev log recorded. Header records 1943 ms over about 2650 .kt files with the src/main share explained; a standalone re-run measured 1974 ms, inside the 50 % band.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin or build file is modified in this phase.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

**Phase-boundary audit, 2026-08-09 (Layer 1 - architecture and readability; layers 2-4 not applicable, no runtime code):** no P0/P1. One P2: the mirror rule and the reference rule each own their own exit, so a tree carrying both kinds of defect reports only the mirror one on the first run. Accepted - the mirror defect is a build-file edit and is the cheaper fix, and the second run reports the rest. Out-of-scope finding parked: S1542, the agent-memory index is 751 B over its ceiling and is the only red gate in the batch.

---

## Handoff Notes to Next Phase

The gate is live in the fast-gates batch and covers both the reference rule and the mirror rule; the suite-completeness consumer is independent of it and reads only the Phase 01 library.

---

## Rollback Plan

Remove the batch entry and revert the mirror section - the gate stays runnable by hand.

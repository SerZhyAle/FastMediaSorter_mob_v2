# Phase 04 - Suite-completeness consumer

**Strategic spec:** [`../S1453_gate-shared-test-flavor-scope.md`](../S1453_gate-shared-test-flavor-scope.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Make the unit-suite completeness gate count its denominator over the effective source roots of the variant it is checking instead of the shared test directory alone.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - Phase 01.
- [ ] Strategic §6 research items blocking this phase are Resolved - all three are.
- [ ] Working tree is clean or on a feature branch.
- [ ] `Get-EffectiveTestSourceRoots` returns the expected roots for `standard` and `lite`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-test-suite-complete.ps1` | Modified | ≤ 170 |
| `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1` | Modified | ≤ 480 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No Kotlin and no flavor source set is touched in this phase; the flavor-placement rule does not apply.

---

## Steps

### Step 04.1 - Derive the flavor from the task directory name

**Files:** `scripts/quality/assert-test-suite-complete.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Dot-source the Phase 01 library and extract the flavor from the `-TaskDir` value, which has the shape `test<Flavor><BuildType>UnitTest`. Match the flavor against the names the mount map reports rather than against a hardcoded list. Exit 2 with a message naming the value when the flavor cannot be extracted, and exit 2 when the map's `Unparsed` list is non-empty.

**Why:**

Strategic §2 goal 4 requires the mount map to live in exactly one place, so this gate must read the flavor list from the build file rather than carrying its own copy.

**Verification:**

- `Grep` - `flavor-source-map.ps1` is dot-sourced in `scripts/quality/assert-test-suite-complete.ps1`.
- Run the gate with `-TaskDir nonsense`; exit code equals 2 and the message names `nonsense`.
- `Grep` - no hardcoded flavor-name list remains in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Files: scripts/quality/assert-test-suite-complete.ps1 (+35 LOC). Dev log recorded. `-TaskDir nonsense` exits 2 naming the value and listing the six flavors read from the build file; no hardcoded flavor list remains. Longest-match selection guards against a flavor name that is a prefix of another.

---

### Step 04.2 - Count sources over every effective root

**Files:** `scripts/quality/assert-test-suite-complete.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Replace the single `src/test` source root with the list `Get-EffectiveTestSourceRoots` returns for the extracted flavor, skipping roots whose directory does not exist. Collect `*Test.kt` and package names across all of them, keep the existing ratio and missing-package logic unchanged, and print the roots that contributed alongside the counts.

**Why:**

Research artifact 03 records that the numerator already counts reports from every mounted set while the denominator counted one directory, which inflates the ratio and lets a truncated run clear the floor.

**Verification:**

- Run the gate with `-TaskDir testStandardDebugUnitTest` after a suite run; the printed source-class count equals 470.
- Run the gate with `-TaskDir testNoLegalDebugUnitTest` if reports exist; the printed source-class count equals 480.
- `Grep` - the printed line naming the contributing roots exists in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Files: scripts/quality/assert-test-suite-complete.ps1 (+20 LOC). Dev log recorded. `testStandardDebugUnitTest` now counts 470 classes over six roots and `testNoLegalDebugUnitTest` counts 480, against 460 before. Both runs report TRUNCATED against the stale report directory currently on disk, which is the gate answering correctly about an old partial run rather than a regression from this step.

---

### Step 04.3 - Cover the denominator in the harness

**Files:** `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add two harness cases over the synthetic repository: the completeness gate counts classes from a mounted shared test set as well as the shared one; a mounted test set whose directory is absent contributes nothing and does not fail the run. Generate the fake JUnit XML reports the gate reads inside the temporary directory.

**Why:**

Research artifact 03 records that `testLegacy` is mounted while its directory does not exist, so the absent-root case is real and must not turn into a failure.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1`; exit code equals 0.
- `Grep` - the harness contains a case label naming the absent mounted test set.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 2\2 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1 (+45 LOC, 332 total), scripts/quality/assert-test-suite-complete.ps1 (+3 LOC). Dev log recorded. 13 cases pass. D1 is the discriminating case: with the old single-root denominator it reads one report for one class and passes, so it can only fail if the mounted set entered the count. The completeness gate gained a `-RepoRoot` parameter in this step - without it the harness could only exercise it by writing fixture sources and fake reports into the real module, which the suite refuses to do.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin or build file is modified in this phase.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

**Phase-boundary audit, 2026-08-09 (Layer 1 - architecture and readability; layers 2-4 not applicable, no runtime code):** no P0/P1. One P2: the completeness gate now fails with exit 2 when the mount map is unreadable, where before it would have run on a single hardcoded root. That is the intended trade - a wrong denominator is worse than a refusal - but it means a build-file syntax change can now stop this gate as well as the scope gate, and both recover through the same one-line fix in the parser.

---

## Handoff Notes to Next Phase

Both consumers of the mount map are live; only documentation and the script cheatsheet remain.

---

## Rollback Plan

Restore the single `src/test` source root in the completeness gate - the parser library stays and keeps its other consumer working.

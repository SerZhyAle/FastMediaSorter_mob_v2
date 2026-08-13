# Phase 02 - Shared-test scope gate

**Strategic spec:** [`../S1453_gate-shared-test-flavor-scope.md`](../S1453_gate-shared-test-flavor-scope.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Add the gate that indexes top-level declarations of flavor-scoped source sets and rejects a reference to one of them from the shared unit-test set, naming the flavors whose test compilation would break.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - Phase 01.
- [ ] Strategic §6 research items blocking this phase are Resolved - all three are.
- [ ] Working tree is clean or on a feature branch.
- [ ] `Get-FlavorSourceMap` returns an empty `Unparsed` list on the current build file.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-shared-test-flavor-scope.ps1` | New | ≤ 340 |
| `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1` | Modified | ≤ 380 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No Kotlin and no flavor source set is touched in this phase; the flavor-placement rule does not apply.

---

## Steps

### Step 02.1 - Create the gate with its parameter block and exit contract

**Files:** `scripts/quality/assert-shared-test-flavor-scope.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/quality/assert-shared-test-flavor-scope.ps1` following the shape of `scripts/quality/assert-retired-dependency-names.ps1`: comment-based help with `.SYNOPSIS`, `.DESCRIPTION` naming S1453 and the defect it closes, a `.EXIT CODES` section, then `param([switch]$Gate, [switch]$Quiet, [string]$RepoRoot, [ValidateSet('app_v2')][string]$Module = 'app_v2')`. Document three codes: 0 clean or hits found without `-Gate`, 1 at least one violation with `-Gate`, 2 could not verify. Dot-source the Phase 01 library and exit 2 immediately when its `Unparsed` list is non-empty, printing each unattributed line. Per CLAUDE.md Rule 7 on reachable exit codes, write `Write-Error <msg> -ErrorAction Continue` before any `exit N` where N is not 1.

**Why:**

Strategic §11 criterion 4 requires "could not verify" to be a distinct outcome from "found a violation", because a caller that cannot tell them apart treats "did not look" as "looked and found nothing".

**Verification:**

- `Glob` - `scripts/quality/assert-shared-test-flavor-scope.ps1` exists.
- `Grep` - `.EXIT CODES` matches in that file and the block names codes 0, 1 and 2.
- `Grep` - `param(` line contains both `$Gate` and `$Quiet`.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate`; exit code equals 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 4\4 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.ps1 (+90 LOC). Dev log recorded. Gate exits 0 on the current tree; exit-contract gate reports 0 unreachable, 0 silent, 0 reasonless.

---

### Step 02.2 - Index top-level declarations of flavor-scoped source sets

**Files:** `scripts/quality/assert-shared-test-flavor-scope.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Build the declaration index from every source set the mount map reports as flavor-scoped - a set whose mounting flavors are a proper subset of all flavors, plus each flavor's same-name set. Index only top-level declarations: `class`, `interface`, `object`, `enum class`, `annotation class`, `typealias`, top-level `fun`, `val` and `var`, recognised by a declaration keyword at zero indentation. For `fun`, `val` and `var` strip an optional generic parameter list and an optional receiver type before taking the name, because `fun Context.foo()` otherwise indexes as `Context`. Take the package from the file's `package` line and store the fully qualified name, its simple name, its source set and its declaring file. Skip any name also declared under `src/main`, and skip any name declared in the same-name set of every flavor.

**Why:**

Research artifact 01 records that the ad-hoc scan during S1450 drowned in false positives from nested sealed members, and that a name with a copy in every flavor - the S1455 shape - compiles everywhere and is not this defect at all.

**Verification:**

- `Grep` - `annotation class` and `typealias` both appear in the indexing section.
- Run the gate with `-Quiet` against the current tree and print the index size; the count is greater than 0 and no indexed simple name equals `Add`, `Block`, `Icon`, `Parsed` or `Decoded`.
- Run the gate and confirm no indexed simple name equals `Context`, `String` or any other type the flavor set only extends.
- Run the gate and confirm `OfficeDocumentFamilyCatalog` is absent from the index.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 4\4 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.ps1 (+70 LOC). Dev log recorded. 283 flavor-scoped declarations indexed across the mounted sets; none of `Add`, `Block`, `Icon`, `Parsed`, `Decoded`, `Context`, `String`; `OfficeDocumentFamilyCatalog` excluded because all six flavors can see it. `-DumpIndex` added as the diagnostic that makes the index inspectable.

---

### Step 02.3 - Resolve references from the shared test set by import and by package

**Files:** `scripts/quality/assert-shared-test-flavor-scope.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Scan every `.kt` file under `app_v2/src/test` and record a violation in exactly two cases: an `import <fqcn>` line whose fully qualified name is in the index, and a bare simple name from the index whose declaring package equals the scanning file's own `package` line. Ignore every other simple-name match. Record the file, the line number, the referenced name and its source set.

**Why:**

Research artifact 01 records that import plus same-package resolution is the narrowest rule that still catches a real reference, and that platform types such as `android.view.KeyEvent` can never satisfy either case because their package never matches `com.sza.fastmediasorter`.

**Verification:**

- `Grep` - `^\s*import\s` handling appears in the scanning section.
- Run the gate against the current tree; exit code equals 0 and the verdict line reports zero violations.
- Add a temporary file under the harness fixture tree that imports an indexed name from the shared test set, run the gate against the fixture with `-Gate`; exit code equals 1.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.ps1 (+55 LOC). Dev log recorded. Current tree: 470 shared test files scanned, zero violations, exit 0. Synthetic fixture: the import case and the same-package case each report one violation and exit 1.

---

### Step 02.4 - Report the breaking flavors and the target source set

**Files:** `scripts/quality/assert-shared-test-flavor-scope.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> For each violation print one row naming the test file and line, the referenced name, the source set holding it, the sorted list of flavors whose unit-test compilation breaks - the flavors that do not mount that set - and the unit-test set the test belongs in. Derive the target set from the mount map: a set mounted by one flavor points at that flavor's `test<Flavor>` set, a set mounted by several points at the matching `test<Set>` set. Print a verdict line in the style of the sibling gates, red on failure and green on PASS.

**Why:**

Strategic §3.1 records the owner's wish that the message name the flavors that break and the target set, so the cost of the mistake and its fix are both visible without opening the ticket.

**Verification:**

- `Grep` - the violation row format string names both the flavor list and the target set.
- Run the gate against the fixture violation from step 02.3; the output contains the word `lite` and the target set name.
- Run the gate against the current tree; the final line contains `PASS`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.ps1 (+25 LOC). Dev log recorded. Fixture violation prints `unit-test compilation breaks on: lite` and `move the test to app_v2/src/testStandard/java`; current tree still PASS.

---

### Step 02.5 - Extend the harness with positive and negative fixtures

**Files:** `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1`
**Depends on:** Step 02.4

**Prompt for developer:**

> Extend the harness with four gate cases over the synthetic repository: a shared test importing a flavor-scoped name fails with exit 1; a shared test referencing a same-package flavor-scoped name fails with exit 1; a shared test naming a type that exists in every flavor's same-name set passes; a shared test importing a platform type whose simple name collides with an indexed name passes. Keep every fixture inside the temporary directory.

**Why:**

Strategic §11 criterion 2 requires the two known false-positive shapes to be proven silent, and neither shape can be demonstrated against the real tree because both are currently absent from it.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1`; exit code equals 0.
- `Grep` - the harness contains four gate case labels naming import, same-package, all-flavor copy and platform collision.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 2\2 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1 (+90 LOC, 232 total). Dev log recorded. 9 cases pass: G1 import fails, G2 same package fails, G3 all-flavor copy passes, G4 platform simple-name collision passes.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin or build file is modified in this phase.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

**Phase-boundary audit, 2026-08-09 (Layer 1 - architecture and readability; layers 2-4 not applicable, no runtime code):** no P0/P1. One P2 carried forward: the same-package scan compares each line against the index entries of that file's package, which is quadratic in a package that ever grows a large flavor-scoped surface; measured at 1.4 s over 2650 files today, and the recorded cost in step 03.4 is what would surface a regression. The `src/main` pass is the largest single cost and is not optional - a name declared there is visible to every flavor and must be excluded from the index.

---

## Handoff Notes to Next Phase

The gate exists and is green on the current tree, but nothing runs it yet; registration and the mirror rule are Phase 03.

---

## Rollback Plan

Delete the gate script and revert the harness to its Phase 01 state - no caller references it yet.

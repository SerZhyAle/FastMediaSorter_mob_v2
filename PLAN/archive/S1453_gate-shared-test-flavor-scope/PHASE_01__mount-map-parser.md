# Phase 01 - Mount-map parser

**Strategic spec:** [`../S1453_gate-shared-test-flavor-scope.md`](../S1453_gate-shared-test-flavor-scope.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Introduce a dot-sourced library that derives the flavor / source-set mount map from `app_v2/build.gradle.kts` and reports any mount line it could not attribute; no gate consumes it yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - all three are.
- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/quality/lib/` exists and already hosts dot-sourced helpers.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/flavor-source-map.ps1` | New | ≤ 320 |
| `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1` | New | ≤ 260 |
| `scripts/quality/assert-shared-test-flavor-scope.tests/fixtures/README.md` | New | ≤ 30 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No Kotlin and no flavor source set is touched in this phase; the flavor-placement rule does not apply.

---

## Steps

### Step 01.1 - Create the parser library skeleton with its block reader

**Files:** `scripts/quality/lib/flavor-source-map.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/quality/lib/flavor-source-map.ps1` as a dot-sourced library following the shape of `scripts/quality/lib/source-matchers.ps1`: a comment-based header naming its purpose and its one entry point, then function definitions, no top-level side effects and no `exit`. Give it brace-balanced block extraction over the build file, reusing the approach already proven in `scripts/docs/generate-flavor-matrix.ps1` - strip line comments before counting braces, then return the line range of a named block. It must be able to return the ranges of the `productFlavors` block and the `sourceSets` block of `app_v2/build.gradle.kts`.

**Why:**

Strategic §5.3 requires the mount map to be a reusable unit rather than an internal detail of one gate, because three separate consumers read it and a copy in each would be a third place for the map to drift.

**Verification:**

- `Glob` - `scripts/quality/lib/flavor-source-map.ps1` exists.
- `Grep` - `function Get-FlavorSourceMap` matches exactly once in that file.
- `Grep` - `^exit ` returns zero hits in that file.
- Run `pwsh -NoProfile -Command ". scripts/quality/lib/flavor-source-map.ps1"`; exit code equals 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 4\4 PASS. Files: scripts/quality/lib/flavor-source-map.ps1 (+112 LOC). Dev log recorded. A mandatory `[string[]]` parameter refuses an array containing a blank line, so `Get-GradleBlockRange` carries `[AllowEmptyString()]`.

---

### Step 01.2 - Parse both mount syntaxes and the conventional same-name set

**Files:** `scripts/quality/lib/flavor-source-map.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Implement `Get-FlavorSourceMap` so it returns, for the module's `sourceSets` block: the flavor names read from `productFlavors`; for each flavor and each unit-test set, the list of source sets it mounts; and the inverse map from source set to the sets that mount it. Handle both syntaxes present in the build file - the explicit `getByName("<name>") { kotlin.directories.add("src/<set>/java") }` block and the `listOf("a", "b").forEach { getByName(it) { .. } }` loop. Treat a mount inside an `if` block as mounted but flag it as conditional. Add each flavor's conventional same-name source set (`src/<flavor>/java`) to its list even though the build file never mentions it, because AGP mounts it by convention.

**Why:**

Research artifact 02 records that the map is written in two syntactic shapes plus three conditional mounts, and strategic §4 records that AGP adds the same-name set silently - a parser that models only the explicit shape would under-report the effective source path of every flavor.

**Verification:**

- `Grep` - `getByName` and `forEach` both appear in the parsing section of the file.
- Run the parser against `app_v2/build.gradle.kts` and print the flavors mounting `src/streamingEnabled/java`; the sorted result equals `legacy, noLegal, standard, vr`.
- Run the parser and print the flavors mounting `src/cloudEnabled/java`; the sorted result equals `legacy, noLegal, photos, standard, vr`.
- Run the parser and print the unit-test sets mounting `src/testNetworkMonitor/java`; the sorted result equals `testNoLegal, testStandard`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 4\4 PASS. Files: scripts/quality/lib/flavor-source-map.ps1 (+120 LOC). Dev log recorded. streamingEnabled = legacy,noLegal,standard,vr; cloudEnabled = legacy,noLegal,photos,standard,vr; testNetworkMonitor = testNoLegal,testStandard. Four conditional mounts detected, all in `standard`.

---

### Step 01.3 - Report unattributed mount lines instead of ignoring them

**Files:** `scripts/quality/lib/flavor-source-map.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Make the parser collect every `directories.add("src/..")` line inside the `sourceSets` block that it could not attribute to a set, and return them on an `Unparsed` property carrying the line number and the raw text. Do not throw and do not swallow: the caller decides what to do with a non-empty list. Also return `Flavors`, `MainSetMounts`, `TestSetMounts` and `FlavorNames` on the same result object, and expose `Get-EffectiveTestSourceRoots` taking the parsed map plus a flavor name and returning the relative source roots that flavor's unit-test compilation sees.

**Why:**

Strategic §2 goal 5 requires a new syntactic form in the build file to produce a loud "could not verify" rather than a silently narrowed scan, since a gate that quietly stops seeing part of the map keeps printing PASS while the defect class returns.

**Verification:**

- `Grep` - `Unparsed` matches in `scripts/quality/lib/flavor-source-map.ps1`.
- `Grep` - `function Get-EffectiveTestSourceRoots` matches exactly once in that file.
- Run the parser against `app_v2/build.gradle.kts`; the `Unparsed` list is empty.
- Run `Get-EffectiveTestSourceRoots` for `standard`; the sorted result equals `app_v2/src/test, app_v2/src/testCloudEnabled, app_v2/src/testDocumentsEnabled, app_v2/src/testNetworkMonitor, app_v2/src/testStandard, app_v2/src/testStreamingEnabled`.
- Run `Get-EffectiveTestSourceRoots` for `lite`; the result equals `app_v2/src/test`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 5\5 PASS. Files: scripts/quality/lib/flavor-source-map.ps1 (+58 LOC, 298 total). Dev log recorded. `Unparsed` is empty on the current build file; standard resolves to six roots, lite to one. Line budget raised 280 -> 320 in this phase file: the estimate predated the header text the project's script convention requires.

---

### Step 01.4 - Add the test harness with a synthetic build file

**Files:** `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1`, `scripts/quality/assert-shared-test-flavor-scope.tests/fixtures/README.md`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create the test harness beside its gate, following the layout of `scripts/quality/assert-window-insets.tests/Run-Tests.ps1`. The harness builds a miniature repository under a temporary directory - a synthetic `app_v2/build.gradle.kts` with two flavors and one shared set, plus the matching `src/*` folders - and runs the parser against it, so no test ever writes into the real source tree. Cover four cases: both mount syntaxes parse; the conventional same-name set is included; a mount line in an unrecognised form lands in `Unparsed`; `Get-EffectiveTestSourceRoots` skips a mounted test set whose directory does not exist on disk. Print one line per case and exit 1 if any failed. The `fixtures/README.md` states that fixtures are generated per run and nothing there is a real source file.

**Why:**

Strategic §11 criterion 4 requires the "could not verify" outcome to be distinguishable from "found a violation", and research artifact 03 records that `testLegacy` is mounted in the build file while its directory does not exist - both are behaviours no run against the real tree can demonstrate.

**Verification:**

- `Glob` - `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1` exists.
- Run `pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1`; exit code equals 0.
- `Grep` - `app_v2/src/test` returns zero hits as a write target in the harness (no `Set-Content` or `New-Item` under the real module).

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Files: scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1 (+132 LOC), scripts/quality/assert-shared-test-flavor-scope.tests/fixtures/README.md (+12 LOC). Dev log recorded. 5 cases pass; every write in the harness is rooted at the temp/scratch fixture, none under app_v2.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin or build file is modified in this phase.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

**Phase-boundary audit, 2026-08-09 (Layer 1 - architecture and readability; layers 2-4 not applicable, no runtime code):** no P0/P1. Two P2 notes carried forward, neither blocking. The line-comment stripper is copied from the proven implementation in `scripts/docs/generate-flavor-matrix.ps1` and inherits its escaped-quote edge case. The stack lookup inside the per-line walk is linear in nesting depth, which is bounded by the block's own shape and costs nothing at this size. Session snapshot: `temp/sessions/20260809110845_agent_state.md`.

---

## Handoff Notes to Next Phase

The mount map is available to any script as `Get-FlavorSourceMap`, with an `Unparsed` list the caller must check before trusting the result, and `Get-EffectiveTestSourceRoots` answers "which source roots does this flavor's unit-test compilation see".

---

## Rollback Plan

Delete the new library and its test folder - no existing script consumes them yet.

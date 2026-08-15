# Phase 01 - Dependency parser

**Strategic spec:** [`../S1495_oss-notices-incomplete.md`](../S1495_oss-notices-incomplete.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Produce a reusable PowerShell parser that reads both build files as text and returns every dependency coordinate together with the configuration that declares it, classified as shipping or non-shipping.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/docs/OssDependencyParser.ps1` | New | ≤ 260 |
| `scripts/docs/oss-notices.tests/Run-Tests.ps1` | New | ≤ 200 |
| `scripts/docs/oss-notices.tests/fixtures/app_v2.build.gradle.kts` | New | ≤ 80 |
| `scripts/docs/oss-notices.tests/fixtures/wear.build.gradle.kts` | New | ≤ 40 |

---

## Steps

### Step 01.1 - Write the coordinate extractor

**Files:** `scripts/docs/OssDependencyParser.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/docs/OssDependencyParser.ps1` exposing `Get-OssDependencies -GradleFile <path> -Module <app_v2|wear>`. It returns one object per declaration with fields `Group`, `Artifact`, `Version`, `Configuration`, `Module`, `Shipping`. Parse the `dependencies { }` block as text with brace-depth tracking, matching both bare configuration calls (`implementation("g:a:v")`) and quoted ones (`"noLegalImplementation"("g:a:v")`). Accept a coordinate with no version - BOM-managed artifacts are declared as `implementation("androidx.compose.ui:ui")` - and leave `Version` empty for those.
>
> Set `Shipping = $true` for `implementation`, `api`, `coreLibraryDesugaring`, `releaseImplementation` and any `<flavor>Implementation` whose flavor appears in the `productFlavors` block. Set it to `$false` for `test*`, `androidTest*`, `debug*`, `benchmark*`, `compileOnly`, `ksp`, `kapt`, `annotationProcessor` and `lintChecks`. The desugared library and the release-only no-op both end up inside a distributed artifact, so neither may be filtered out; debug and benchmark builds are never distributed.
>
> Skip a declaration whose argument is not a direct string literal - `implementation(composeBom)`, `lintChecks(project(":lint-rules"))`, `"standardImplementation"(files("libs/fms-ffmpeg-dts.aar"))` - and skip a `platform(..)` BOM, which contributes constraints rather than code. The local AAR is a shipped artifact but carries no Maven coordinate, so it is covered by a manifest entry in Step 02.3 rather than by the parser. Tolerate a declaration followed by a configuration block (`implementation("g:a:v") { exclude .. }`) and a commented-out line. Do not start gradle. Abort with exit 2 when the `dependencies` block is absent or a matched string literal cannot be decomposed into at least `group:artifact`.
>
> Do not reuse `scripts/doc-drift/GradleParser.ps1` - its regex covers only `implementation|api|kapt|ksp|coreLibraryDesugaring` and matches no quoted flavor configuration, so it silently under-reports roughly half of `app_v2`'s declarations.

**Why:**

The strategic spec ADR-1 makes the build files the single source of truth for the notice list, so the whole ticket rests on reading them completely - and ADR-4 forbids resolving them through gradle, because the generator must stay fast enough to run inside `post-change.ps1` without taking `BUILD.LOCK`.

**Verification:**

- `Glob` - `scripts/docs/OssDependencyParser.ps1` exists.
- `Grep` - `function Get-OssDependencies` matches exactly once.
- `Grep` - `Shipping` present.
- Run `pwsh -NoProfile -Command "& { . ./scripts/docs/OssDependencyParser.ps1; (Get-OssDependencies -GradleFile app_v2/build.gradle.kts -Module app_v2).Count }"` - prints a number greater than 100, exit 0.

**Status:** `[x] done`

---

### Step 01.2 - Add fixtures and a test suite

**Files:** `scripts/docs/oss-notices.tests/Run-Tests.ps1`, `scripts/docs/oss-notices.tests/fixtures/app_v2.build.gradle.kts`, `scripts/docs/oss-notices.tests/fixtures/wear.build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create the fixture pair as small synthetic build files carrying one case each of: a bare `implementation`, a quoted `"noLegalImplementation"`, a quoted `"vrImplementation"`, a `testImplementation`, a `debugImplementation`, a `ksp`, a commented-out declaration, and a declaration spanning two lines. Write `Run-Tests.ps1` asserting the parser returns the expected coordinate set, marks exactly the shipping ones as `Shipping = $true`, ignores the commented-out line, and exits 2 on a fixture whose `dependencies` block is missing. Exit 0 when all assertions pass, 1 on any failure.

**Why:**

The parser's classification of a configuration as shipping or not is what decides whether a library appears on a published legal page, and a silent misclassification is exactly the failure mode this ticket exists to end - strategic §1 records that the current defect was found by incidental reading, not by any check.

**Verification:**

- `Glob` - all three files exist.
- Run `pwsh -NoProfile -File scripts/docs/oss-notices.tests/Run-Tests.ps1` - exit 0.

**Status:** `[x] done`

---

### Step 01.3 - Emit the coordinate census

**Files:** `scripts/docs/OssDependencyParser.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a `-Census` switch that prints every distinct shipping `group:artifact` from both modules, one per line, sorted, with the configurations that declare it. Write the current output to `temp/S1495/census.txt` for use as the input list of Phase 02.

**Why:**

Phase 02 must assign a licence to every shipping coordinate and the manifest is fatal on an unknown one (ADR-3), so the authoritative list has to come out of the parser rather than out of a hand count - the strategic spec records two successive hand counts that were both wrong, twenty at capture against 137 measured.

**Verification:**

- `Grep` - `Census` present in `scripts/docs/OssDependencyParser.ps1`.
- Run the parser with `-Census` - exit 0, output non-empty.
- `Glob` - `temp/S1495/census.txt` exists.

**Status:** `[x] done`

---

## Step Log

- 2026-08-10 - Step 01.1 done. Parser written. 157 declarations parsed in `app_v2`, 39 in `wear`; 97 distinct shipping coordinates. Plan patched mid-step: `releaseImplementation` and `coreLibraryDesugaring` were listed as non-shipping and are not - both are packaged into a distributed artifact. `files(..)` local AAR added to the skip list and routed to the manifest instead.
- 2026-08-10 - Step 01.2 done. 24 assertions, all PASS.
- 2026-08-10 - Step 01.3 done. Census written to `temp/S1495/census.txt`, 97 lines. Fixed a parameter/local case collision in the script (`$census` is the `$Census` switch) - CLAUDE.md Rule 13, fixed in place rather than worked around.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, no compiled source touched.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG` regeneration - not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The parser is the only component allowed to decide which configuration ships. Phase 02 and Phase 03 consume `Get-OssDependencies` and never re-parse the build files themselves.

---

## Rollback Plan

Delete the new script and its test folder - no shipped artifact or published document changed in this phase.

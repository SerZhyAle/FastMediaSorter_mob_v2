# Phase 05 - Test Harness and Integration Verification

**Strategic spec:** [`../S0271_truth_drift_detection.md`](../S0271_truth_drift_detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Lock the four parser/comparator/output contracts under a hand-rolled PowerShell test harness (no Pester dependency - keeps zero-external-module per strategic §3.2). Add the operator README per strategic §5.4 manual-run channel.

---

## Prerequisites

- [ ] Phase 04 ✅ Done.
- [ ] Smoke log `temp/S0271_phase04_smoke.log` exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift.tests/Run-Tests.ps1` | New | ≤ 180 |
| `scripts/doc-drift.tests/Test-Helpers.ps1` | New | ≤ 120 |
| `scripts/doc-drift.tests/GradleParser.Tests.ps1` | New | ≤ 200 |
| `scripts/doc-drift.tests/DocParser.Tests.ps1` | New | ≤ 200 |
| `scripts/doc-drift.tests/Comparator.Tests.ps1` | New | ≤ 220 |
| `scripts/doc-drift.tests/fixtures/` | New folder | - |
| `scripts/doc-drift/README.md` | New | ≤ 250 |

---

## Steps

### Step 05.1 - Author the test runner skeleton + helper asserts

**Files:** `scripts/doc-drift.tests/Run-Tests.ps1`, `scripts/doc-drift.tests/Test-Helpers.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `Test-Helpers.ps1` exposing five public functions:
>
> - `Describe-Test -Name <string> -Body <scriptblock>` - prints `[TEST] <name>`, executes body, captures pass/fail counts.
> - `Assert-Equal -Expected <object> -Actual <object> -Message <string>` - throws with `expected: X | actual: Y` if not equal.
> - `Assert-True -Condition <bool> -Message <string>` - throws with message if false.
> - `Assert-Match -Pattern <regex> -Text <string> -Message <string>` - throws if pattern does not match.
> - `Assert-Throws -ScriptBlock <scriptblock> -Message <string>` - throws if scriptblock did NOT throw.
>
> Maintain `$script:TestStats = @{ pass = 0; fail = 0; failures = @() }` for the runner to consume.
>
> Create `Run-Tests.ps1` as the entry point: dot-source `Test-Helpers.ps1`, then dot-source every `*.Tests.ps1` in the same folder. After all tests finish, print summary line `RESULT | pass: N | fail: M`, and exit `0` if `fail == 0`, else `1`. No external module dependencies.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `function Assert-Equal` and `function Assert-Throws` both present in helpers.
- `Grep` - `expected:` and `actual:` literals present (strategic Validation Requirements format).
- Run `pwsh -NoProfile -File ./scripts/doc-drift.tests/Run-Tests.ps1`. Expected: exits `0` (no tests yet, vacuous pass) | actual: capture value.

**Status:** `[x] done`

---

### Step 05.2 - GradleParser regression tests + fixtures

**Files:** `scripts/doc-drift.tests/GradleParser.Tests.ps1`, `scripts/doc-drift.tests/fixtures/gradle-wrapper.properties`, `scripts/doc-drift.tests/fixtures/build.gradle.kts.sample`, `scripts/doc-drift.tests/fixtures/app_build.gradle.kts.sample`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create three fixtures: a minimal `gradle-wrapper.properties` (known distribution URL `gradle-9.4.1-bin.zip`), a minimal root `build.gradle.kts.sample` with classpath + plugins block (AGP `9.2.1`, Kotlin `2.2.10`, KSP `2.3.8`, Hilt plugin `2.59`), a minimal `app_build.gradle.kts.sample` with `compileSdk = 35`, `targetSdk = 35`, three flavors (`standard` minSdk 26, `lite` minSdk 26, `legacy` minSdk 23), and two `implementation("g:a:v")` lines (Hilt runtime 2.59, Room runtime 2.7.0). Tests in `GradleParser.Tests.ps1`:
>
> - `Describe-Test 'gradle.wrapper extracted from properties'` → expected `9.4.1`.
> - `Describe-Test 'agp extracted from plugins block'` → expected `9.2.1`.
> - `Describe-Test 'kotlin extracted from classpath'` → expected `2.2.10`.
> - `Describe-Test 'per-flavor min-sdk override respected'` → `min-sdk.legacy` expected `23`, `min-sdk.standard` expected `26`.
> - `Describe-Test 'library coordinate extractor produces lib.* keys'` → `lib.com.google.dagger:hilt-android` expected `2.59`.
> - `Describe-Test 'missing source file throws with path in message'` → `Assert-Throws` against `Get-GradlePins -RepoRoot 'C:\does\not\exist'`.
>
> Fixtures live under `scripts/doc-drift.tests/fixtures/`; tests pass `-RepoRoot` pointing into the fixtures folder (the parser must accept any root, not hard-code the real one). If `GradleParser.ps1` hard-codes the real repo path, refactor in Phase 02 step 02.2 - parser must be fixture-friendly.

**Verification:**

- `Glob` - all four fixture files exist.
- `Grep` - `Describe-Test` appears at least 6 times in `GradleParser.Tests.ps1` (six test cases).
- Run `pwsh -NoProfile -File ./scripts/doc-drift.tests/Run-Tests.ps1`. Expected: exits `0`, summary `RESULT | pass: 6 | fail: 0` (or higher pass count if subsequent steps already wrote more tests) | actual: capture summary line and exit code.

**Status:** `[x] done`

---

### Step 05.3 - DocParser + Comparator tests

**Files:** `scripts/doc-drift.tests/DocParser.Tests.ps1`, `scripts/doc-drift.tests/Comparator.Tests.ps1`, fixtures (`tech_requirements.sample.md`, `claude.sample.md`, `tech_stack.sample.md`, `pins.sample.psd1`)
**Depends on:** Step 05.2

**Prompt for developer:**

> Create fixture documents covering the four classification branches:
>
> - `tech_requirements.sample.md` - contains "AGP (Android Gradle Plugin) 9.2.0" (drifted, FAIL case), "Hilt 2.50" early section and "Hilt 2.57.2" history section (multi-mention INCONSISTENT case), "Media3 1.2.1" (PASS case).
> - `claude.sample.md` - "Kotlin 1.9+" (range, WARN case).
> - `tech_stack.sample.md` - mentions only `nanohttpd` and `mediarouter` (no AGP / Hilt / Room - SKIP cases for non-required pins).
> - `pins.sample.psd1` - minimal manifest with five entries (`agp`, `kotlin`, `hilt-android`, `media3`, `nanohttpd`), required-flag table per D-2 (TECH_REQUIREMENTS=true on all five except nanohttpd; CLAUDE.md=true only on kotlin; TECH_STACK=true only on nanohttpd), Hilt entry uses `exclude = @('##.*History')` to demonstrate exclude convention. For the multi-mention INCONSISTENT case use `policy = 'allMustMatch'` and intentionally do NOT add the exclude (so both mentions survive and trigger INCONSISTENT).
>
> Tests in `DocParser.Tests.ps1`:
>
> - Mentions for `agp` in TECH_REQUIREMENTS extract to `@('9.2.0')`.
> - Mentions for `hilt-android` in TECH_REQUIREMENTS extract to `@('2.50','2.57.2')` (both, since exclude omitted in this test entry).
> - Mentions for `kotlin` in CLAUDE.md extract to `@('1.9+')`.
> - Mentions for `agp` in TECH_STACK extract to `@()` (missing - but matcher was `$null`, so SKIP path).
> - `exclude` strips matching span: separate test using a manifest entry WITH `exclude` produces only `@('2.50')`.
>
> Tests in `Comparator.Tests.ps1`:
>
> - `agp` drifted → record `Status = 'FAIL'`, `Gradle = '9.2.1'`, `DocValue = '9.2.0'`.
> - `hilt-android` two values → record `Status = 'INCONSISTENT'`, `DocValue` contains both versions separated by ` vs `.
> - `media3` exact match → record `Status = 'PASS'`.
> - `kotlin 1.9+` with Gradle 2.2.10 → record `Status = 'WARN'`.
> - `agp` missing from required doc → record `Status = 'MISSING'`.
> - `agp` missing from non-required doc → record `Status = 'SKIP'`.
>
> Each test sets up its own minimal `$gradle` hashtable and `$mentions` array directly (no real parser call) so the comparator can be unit-tested in isolation.

**Verification:**

- `Glob` - all four fixture files exist; both `*.Tests.ps1` files exist.
- `Grep` - in `Comparator.Tests.ps1`, all six classification status literals tested: `'PASS'`, `'FAIL'`, `'WARN'`, `'SKIP'`, `'MISSING'`, `'INCONSISTENT'`.
- Run `pwsh -NoProfile -File ./scripts/doc-drift.tests/Run-Tests.ps1`. Expected: exits `0`, `pass: >= 17` (6 parser + 5 doc + 6 comparator) | actual: capture summary.

**Status:** `[x] done`

---

### Step 05.4 - Operator README + integration assertion

**Files:** `scripts/doc-drift/README.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Write `scripts/doc-drift/README.md` - operator-facing usage doc. Sections:
>
> - `## Purpose` - one paragraph: chequer compares declared versions in `docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`, `CLAUDE.md` against canonical Gradle sources; surface drift before it costs research time. Cite `PLAN/S0271_truth_drift_detection.md`.
> - `## Quick start` - the three canonical invocations: full run (`pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1`), single-pin probe (`-Pin agp`), bootstrap mode (`-AsBootstrapWarning`).
> - `## Output grammar` - copy the D-5 record templates from `DECISIONS.md` verbatim so operators don't need to open the spec.
> - `## Adding a new pin` - point at `pins.psd1`, show one minimal example entry, list the per-doc required-flag matrix.
> - `## Tests` - one line: `pwsh -NoProfile -File scripts/doc-drift.tests/Run-Tests.ps1` returns `0` with all asserts passing.
> - `## Limitations` - explicit list of Non-goals from strategic §2 (no auto-fix, no wear/, no libs.versions.toml, no new pin discovery beyond manifest).
> - `## Exit codes` - `0` no FAIL/INCONSISTENT/MISSING; `1` otherwise; `-AsBootstrapWarning` forces `0`.
>
> After writing, re-run the smoke chequer from Phase 04 step 04.4 and re-run the test harness. Both must still pass.

**Verification:**

- `Glob` - `scripts/doc-drift/README.md` exists.
- `Grep` - all seven section headings (`## Purpose`, `## Quick start`, `## Output grammar`, `## Adding a new pin`, `## Tests`, `## Limitations`, `## Exit codes`) present.
- Run `pwsh -NoProfile -File ./scripts/doc-drift.tests/Run-Tests.ps1`. Expected: exits `0`, `fail: 0` | actual: capture.
- Run `pwsh -NoProfile -File ./scripts/check-doc-vs-gradle.ps1 > temp/S0271_phase05_smoke.log 2>&1`. Capture exit code (expected `1` - drift still present) | actual: capture.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `Run-Tests.ps1` exits `0` with `fail: 0` (`expected: 0 | actual: 0` recorded explicitly).
- [x] End-to-end smoke exit code `1` (drift exists on current repo - strategic §11 #2).
- [x] Grep for unresolved phase-05 placeholder markers returns zero hits.
- [x] Dev log entries added for all created files.

---

## Handoff Notes to Next Phase

Phase 06 is mechanical cleanup. The substance of S0271 is complete after Phase 05.

---

## Rollback Plan

Delete `scripts/doc-drift.tests/` folder and `scripts/doc-drift/README.md`. No data migration. Chequer continues to work without tests, but the contract is no longer locked.

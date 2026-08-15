# Phase 01 - Restore Test-Source-Set Compilation

**Strategic spec:** [`../S0457_bugfix-unit-test-source-set-broken-compile.md`](../S0457_bugfix-unit-test-source-set-broken-compile.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Add a defaulted `MediaCapabilities` test factory and route every stale test construction through it (or pass it where a UseCase requires it), so the `app_v2` unit-test source set compiles again. No production code changes.

---

## Prerequisites

- [ ] Strategic §6 has no open research items (none).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/testutil/TestMediaCapabilities.kt` | New | ≤ 40 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ApplyEnableAllSettingsUseCaseTest.kt` | Modified | ≤ 130 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCaseTest.kt` | Modified | ≤ 130 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolveOpenInFmsTargetUseCaseTest.kt` | Modified | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCaseTest.kt` | Modified | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlannerTest.kt` | Modified | ≤ 120 |

> All files are test-only (`src/test`). No production source is touched. The factory is the single creation point so a future `MediaCapabilities` field addition only updates this one file.

---

## Steps

### Step 01.1 - Add a defaulted MediaCapabilities test factory

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/testutil/TestMediaCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a top-level test helper `fun testMediaCapabilities(...) : MediaCapabilities` in package `com.sza.fastmediasorter.testutil`. Give it one parameter per `MediaCapabilities` field (currently 12: `supportsVideo`, `supportsAudio`, `supportsImages`, `supportsDocuments`, `supportsEpub`, `supportsCloud`, `supportsLocalNetworkSources`, `supportsDefaultPlayer`, `supportsCast`, `supportsMicRecording`, `supportsVrPlayer`, `supportsWearCompanion`), each defaulting to `true`, and return `MediaCapabilities(...)` passing them through by name. This is the single test-side construction point; adding a future field means adding one defaulted parameter here only. Do not add defaults to the production `MediaCapabilities` data class.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/testutil/TestMediaCapabilities.kt` exists.
- `Grep` - `fun testMediaCapabilities(` present exactly once.
- `Grep` - the function passes all 12 field names to `MediaCapabilities(`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS (file exists; `fun testMediaCapabilities(` once; 12 named fields forwarded to `MediaCapabilities(`). Files: TestMediaCapabilities.kt (New, +37 lines).

---

### Step 01.2 - Route all stale test constructions through the factory

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ApplyEnableAllSettingsUseCaseTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCaseTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolveOpenInFmsTargetUseCaseTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCaseTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlannerTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Fix the five stale test sites to compile against current signatures, all via `testMediaCapabilities(..)`:
> 1. `ApplyEnableAllSettingsUseCaseTest` - its `caps(..)` helper builds `MediaCapabilities(..)` directly with only 7 fields. Replace the direct constructor call with `testMediaCapabilities(..)`, forwarding the helper's existing parameters (`video`, `audio`, `images`, `documents`, `epub`, `cloud`, `defaultPlayer`) by name; the factory defaults the remaining fields. Keep the helper's own parameter list and tests unchanged.
> 2. `ProvisionDefaultResourcesUseCase`, `ResolveOpenInFmsTargetUseCase`, `ScanLocalFoldersUseCase` each take `mediaCapabilities` as the final constructor parameter; their tests omit it. Pass `mediaCapabilities = testMediaCapabilities()` in each constructor call. Where a specific test asserts capability-driven behaviour, override only the relevant fields (e.g. `testMediaCapabilities(supportsAudio = false)`) instead of the all-true default.
> 3. `CommandPanelLayoutPlannerTest` constructs `CommandPanelLayoutPlanner` (sole parameter `mediaCapabilities`); pass `testMediaCapabilities()` (with field overrides where the test's assertion depends on them).
> Import `com.sza.fastmediasorter.testutil.testMediaCapabilities` in each file. Touch nothing beyond the construction sites and imports.

**Verification:**

- `Grep` - `testMediaCapabilities(` referenced in all five test files.
- `Grep` - no remaining `MediaCapabilities(` direct constructor call in `ApplyEnableAllSettingsUseCaseTest.kt`.
- `Grep` - each of the four UseCase/planner constructor calls now passes `mediaCapabilities` / `testMediaCapabilities(`.
- Targeted compile+run at Phase Done: `testStandardDebugUnitTest` compiles and the five classes report green.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification grep PASS (all 5 sites reference `testMediaCapabilities(`; `ApplyEnableAllSettingsUseCaseTest` no longer calls `MediaCapabilities(` directly). All sites use all-true defaults - the prior unconditional behaviour before the UseCases gained `mediaCapabilities`. Compile+run validated at Phase Done. Files: 5 test files (imports + constructor sites).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Unit-test source set compiles - targeted `testStandardDebugUnitTest` run BUILD SUCCESSFUL (no `compileStandardDebugUnitTestKotlin` failure).
- [x] The five previously-failing classes plus `PermissionRegistryRepositoryImplTest` (S0456) ran green (targeted run exit 0).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1`.

---

## Handoff Notes to Next Phase

The `app_v2` unit-test source set compiles and runs again; `testMediaCapabilities(..)` is the single test construction point for the capability config. Phase 02 only reconciles catalog/changelog (no FEATURES change per strategic §8). Closing S0457 unblocks S0456.

---

## Rollback Plan

Revert the phase commit(s) - test-only changes; no production code, no persisted state, no user-facing surface.

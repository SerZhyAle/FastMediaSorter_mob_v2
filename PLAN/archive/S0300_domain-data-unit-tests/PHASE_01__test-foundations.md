# Phase 01 - Test Foundations & Coverage Inventory

**Strategic spec:** [`../S0300_domain-data-unit-tests.md`](../S0300_domain-data-unit-tests.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 5 / 5
**Started:** 2026-05-29
**Completed:** 2026-05-29

---

## Objective

Establish the shared JVM test harness (coroutine dispatcher rule, domain-model factories, reusable fakes for repositories/data sources, in-memory Room helper) and produce `COVERAGE_INVENTORY.md` - the authoritative per-class work-list for all later phases. No production code changes.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved (see INDEX Pre-Implementation Blockers - all checked).
- [ ] Working tree is clean or on a feature branch.
- [ ] Existing `app_v2/src/test/` tree builds under `testStandardDebugUnitTest`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/testing/MainDispatcherRule.kt` | New | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/testing/DomainModelFactories.kt` | New | ≤ 250 |
| `app_v2/src/test/java/com/sza/fastmediasorter/testing/fakes/` (fake repos/sources) | New | ≤ 400 |
| `app_v2/src/test/java/com/sza/fastmediasorter/testing/InMemoryRoomHelper.kt` | New | ≤ 120 |
| `PLAN/S0300_domain-data-unit-tests/COVERAGE_INVENTORY.md` | New | n/a |

> All files are in test source sets or `PLAN/`; no production code touched. No landscape layouts involved.

---

## Steps

### Step 01.1 - Add shared coroutine dispatcher rule

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/testing/MainDispatcherRule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a JUnit4 `TestWatcher` rule `MainDispatcherRule` that installs a `StandardTestDispatcher` (or injectable `TestDispatcher`) as `Dispatchers.Main` for the duration of a test and resets it afterwards. This becomes the single canonical way to drive coroutine-based domain/data logic deterministically. Use Timber-free, framework-only code.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/testing/MainDispatcherRule.kt` exists.
- `Grep` - `class MainDispatcherRule` matches exactly once.
- `Grep` - `Dispatchers.setMain` present.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-29 - Verification 4/4 PASS (exists; `class MainDispatcherRule` ×1; `Dispatchers.setMain` ×1; `Log.d` ×0). Files: testing/MainDispatcherRule.kt (+37 LOC). Dev log recorded.

---

### Step 01.2 - Add domain-model factories

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/testing/DomainModelFactories.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add factory functions producing valid default instances of the most-used domain models (the `domain/model` types referenced by use cases under test), each with named-argument overrides. Goal: tests construct fixtures via one call instead of duplicating constructor boilerplate. Cover only models the inventory (Step 01.5) marks as needed by in-scope classes.

**Verification:**

- `Glob` - `DomainModelFactories.kt` exists.
- `Grep` - `fun create` matches at least once (factory functions present).
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-29 - Verification 3/3 PASS (exists; `fun create` ×7; `Log.d` ×0). Factories: MediaResource, ScheduledOperation, MediaFile, AppSettings, ResumeState, FileFilter. Compiles via `:app_v2:compileStandardDebugUnitTestKotlin` (exit 0). Dev log recorded.

---

### Step 01.3 - Add reusable fakes for repositories and data sources

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/testing/fakes/`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add hand-written fake implementations of the repository interfaces (`domain/repository`) and the key data-source contracts that recur across use cases, each exposing controllable return values and recorded invocations. These fakes replace ad-hoc MockK setup for cross-cutting dependencies; MockK stays available for one-off stubbing. No real I/O, network, or disk access.

**Verification:**

- `Glob` - at least one `*.kt` exists under `.../testing/fakes/`.
- `Grep` - `class Fake` matches at least once across the directory.
- `Grep -n "Log\.d\("` - zero hits across the directory.

**Status:** `[x]` done

**Step Log:**

- 2026-05-29 - Verification 3/3 PASS (4 .kt under testing/fakes/; `class Fake` ×4; `Log.d` ×0). Fakes: ResourceRepository, SettingsRepository, ScheduledOperationRepository, FavoritesRepository - all Flow-backed by MutableStateFlow, signatures match interfaces. Compiles (exit 0). Dev log recorded.

---

### Step 01.4 - Add in-memory Room helper

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/testing/InMemoryRoomHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a helper that builds the app Room database via `Room.inMemoryDatabaseBuilder(..).allowMainThreadQueries()` for tests that exercise real DAO/query logic (per strategic §6.3). Provide open/close lifecycle suitable for a `@get:Rule` or `@Before`/`@After`. This is used only where DAO logic itself is under test; otherwise data sources are faked (Step 01.3).

**Verification:**

- `Glob` - `InMemoryRoomHelper.kt` exists.
- `Grep` - `inMemoryDatabaseBuilder` present.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-29 - Verification 3/3 PASS (exists; `inMemoryDatabaseBuilder` ×2; `Log.d` ×0). Provides `createInMemoryAppDatabase(context)` + `InMemoryRoomRule`. Converters has no-arg ctor → plain builder sufficient. Compiles (exit 0). Dev log recorded.

---

### Step 01.5 - Produce coverage inventory

**Files:** `PLAN/S0300_domain-data-unit-tests/COVERAGE_INVENTORY.md`
**Depends on:** - independent of Steps 01.1–01.4

**Prompt for developer:**

> Enumerate every class under `app_v2/src/main/java/com/sza/fastmediasorter/domain/` and `.../data/` (use `dev/CATALOG/app_v2.jsonl` as the source). For each, record: fully-qualified class, owning phase (02–07 per the package map below), in-scope flag per the cutoff (branch/transform/error logic = in; pure data/enum/thin delegate = out), and current test status (has a `*Test.kt` or not). Group rows by phase. Mark flavor-only classes (`noLegal`/`vr`) for Phase 07. This file is the work-list and progress tracker for Phases 02–07.
>
> Phase package map: 02 = `domain/usecase**`, `domain/playback`, `domain/mutation`, `domain/verifier`, `domain/hash`, `domain/path`, `domain/files`; 03 = `domain/model**`, `domain/strategy`, `domain/identity`, `domain/input**`, `domain/ocr`, `domain/transfer`; 04 = `data/repository`, `data/local**`, `data/observer`, `data/paging`; 05 = `data/network**`, `data/remote**`; 06 = `data/transfer**`, `data/link**`, `data/cloud**`, `data/browser`, `data/input`, `data/hash`, `data/verifier`, `data/permissions`, `data/glide`.

**Verification:**

- `Glob` - `PLAN/S0300_domain-data-unit-tests/COVERAGE_INVENTORY.md` exists.
- `Grep` - a per-phase heading for each of `02`..`07` is present.
- `Grep` - both `in-scope` and `out` markers appear (cutoff applied, not blanket-in).
- Manual: spot-check 3 rows against the actual source - `expected: class is in/out per its logic | actual: matches inventory flag`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-29 - Verification PASS. Headings 02..07 present; `in`/`out` markers both present; spot-check (CleanupTrashFoldersUseCase=in+tested, ArtStationExtractionStrategy=in) matches source. Inventory: 593 classes in target packages, 382 in-scope. Generated via `temp/gen_s0300_inventory.py` from `dev/CATALOG/app_v2.jsonl`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `:app_v2:compileStandardDebugUnitTestKotlin` exit 0 (compiles the new test sources, which transitively compiles `main`; strictly stronger than `assembleStandardDebug` for a test-only change - documented equivalence).
- [x] `testStandardDebugUnitTest` for the new `testing/` package compiles - proven by the same task (exit 0).
- [x] `Grep` for `TODO(phase-01)` returns zero hits. `expected: 0 | actual: 0`.
- [x] `Grep -n "Log\.d\("` returns zero hits across new `testing/` files. `expected: 0 | actual: 0`.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The shared harness (`MainDispatcherRule`, `DomainModelFactories`, `testing/fakes/`, `InMemoryRoomHelper`) is the mandatory base for every later test - phases must reuse it, not re-implement dispatcher/fixture plumbing. `COVERAGE_INVENTORY.md` is the per-class work-list; each later phase processes its rows and flips the test-status column.

---

## Rollback Plan

Delete the new `testing/` files and `COVERAGE_INVENTORY.md`. No production code or user-facing surface changed.

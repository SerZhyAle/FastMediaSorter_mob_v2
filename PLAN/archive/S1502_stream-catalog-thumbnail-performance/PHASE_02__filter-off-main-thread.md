# Phase 02 - Move the catalog pass off the main thread

**Strategic spec:** [`../S1502_stream-catalog-thumbnail-performance.md`](../S1502_stream-catalog-thumbnail-performance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Run the catalog filter/sort transform on `Dispatchers.Default` instead of the main thread, leaving the filter logic itself and the emitted state shape unchanged.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done and `temp/S1502/baseline/` holds the before-numbers.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.2 resolved; §6.1's after-measurement is Phase 05's, and ADR-1 orders pillar A first.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 720 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterTest.kt` | Modified | ≤ 280 |

> `StreamsViewModel.kt` is 697 LOC, over the 500-LOC line, so Step 02.1 takes a timestamped backup first (CLAUDE.md Rule 5).

---

## Steps

### Step 02.1 - Back up the ViewModel before editing

**Files:** `temp/S1502/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` to `temp/S1502/StreamsViewModel.<yyyyMMdd-HHmmss>.kt.bak` before any edit in this phase. Do not touch `temp/S1502/baseline/` - Phase 01's numbers live there and are not reproducible.

**Why:**

CLAUDE.md Rule 5 requires a timestamped backup before editing a file over 500 LOC, and this file is 697 LOC.

**Verification:**

- `Glob` - `temp/S1502/StreamsViewModel.*.kt.bak` matches at least one file.
- `Glob` - `temp/S1502/baseline/*.json` still matches the baseline set.

**Status:** `[x]` done

---

### Step 02.2 - Inject the default dispatcher and move the combine onto it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a constructor parameter `@DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher` to `StreamsViewModel`, using the qualifier already declared in `app_v2/src/main/java/com/sza/fastmediasorter/core/di/AppModule.kt`. Insert `.flowOn(defaultDispatcher)` between the `combine { .. }` block and the `.onEach { .. }` call in the `init` block, so the transform runs off the main thread while `onEach` and the resulting `_state` write stay on the collector's context. Do not change `applyFilter`, `matchesFacets`, `cachedFacetsOf` or the emitted `StreamsUiState` shape in this step. `BrowseViewModel.kt:80` is the in-repo precedent for the same construction.
>
> The class already carries `@Suppress("LongParameterList")`; keep it and add no second suppression.

**Why:**

Strategic §5.1 pillar A states that combining the source and the filter on a background dispatcher is the cheapest-risk step and by itself removes the sticking-input symptom, and §2 goal 1 requires that typing in the search box stop blocking the main thread.

**Verification:**

- `Grep` - `@DefaultDispatcher` matches in `StreamsViewModel.kt`.
- `Grep` - `flowOn(defaultDispatcher)` matches exactly once in `StreamsViewModel.kt`.
- `Grep` - `import kotlinx.coroutines.flow.flowOn` present in `StreamsViewModel.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[ ]` not done

---

### Step 02.3 - Pin the filter's purity with a unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a test to `StreamsFilterTest` asserting that `StreamsViewModel.applyFilter` returns the same ordered id list when called twice with identical inputs, and that it does not mutate the input list. Keep it in the existing pure-companion style - no Hilt graph, no dispatcher.

**Why:**

Strategic §7 records that moving the pass to a background dispatcher risks a race with the filter state, mitigated by keeping the filter a pure side-effect-free function; a test that pins purity is what makes that mitigation checkable rather than asserted.

**Verification:**

- `Grep` - the new `@Test` function name matches in `StreamsFilterTest.kt`.
- `.\a.ps1 fu` - `StreamsFilterTest` passes (read the JUnit XML, per CLAUDE.md §12).

**Status:** `[ ]` not done

---

## Step Log

- 2026-08-08 - Step 02.1 DONE. `temp/S1502/StreamsViewModel.20260808-030448.kt.bak` written; `temp/S1502/baseline/` still holds six files (five checkpoints plus `device.json`).
- 2026-08-08 - Step 02.2 queued behind `CODE.LOCK`, held by a sibling session running `/spec-dev S1471 step 01.1`. Waited 96 s on `wait-for-lock-turn.ps1` in the background per CLAUDE.md Rule 23 rather than editing under another session's build; used the wait to extract the version-47 `stream_sources` DDL into Phase 04.
- 2026-08-08 - Step 02.2 DONE. `@DefaultDispatcher CoroutineDispatcher` injected into `StreamsViewModel`; `.flowOn(defaultDispatcher)` inserted between the `combine` block and `.onEach`. `.\a.ps1 fk` exit 0. The dispatcher is injected rather than hardcoded so a test can substitute a deterministic one.
- 2026-08-08 - Step 02.3 DONE. `applyFilter is pure - repeatable result and untouched input` added to `StreamsFilterTest`, pinning the property that makes the background dispatcher safe (strategic §7's stated mitigation).
- 2026-08-08 - Constructor fallout, found and fixed. `StreamsViewModelAutoGridTest` builds `StreamsViewModel` by hand, so the new parameter broke `compileStandardDebugUnitTestKotlin`. It now passes `dispatcherRule.testDispatcher` as `defaultDispatcher`, which keeps the emission synchronous under the test rule - the reason the dispatcher was injected rather than hardcoded in the first place.
  - Two process notes worth carrying forward. First, `.\a.ps1 fu` reported `coverage ratio 0` and the gate's own advisory text mentions `OutOfMemoryError`, which reads like an OOM; the actual cause was a compile failure in the unit-test source set, visible only in the full log. Second, the first diagnosis attempt was blind because the run had been piped through `Select-Object -Last 20`, discarding the gradle output - the re-run wrote `temp/S1502/fu-run.log` instead.
  - The earlier screening grep over this test file searched for `applyFilter` and `sources` and found nothing, so the file was wrongly cleared. A constructor change has to be screened by searching for the class being constructed, not for the members being changed.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, then `.\a.ps1 dq` exit 0. The full debug build is what proves the Hilt graph resolves `@DefaultDispatcher`; `fk` compiles without validating it.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entries added via `post-change.ps1`.
- [x] Public API unchanged apart from the constructor parameter - no catalog regeneration needed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

Unit suite: `3267 tests completed, 1 failed`. The failure is `IconInventoryExportTest > committed icon inventory is fresh`, which is not this phase's: no file in "Files Touched" is an icon source or icon doc, and `icon-inventory-sync-gate` reported "not applicable" on every closure in this ticket. It is a sibling session's in-flight state, recorded rather than fixed - fixing another ticket's WIP is how two sessions collide. This phase's own tests: `StreamsFilterTest` 14 passed / 0 failed, `StreamsViewModelAutoGridTest` 4 passed / 0 failed.

### Phase-boundary audit (Layers 1-2; Layers 3-4 not applicable - no listener, no Room surface touched)

- **Layer 2, examined, no finding.** `cachedFacetsOf` mutates two non-`@Volatile` fields, `facetsSourceSnapshot` and `facetsCache`, and after this phase it runs on `Dispatchers.Default` rather than on `Main.immediate` - the exact shape that usually is a race. It is not one here: `flowOn` runs the whole upstream, transform included, in a single coroutine, so the two fields are touched sequentially and by nothing else in the class (checked - `cachedFacetsOf` is their only reader and only writer). Coroutine dispatch establishes happens-before across the thread migration inside the pool, so visibility holds without `@Volatile`.
  - This stops being true the moment a second collector or a `stateIn` shares that upstream, or anything outside `cachedFacetsOf` reads either field. Phase 04 adds a second flow beside this one and must keep it beside, not folded in.
- **Layer 1, no finding.** The change is one injected parameter and one operator. The comment states the measured 21 % figure rather than restating the code.
- **Not deferred.** No P0/P1 raised, so nothing was carried past the boundary.

---

## Handoff Notes to Next Phase

The transform now runs on `Dispatchers.Default`, so any work Phase 03 adds inside the `combine` block is off the main thread by construction. `StreamsUiState` still carries only the flat `sources` list; Phase 03 is what widens it.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed.

# Phase 03 - Cheapen the catalog pass

**Strategic spec:** [`../S1502_stream-catalog-thumbnail-performance.md`](../S1502_stream-catalog-thumbnail-performance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 6 / 6
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Remove the per-row string allocation from the query match and compute the pinned/unpinned split once per emission instead of three times, without changing which channels the filter returns.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 740 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsSectionsManager.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1360 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterTest.kt` | Modified | ≤ 330 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterScaleTest.kt` | New | ≤ 160 |

> `StreamsActivity.kt` is 1343 LOC against the 1500-LOC ceiling; this phase's edits to it are substitutions at three call sites and must not add net lines. Take a fresh timestamped backup of both files over 500 LOC into `temp/S1502/` before the first edit, and leave `temp/S1502/baseline/` alone.

---

## Steps

### Step 03.1 - Match the query without allocating per row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `matchesFacets`, replace `source.title.lowercase().contains(query)`, `source.topic?.lowercase()?.contains(query)` and `source.language?.lowercase()?.contains(query)` with `contains(query, ignoreCase = true)` calls on the raw fields, and stop lowercasing `filter.query` in `applyFilter` - keep the `trim()`. Update the `applyFilter` KDoc, which currently states that the query arrives pre-lowercased, and the `matchesFacets` KDoc, which repeats it.
>
> Keep the disjunct order (title, then topic, then language) so the existing short-circuit behaviour is unchanged.

**Why:**

Strategic §5.1 pillar B requires that lowercasing stop happening per catalog row on every keystroke, and §3.2 caps the floor device at a 128 MB heap, which is what tens of thousands of transient strings per typed character are measured against.

**Verification:**

- `Grep` - `lowercase()` returns zero hits inside `matchesFacets` in `StreamsViewModel.kt`.
- `Grep` - `ignoreCase = true` matches at least three times in `StreamsViewModel.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - `lowercase()` inside `matchesFacets`: 0 hits; `ignoreCase = true` in the file: 4 hits; `.\a.ps1 fk` exit 0.

---

### Step 03.2 - Carry the pinned/unpinned split in the state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `pinned: List<StreamSourceEntity> = emptyList()` and `unpinned: List<StreamSourceEntity> = emptyList()` to `StreamsUiState`. Change `applyFilter` to return both halves instead of the concatenated list - introduce an `internal data class FilteredStreams(val pinned: List<StreamSourceEntity>, val unpinned: List<StreamSourceEntity>)` in the companion and have `applyFilter` return it, with `sources` in the state built as `pinned + unpinned` at the single point where the state is constructed. The existing `partition` inside `applyFilter` is the one that survives; no other code may partition again.
>
> Keep `applyFilter` `internal` so `StreamsFilterTest` still reaches it without the Hilt graph.

**Why:**

Strategic §2 goal 3 requires the pinned/unpinned split to run once per emission rather than three times, and §5.1 pillar B names reusing one computed split as the way to get there.

**Verification:**

- `Grep` - `data class FilteredStreams` matches exactly once in `StreamsViewModel.kt`.
- `Grep` - `val pinned:` and `val unpinned:` both match inside the `StreamsUiState` declaration.
- `Grep` - `partition { it.pinned }` matches exactly once in `StreamsViewModel.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - `data class FilteredStreams`: 1 hit; `pinned` / `unpinned` present in `StreamsUiState`; `partition { it.pinned }`: 1 hit; `.\a.ps1 fk` exit 0.

**Deviation from the prompt:** `FilteredStreams` is declared at class level beside `StreamsUiState`, not inside the companion. A classifier nested in a companion object is only reachable as `StreamsViewModel.Companion.FilteredStreams`, which would have made every call site and the test file read worse for no gain; class level matches how `StreamsUiState`, `StreamsFacets` and `StreamsFilter` are already declared in this file. The companion's `applyFilter` references it unqualified either way.

---

### Step 03.3 - Consume the precomputed split in the sections manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsSectionsManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Change `StreamsSectionsManager.applyMode(mode, sources)` to `applyMode(mode, pinned, unpinned)` and `submitList(sources)` to `submitList(pinned, unpinned)`, deleting the `sources.partition { it.pinned }` line from each. Update the two call sites at `StreamsActivity.kt:715` and `StreamsActivity.kt:717` to pass `state.pinned` and `state.unpinned`. Update the class KDoc, which currently says this manager owns the split.

**Why:**

Strategic §2 goal 3 counts this manager as two of the three redundant passes, and §5.1 pillar B requires the split be computed once and handed onward already divided.

**Verification:**

- `Grep` - `partition` returns zero hits in `StreamsSectionsManager.kt`.
- `Grep` - `submitList(state.pinned, state.unpinned)` matches in `StreamsActivity.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - `partition` in `StreamsSectionsManager.kt`: 0 hits; `submitList(state.pinned, state.unpinned)` present in `StreamsActivity.kt`; `.\a.ps1 fk` exit 0.

**Note on the prompt's line numbers:** the two call sites were at `StreamsActivity.kt:715` / `:717` as stated, but the `visibleSources` partition named by Step 03.4 sat at `:812`, not the line the plan implied. Both were located by grep rather than by line number.

---

### Step 03.4 - Consume the precomputed split in the probe scope

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `visibleSources()` replace `latestState.sources.partition { it.pinned }` with `latestState.pinned` and `latestState.unpinned`. The rest of the function, including the XOR-so-no-dedup reasoning in its KDoc, is unchanged.

**Why:**

Strategic §2 goal 3 names this the third redundant split, and it runs on the main thread inside a user-initiated refresh where the whole catalog is walked to find the handful of rows on screen.

**Verification:**

- `Grep` - `partition { it.pinned }` returns zero hits in `StreamsActivity.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - `partition { it.pinned }` in `StreamsActivity.kt`: 0 hits; `.\a.ps1 fk` exit 0. `visibleSources()` became an expression body reading `latestState.pinned` / `latestState.unpinned`; the XOR-so-no-dedup KDoc is unchanged as instructed.

---

### Step 03.5 - Pin the search contract across the whole catalog

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterTest.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Add tests covering: a mixed-case query matching a title, a topic and a language regardless of the case of either side; a query matching a channel that is neither pinned nor near the start of the list; and `FilteredStreams.pinned + FilteredStreams.unpinned` equalling the ordering the previous flat `applyFilter` produced for the same input. Adapt the existing assertions to the new return type rather than duplicating them.

**Why:**

Strategic §11 criterion 8 requires that search still find a channel by name, topic and language across the whole catalog rather than only part of it, and the case-folding change in Step 03.1 is exactly the kind of edit that can silently narrow it.

**Verification:**

- `Grep` - at least three new `@Test` functions match in `StreamsFilterTest.kt`.
- `.\a.ps1 fu` - `StreamsFilterTest` passes (read the JUnit XML, per CLAUDE.md §12).

**Status:** `[x]` done - `@Test` in `StreamsFilterTest.kt`: 17 (3 new). JUnit XML `TEST-..StreamsFilterTest.xml`: `tests="17" skipped="0" failures="0" errors="0"`.

The three added tests are the case-folding contract per field, a match on an unpinned row at the far end of the catalog, and the two halves concatenating back to the flat pinned-first order. The existing assertions were adapted by re-typing the `ids` helper to take `FilteredStreams` and adding an `ordered()` join, so no assertion was duplicated.

---

### Step 03.6 - Guard the filter pass against algorithmic regression in CI

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterScaleTest.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Add a JVM test that builds 20,000 synthetic `StreamSourceEntity` rows with varied titles, topics, languages and countries, then asserts:
>
> 1. `applyFilter` over the full set with a non-empty query completes within a generous wall-clock ceiling - pick one that a linear pass clears by a wide margin on CI hardware and that a quadratic or re-allocating implementation cannot, and state the reasoning in the test's KDoc rather than presenting the number as a device budget.
> 2. Filtering by a query that matches a single row in the last thousand still returns that row, so the guard cannot be satisfied by truncating the catalog.
>
> This test is a shape guard, not a device measurement - say so in its KDoc so a later reader does not mistake it for the acceptance number.

**Why:**

Strategic §2 goal 5 asks for a reproducible way to see the next regression before release, and research artifact 01 records that no test exercises anything near 20k rows, so keystroke cost at real N is unmeasured in CI as well as on device.

**Verification:**

- `Glob` - `StreamsFilterScaleTest.kt` exists.
- `Grep` - `20_000` or `20000` matches in that file.
- `.\a.ps1 fu` - `StreamsFilterScaleTest` passes (read the JUnit XML, per CLAUDE.md §12).

**Status:** `[x]` done - `StreamsFilterScaleTest.kt` exists; `20_000` matches. JUnit XML: `tests="2" skipped="0" failures="0" errors="0"`.

Measured: the 20,000-row filter pass ran in **0.115 s** including the warm-up call, against the 2,000 ms shape ceiling - clearing it by ~17x, which is the margin the KDoc claims a linear pass has. Per the step prompt this is a shape guard, not a device number, and the KDoc says so explicitly so it cannot later be quoted as a §11 acceptance figure.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` (`:app_v2:compileStandardDebugKotlin`) exit 0, and the unit run compiled the test source set too. Per the CLAUDE.md §12 validation ladder this is the right rung: the phase changes Kotlin symbols only, with no resource, manifest or packaging change to prove.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via `post-change.ps1`.
- [x] `StreamsActivity.kt` still under 1500 LOC - 1347 (was 1348).
- [x] Phase-boundary audit run - no P0/P1 findings. See below.

### Line budgets

| File | Budget | Actual |
|------|-------:|-------:|
| `StreamsViewModel.kt` | 740 | 734 |
| `StreamsSectionsManager.kt` | 160 | 142 |
| `StreamsActivity.kt` | 1360 | 1347 |
| `StreamsFilterTest.kt` | 330 | 319 |
| `StreamsFilterScaleTest.kt` | 160 | 101 |

---

## Phase-boundary audit

Triggers that fired: coroutine/Flow change (the emission now carries a different shape), shared-state change (`StreamsUiState`), and the phase boundary itself.

**No P0/P1 findings.**

- **Layer discipline - improved, not violated.** The split moved *up* from a UI helper (`StreamsSectionsManager`) and the Activity into the ViewModel. Both former sites now render what they are handed instead of deriving it, which is the direction CLAUDE.md section 8 prescribes.
- **Purity under `flowOn` holds.** Phase 02 moved the pass onto `defaultDispatcher`; that is only safe while `applyFilter` stays pure. `FilteredStreams` is an immutable holder of two immutable lists and the existing purity test still passes, so the new return type introduces no shared mutable state.
- **No consumer of the changed API was missed.** `applyFilter` / `StreamsUiState` were grepped across every source set including `debug`, `test` and `androidTest`: `StreamsViewModelAutoGridTest` touches neither, and `StreamsFilterDialogManager` takes the state but reads only `filter` / `facets`. `state.sources` is written at exactly one place and read at exactly three.
- **Case folding is not narrowed.** `contains(query, ignoreCase = true)` and the old `lowercase().contains(query)` agree on Latin and Cyrillic, which is what the catalog holds; they diverge only on Unicode where case mapping changes length (Turkish `İ`, `ß`), and the old form was already wrong there. Pinned by the new per-field case test rather than left as an assertion.
- **P3, informational - `sources` now exists only for a count.** Its three consumers are two `tryRestoreScroll(..size)` calls and `isEmpty`; none needs the flat list itself, so the `pinned + unpinned` join copies ~20k references per emission to compute a size. It is off the main thread (inside the `flowOn` block) and the previous code allocated the same concatenation, so this is not a regression and not worth deviating from the step prompt for. Recorded so a later reader does not mistake it for an oversight.

---

## Handoff Notes to Next Phase

`StreamsUiState` now carries `pinned` and `unpinned` already split, and nothing downstream partitions. Phase 04 adds a second, independent flow beside this state and must not fold the outcome map into `applyFilter` - that would put a per-keystroke dependency back onto a per-probe signal.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed. The `StreamsUiState` shape change is source-only.

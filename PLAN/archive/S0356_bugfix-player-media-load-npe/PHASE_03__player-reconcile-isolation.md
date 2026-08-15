# Phase 03 - Player Reconcile Isolation

**Strategic spec:** [`../S0356_bugfix-player-media-load-npe.md`](../S0356_bugfix-player-media-load-npe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Make the favorites-reconcile pass in the player tolerate a single corrupted element: a `copy()` that throws on one item must not abort the whole list load. Defense-in-depth behind the Phase 02 upstream guard.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` | Modified | ≤ 520 |

> No layout files. Shared `src/main` - no flavor source set involved.

---

## Steps

### Step 03.1 - Isolate per-element failure in the favorites-reconcile map

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `loadMediaFiles()`, the favorites-reconcile block (around line 349-359) maps `files.map { file -> file.copy(isFavorite = favoriteMap[file.path] == true) }` inside a single try/catch. Change the map so a failure on one element does not discard the entire result: wrap the per-element `copy()` so a throwing element is kept in its original (pre-reconcile) form rather than aborting the whole list, and log one `Timber` line naming the offending element's path (level set in Phase 04). The outer try/catch may remain as a last-resort net. Net effect: a single corrupted element degrades to "favorite flag not reconciled for that one item", never "list load failed".

**Verification:**

- `Grep` - the favorites-reconcile block in `PlayerMediaFilesLoader.kt` contains a per-element guard (e.g. `runCatching` or an inner `try` inside the `.map { }`), verified by `Grep` for `runCatching` or `try {` within the reconcile region.
- `Grep` - the original `file.copy(isFavorite = favoriteMap[file.path] == true)` intent is preserved (favorite flag still applied on the success path).
- `Grep -n "Log\.d\("` - zero hits in `PlayerMediaFilesLoader.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. `reconcileFavoriteFlags` extracted as internal fn: `files.map` with per-element `runCatching { copyFavorite(file, isFavorite) }.onFailure {..}.getOrElse { file }` - a throwing element is kept in original form, siblings continue. copy intent preserved (`file.copy(isFavorite = isFavorite)` default). Outer try/catch retained as last-resort net. Log.d expected 0 | actual 0.

---

### Step 03.2 - Add a focused unit test for the isolation behavior

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoaderReconcileTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a unit test that exercises the reconcile isolation: given a list where one element throws on `copy()` (or stands in for the corrupted case), assert the load still yields the full list size and the non-throwing elements still receive their reconciled `isFavorite` flag. If the reconcile logic is not unit-testable in isolation without heavy ViewModel setup, extract the per-element reconcile into a private/internal helper function on the loader first, then test that helper. Keep the test single-class and self-contained.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoaderReconcileTest.kt` exists.
- `Grep` - `@Test` matches at least once in the new test file.
- Value - the new test class passes via `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*PlayerMediaFilesLoaderReconcileTest*"` (read the per-class XML report, not the whole-suite result). `expected: PASS | actual: <fill at run>`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. Test file exists; @Test expected >=1 | actual 1. Reconcile extracted to internal `reconcileFavoriteFlags` with injectable `copyFavorite`/`onFailure` for testability. Ran `:app_v2:testStandardDebugUnitTest --tests *PlayerMediaFilesLoaderReconcileTest*` -> BUILD SUCCESSFUL; XML report tests=1 skipped=0 failures=0 errors=0. expected PASS | actual PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `compileStandardDebugKotlin` succeeded during the test task (BUILD SUCCESSFUL).
- [x] `Grep` for `TODO(phase-03)` returns zero hits. (expected 0 | actual 0)
- [x] The new `PlayerMediaFilesLoaderReconcileTest` passes (XML: tests=1 failures=0 errors=0).
- [x] Dev log entry added for every file in "Files Touched" (batched in Phase 05 closure).

---

## Handoff Notes to Next Phase

The player reconcile is now resilient to a single corrupted element. The `Timber` line emitted on a per-element reconcile failure still needs its level decided - Phase 04 sets it (and the level of the existing outer "Failed to reconcile favorites" log) per the Phase 01 finding on whether the failure is always a data defect.

---

## Rollback Plan

Revert phase commit - no data migration or user-facing surface changed. The Phase 02 upstream guard remains as the primary protection.

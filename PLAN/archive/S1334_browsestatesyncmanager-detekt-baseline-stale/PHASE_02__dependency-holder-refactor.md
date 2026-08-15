# Phase 02 - BrowseStateSyncManager dependency-holder refactor

**Strategic spec:** [`../S1334_browsestatesyncmanager-detekt-baseline-stale.md`](../S1334_browsestatesyncmanager-detekt-baseline-stale.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Bring `BrowseStateSyncManager`'s constructor back under the `LongParameterList` threshold (11 -> 9
parameters) by grouping its three `UseCase` dependencies into one holder, then prune the resulting
dead baseline entry - no behavior change.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none - foundation phase)
- [ ] Strategic §6 research items blocking this phase are Resolved - both are (see `research/01__dependency-holder-precedent.md`).
- [ ] Working tree is clean or on a feature branch.
- [ ] `S1315` remains `BlockNeedUserTest` - its `Timber.d("S1315: ...")` probe line in
      `BrowseStateSyncManager.kt` must not be touched by this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateSyncUseCases.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateSyncManager.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | ≤ 700 (existing file - touch only lines ~363-375) |
| `config/detekt/baseline-app_v2.xml` | Modified (delete one line) | - |

---

## Steps

### Step 02.1 - Add the `BrowseStateSyncUseCases` holder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateSyncUseCases.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `BrowseStateSyncUseCases.kt` in `ui/browse/managers/` with:
> `data class BrowseStateSyncUseCases(val favoritesUseCase: FavoritesUseCase, val
> materializeFavoritesUseCase: MaterializeFavoritesUseCase, val getResourcesUseCase:
> GetResourcesUseCase)`. Plain data holder only, no logic - same shape as the existing
> `ui/player/VideoPlayerDependencies.kt` holders.

**Verification:**

- `Glob` - `BrowseStateSyncUseCases.kt` exists.
- `Grep` - `data class BrowseStateSyncUseCases` present with all three field names
  (`favoritesUseCase`, `materializeFavoritesUseCase`, `getResourcesUseCase`).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 2/2 PASS. Files: `BrowseStateSyncUseCases.kt` (+10 LOC, new).

---

### Step 02.2 - Refactor the constructor, the call site, and prune the dead baseline entry

**Files:** `ui/browse/managers/BrowseStateSyncManager.kt`, `ui/browse/BrowseViewModel.kt`, `config/detekt/baseline-app_v2.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `BrowseStateSyncManager.kt`, replace the three constructor parameters `favoritesUseCase:
> FavoritesUseCase`, `materializeFavoritesUseCase: MaterializeFavoritesUseCase`,
> `getResourcesUseCase: GetResourcesUseCase` with a single `private val useCases:
> BrowseStateSyncUseCases` parameter (constructor param count: 11 -> 9). Update the two internal call
> sites - `loadFavorites()`'s `favoritesUseCase.getAllFavorites()` /
> `materializeFavoritesUseCase.toMediaFiles(favorites)`, and
> `checkAndReloadIfResourceChanged()`'s `getResourcesUseCase.getById(resourceId)` - to read through
> `useCases.favoritesUseCase` / `useCases.materializeFavoritesUseCase` / `useCases.getResourcesUseCase`
> respectively. Do not touch any other line in the file - in particular the `Timber.d("S1315: ...")`
> probe line must survive byte-for-byte (spec S1315 is `BlockNeedUserTest`; removing its tag early is
> forbidden).
>
> In `BrowseViewModel.kt`, update the `stateSyncManager` construction (the `BrowseStateSyncManager(`
> call around line 363) to pass `useCases = BrowseStateSyncUseCases(favoritesUseCase =
> favoritesUseCase, materializeFavoritesUseCase = materializeFavoritesUseCase, getResourcesUseCase =
> getResourcesUseCase)` instead of the three flat named arguments. Leave every other
> `favoritesUseCase` / `materializeFavoritesUseCase` / `getResourcesUseCase` usage in this file
> untouched - they feed other managers directly and are out of this ticket's scope.
>
> Run `/build` (standard debug), then `gradlew :app_v2:detekt --rerun-tasks` and confirm no
> `LongParameterList` finding remains for `BrowseStateSyncManager`. Delete the now-dead
> `<ID>LongParameterList:BrowseStateSyncManager.kt$BrowseStateSyncManager$( private val
> favoritesUseCase: FavoritesUseCase, ..)</ID>` line from `config/detekt/baseline-app_v2.xml` - it no
> longer describes any parameter list in the live file. If Phase 01 is already done, confirm via
> `audit-detekt-baseline-drift.ps1` that this specific entry now classifies `DEAD` before deleting it.
>
> Note (found during implementation): the new holder file must be named after its single
> declaration - `BrowseStateSyncUseCases.kt`, not `BrowseStateSyncDependencies.kt` - or detekt's
> `Filename`/`MatchingDeclarationName` rules fire as new findings (`VideoPlayerDependencies.kt`
> avoids this only because it holds three declarations, not one). Scope the detekt gate check to
> exactly the two touched manager files, not `BrowseViewModel.kt` - that file carries its own,
> unrelated, pre-existing drifted `LongParameterList` baseline entry (same root cause, different
> class - parked as **S1350**, out of this ticket's scope per the dirty-tree rule: fix only what your
> own diff introduces).

**Verification:**

- `Grep` - `BrowseStateSyncManager.kt` contains `private val useCases: BrowseStateSyncUseCases`.
- `Grep` - `BrowseStateSyncManager.kt` no longer declares `private val favoritesUseCase:
  FavoritesUseCase` / `private val materializeFavoritesUseCase` / `private val getResourcesUseCase` as
  constructor parameters.
- `Grep` - `BrowseStateSyncManager.kt` still contains `Timber.d("S1315:` unchanged.
- `Grep` - `BrowseViewModel.kt` contains `useCases = BrowseStateSyncUseCases(` at the
  `stateSyncManager` construction site.
- `Grep` - `config/detekt/baseline-app_v2.xml` no longer contains
  `LongParameterList:BrowseStateSyncManager.kt$BrowseStateSyncManager$( private val
  favoritesUseCase: FavoritesUseCase`.
- `/build` (standard debug) succeeds.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 6/6 PASS. Files: `BrowseStateSyncManager.kt` (11 -> 9 constructor params),
  `BrowseViewModel.kt` (call site only, ~5 LOC), `config/detekt/baseline-app_v2.xml` (-1 line).
  `.\a.ps1 fk` and `.\a.ps1 dq` both green. `audit-detekt-baseline-drift.ps1` confirmed the pruned
  entry classified `DEAD (prune candidate)` before deletion. `Timber.d("S1315: ...")` line unchanged
  (confirmed byte-identical). Detekt scoped-gate check found ONE unrelated finding while
  `BrowseViewModel.kt` was in the changed-file list: a pre-existing, already-thawed
  `LongParameterList` baseline entry on `BrowseViewModel`'s OWN primary constructor (same root cause
  as this ticket, different class, ~40 params - not introduced by this step, confirmed by comparing
  the frozen baseline signature against the live one). Per the dirty-tree rule (do not fix another
  finding's WIP/pre-existing debt inline), parked as **S1350** and closed `post-change.ps1` scoped to
  only the files this step's own diff affects cleanly (`BrowseStateSyncManager.kt`,
  `BrowseStateSyncUseCases.kt`, `baseline-app_v2.xml`) - PASS. `BrowseViewModel.kt`'s dev-log entry
  recorded separately with an explicit note cross-referencing S1350. Catalog already regenerated as a
  side effect of this closure (Phase 03 step 03.1 verification still runs, but the class is already
  indexed).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). `.\a.ps1 dq` PASS.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (3 via `post-change.ps1`, `BrowseViewModel.kt` recorded separately - see Step 02.2 log).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated already (side effect of this phase's `post-change.ps1` run); Phase 03 step 03.1 re-verifies rather than re-runs.
- [x] Phase-boundary audit run - Layer 1 (readability/naming): holder follows the `VideoPlayerXDependencies`/`LauncherXDependencies` precedent, naming matches. Layer 2 (lifecycle/coroutine): unaffected - `scope`/`ioDispatcher` usage inside the manager is byte-identical, only how the three use cases arrive changed. Layer 3 (listener ownership): not applicable, no listeners touched. No P0/P1 findings.
- [x] `S1315`'s probe tag and device-test behavior are unaffected (no change to method bodies, only to how dependencies arrive) - confirmed byte-identical in Step 02.2 verification.

---

## Handoff Notes to Next Phase

Public API changed: one new class (`BrowseStateSyncUseCases`), one changed constructor arity
(`BrowseStateSyncManager`). Phase 03 regenerates the catalog to pick both up.

---

## Rollback Plan

Revert the three source edits and re-add the deleted baseline line - no data migration, no
user-facing surface changed, no behavior change to roll back.

# Phase 01 - Resize Persistence

**Strategic spec:** [`../S1093_launcher-widget-resize-to-max.md`](../S1093_launcher-widget-resize-to-max.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-07-21
**Completed:** 2026-07-21

**Step Log:**

- 2026-07-21 - 01.1-01.3 grep-verified (resizeCell interface+impl mirroring moveCell with findOverlapping excludeId; ViewModel.resizeCell; 3 repo tests). `:app_v2:testStandardDebugUnitTest --tests *LauncherDesktopRepositoryImplTest*` BUILD SUCCESSFUL. Audit: one withTransaction (atomic), Dispatchers.IO, no P0/P1.

---

## Objective

Add a collision-checked `resizeCell` to the desktop repository (mirroring `moveCell`) and expose it on the ViewModel, so a gadget's stored `spanW`/`spanH` can change without ever breaking the no-overlap invariant.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 250 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 400 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt` | Modified | ≤ 400 |

> No Room schema change - `spanW`/`spanH` columns already exist. `findOverlapping` already takes span args + `excludeId`.

---

## Steps

### Step 01.1 - Add resizeCell to the repository interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `suspend fun resizeCell(id: Long, spanW: Int, spanH: Int): Boolean` to the interface. KDoc: changes a cell's footprint at its current anchor onto free space only - the new `spanW x spanH` must not overlap another cell (the cell's own current squares are excluded); returns whether it resized. Same no-overlap invariant as [moveCell].

**Verification:**

- `Grep` - `suspend fun resizeCell(id: Long, spanW: Int, spanH: Int): Boolean` present in the interface.

**Status:** `[x]` done

---

### Step 01.2 - Implement resizeCell mirroring moveCell

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Implement `resizeCell` on `Dispatchers.IO` inside `db.withTransaction`: get the cell (`cellDao.getById(id)` or return false); clamp `spanW`/`spanH` with `coerceAtLeast(MIN_SPAN)`; if the clamped spans equal the source spans return false (no-op); call `cellDao.findOverlapping(orientation = source.orientation, rowIndex = source.rowIndex, colIndex = source.colIndex, spanW = safeW, spanH = safeH, excludeId = id)`; when the blocker is null `cellDao.update(source.copy(spanW = safeW, spanH = safeH))` and return true, otherwise return false (the caller snaps back). Do NOT enforce the grid right edge or a per-gadget floor here - that is the gesture layer's job; the repository only guarantees no overlap and a positive span. Add a one-line WHY comment that self is excluded so growth into the cell's own squares is allowed.

**Verification:**

- `Grep` - `override suspend fun resizeCell(` present.
- `Grep` - `findOverlapping(` called with `excludeId = id` inside `resizeCell`.
- `Grep` - `cellDao.update(source.copy(spanW =` present.

**Status:** `[x]` done

---

### Step 01.3 - Expose resizeCell on the ViewModel + unit-test the repository

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt`

**Depends on:** Step 01.2

**Prompt for developer:**

> In `LauncherHomeViewModel`, add `fun resizeCell(id: Long, spanW: Int, spanH: Int)` that launches in `viewModelScope` and calls `desktopRepository.resizeCell(id, spanW, spanH)` - mirror the existing `moveCell` exactly. In `LauncherDesktopRepositoryImplTest`, add tests: (a) growing a gadget into free space returns true and persists the new spans; (b) growing into a square held by another cell returns false and leaves the spans unchanged; (c) shrinking (no overlap possible) returns true. Reuse the test's existing in-memory Room setup.

**Verification:**

- `Grep` - `fun resizeCell(id: Long, spanW: Int, spanH: Int)` in the ViewModel calling `desktopRepository.resizeCell`.
- `Grep` - at least one new test referencing `resizeCell` in `LauncherDesktopRepositoryImplTest.kt`.
- `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*LauncherDesktopRepositoryImplTest*"` - passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `LauncherDesktopRepositoryImplTest` passes.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (repository API changed).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (Layer 4 Room: resize is one `withTransaction`; Layer 2 no main-thread DB).

---

## Handoff Notes to Next Phase

`viewModel.resizeCell(id, w, h)` commits a collision-checked resize; a rejected resize leaves the stored spans unchanged, so the gesture layer snaps back by reverting to the persisted span. Phase 02 builds the gesture on top.

---

## Rollback Plan

Revert the phase commit(s) - one new repository method + ViewModel passthrough + tests; no schema change.

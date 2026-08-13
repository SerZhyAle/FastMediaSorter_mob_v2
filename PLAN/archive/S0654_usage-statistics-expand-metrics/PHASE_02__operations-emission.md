# Phase 02 - Operations Emission

**Strategic spec:** [`../S0654_usage-statistics-expand-metrics.md`](../S0654_usage-statistics-expand-metrics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Wire emission for the two cheapest, highest-value operation behaviors: file rename and favorite toggle. No UI or rendering change.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt` | Modified | ≤ 560 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FavoritesUseCase.kt` | Modified | ≤ 120 |

> `FileOperationUseCase.kt` is near the 500-LOC backup threshold - create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 02.1 - Record rename as a file operation

**Files:** `domain/usecase/FileOperationUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `recordFileOpStats()` remove the early `return` on `FileOperation.Rename` and emit `StatsEvent.FileOp(action = FileOpAction.RENAME, type = <media type>, count = <renamed count>, bytes = 0)` after the rename result is confirmed successful. Reuse the same media-type classification the other actions use. The sink no-ops when opt-in is off, so emit unconditionally.

**Verification:**

- `Grep` - `FileOpAction.RENAME` referenced in `FileOperationUseCase.kt`.
- `Grep` - the previous `is FileOperation.Rename -> return` line is gone.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

### Step 02.2 - Record favorite add/remove

**Files:** `domain/usecase/FavoritesUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink` into `FavoritesUseCase`. In `toggleFavorite()` emit `StatsEvent.Favorite(added = true)` on the add branch and `StatsEvent.Favorite(added = false)` on the remove branch, after the persistence call succeeds. Do not log file uri/path at info level or above.

**Verification:**

- `Grep` - `StatsSink` constructor parameter present in `FavoritesUseCase.kt`.
- `Grep` - `StatsEvent.Favorite` referenced twice (add + remove).
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`FILES_RENAMED`, `FAVORITES_ADDED`, `FAVORITES_REMOVED` now accrue. Rows for them are rendered in Phase 06.

---

## Rollback Plan

Revert phase commit(s) - emission-only, no data migration or user-facing surface changed.

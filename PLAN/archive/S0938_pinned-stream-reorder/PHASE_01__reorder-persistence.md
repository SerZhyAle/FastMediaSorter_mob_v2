# Phase 01 - Reorder Persistence

**Strategic spec:** [`../S0938_pinned-stream-reorder.md`](../S0938_pinned-stream-reorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Add the pinned-order reorder operation end to end at the data + domain layers: a transactional contiguous renumber of the pinned set and a `ReorderPinnedStreamUseCase` fronting it. No UI, no ViewModel wiring yet.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved (see `research/`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ReorderPinnedStreamUseCase.kt` | New | ≤ 70 |

> No Room `@Database` version bump - the reorder reuses the existing `sortIndex` column (research 01). No new Hilt module - the use case is constructor-injected like its siblings (`PinStreamSourceUseCase`).

---

## Steps

### Step 01.1 - Add DAO snapshot + single-row sortIndex write

**Files:** `data/local/db/StreamSourceDao.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two DAO members. A suspend snapshot of the pinned set in display order: `@Query("SELECT * FROM stream_sources WHERE pinned = 1 ORDER BY sortIndex ASC, addedAt DESC") suspend fun pinnedSnapshot(): List<StreamSourceEntity>`. A single-row order write: `@Query("UPDATE stream_sources SET sortIndex = :sortIndex WHERE id = :id") suspend fun setSortIndex(id: String, sortIndex: Int)`. Keep the existing `observePinned()` Flow untouched - the snapshot is the one-shot read the reorder needs.

**Verification:**

- `Grep` - `suspend fun pinnedSnapshot()` matches once in `StreamSourceDao.kt`.
- `Grep` - `suspend fun setSortIndex(` matches once in `StreamSourceDao.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 2/2 PASS. Files: data/local/db/StreamSourceDao.kt (+8 LOC).

---

### Step 01.2 - Add transactional reorder to the repository

**Files:** `data/repository/StreamSourceRepository.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Expose two repository methods. `suspend fun pinnedSnapshot(): List<StreamSourceEntity> = dao.pinnedSnapshot()`. `suspend fun reorderPinned(orderedIds: List<String>)` writing the new order in one `db.withTransaction { orderedIds.forEachIndexed { index, id -> dao.setSortIndex(id, index) } }` so the renumber is all-or-nothing. The repository must NOT own the move math (up/down/top) and must NOT import any `domain.usecase` type - it only persists the id order it is given.

**Verification:**

- `Grep` - `suspend fun reorderPinned(orderedIds: List<String>)` matches once in `StreamSourceRepository.kt`.
- `Grep` - `withTransaction` present inside `reorderPinned` (same file).
- `Grep` - `import com.sza.fastmediasorter.domain.usecase` returns zero hits in `StreamSourceRepository.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS. Files: data/repository/StreamSourceRepository.kt (+15 LOC). No domain.usecase import.

---

### Step 01.3 - Add ReorderPinnedStreamUseCase with the move enum

**Files:** `domain/usecase/streams/ReorderPinnedStreamUseCase.kt` (New)
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `ReorderPinnedStreamUseCase` (`@Inject constructor(private val repository: StreamSourceRepository)`). Declare `enum class PinnedStreamMove { UP, DOWN, TO_TOP }` in the same file. `suspend operator fun invoke(id: String, move: PinnedStreamMove)`: read `repository.pinnedSnapshot()`, find `from = indexOfFirst { it.id == id }`; return early if `from < 0`. Compute `to` = `from - 1` (UP), `from + 1` (DOWN), or `0` (TO_TOP), then `coerceIn(0, lastIndex)`; return early if `to == from`. Rebuild the id order by removing the item at `from` and inserting it at `to`, then call `repository.reorderPinned(newОrderIds)`. Keep it allocation-light and free of Android types.

**Verification:**

- `Glob` - `ReorderPinnedStreamUseCase.kt` exists.
- `Grep` - `class ReorderPinnedStreamUseCase` matches once (declaration).
- `Grep` - `enum class PinnedStreamMove` matches once, with `UP`, `DOWN`, `TO_TOP` present.
- `Grep` - `repository.reorderPinned(` present in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 4/4 PASS. Files: domain/usecase/streams/ReorderPinnedStreamUseCase.kt (New, +33 LOC).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`) (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`ReorderPinnedStreamUseCase(id, PinnedStreamMove)` is the single entry point the ViewModel calls. It operates only within the pinned set and renumbers contiguously, so the panel (`observePinned`) and player prev/next (`observeAll`) reflect the new order with no further change. Phase 02 wires the menu + makes the list/grid pinned block honour this order.

---

## Rollback Plan

Revert the phase commit(s) - no data migration and no user-facing surface changed (the new column write reuses the existing `sortIndex`).

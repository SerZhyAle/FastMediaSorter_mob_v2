# Phase 01 - Free slot placement

**Strategic spec:** [`../S1170_launcher-desktop-app-widgets.md`](../S1170_launcher-desktop-app-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## Objective

Give `LauncherDesktopRepository` a way to place a cell in the first free slot, so a widget added from Settings lands on the desktop without the caller knowing a row or column.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 260 |

---

## Steps

### Step 01.1 - Declare the free-slot placement API

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `suspend fun addCellInFirstFreeSlot(cell: LauncherCell, columns: Int): Long?` next to `addCell`. KDoc must state the contract the owner chose (strategic §4 item 2): scan row-major from the top, take the first anchor whose whole span fits without overlapping, and when no row has room append a new row below the last occupied one. `columns` is a parameter rather than repository state because the column count belongs to the current screen, not to the stored desktop - the same reason `LauncherDesktopRepositoryImpl` gives for not enforcing the right edge today. The return stays nullable to match `addCell`, but null now means "could not place at all", not "the requested anchor was taken".

**Verification:**

- `Grep` - `suspend fun addCellInFirstFreeSlot(` present in the interface.
- `Grep` - the existing `suspend fun addCell(cell: LauncherCell): Long?` is unchanged.

**Status:** `[x]` done

---

### Step 01.2 - Implement the row-major scan

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Implement `addCellInFirstFreeSlot` inside one `db.withTransaction` so the scan and the insert cannot race another placement. Clamp `spanW` to `1..columns` first, then walk `row = 0` upward and `col = 0..columns - spanW`, asking the existing `cellDao.findOverlapping(orientation, row, col, spanW, spanH, excludeId)` for each anchor; the first anchor with no blocker wins. Bound the upward walk by the last occupied row plus the candidate's own height so an empty desktop terminates immediately and a full one appends exactly one new row. Reuse the normalisation `addCell` already performs rather than repeating it - extract it if that is what it takes. Log the chosen anchor at `Timber.i` in the same style as `addCell`'s existing rejection line, with no ticket id in the message (CLAUDE.md Rule: permanent logs carry no `Sxxxx`).

**Verification:**

- `Grep` - `override suspend fun addCellInFirstFreeSlot(` present.
- `Grep` - `findOverlapping(` appears in the new function.
- `Grep` - `db.withTransaction` wraps it.
- `Grep` - zero `S1170` occurrences in any `Timber.i`/`Timber.w`/`Timber.e` call in the file.
- `.\a.ps1 fu` - existing launcher repository unit tests still pass (constructor and existing signatures untouched).

**Status:** `[x]` done

**Result (2026-07-30).** The scan needed one upper bound the plan left to the implementer, so `LauncherCellDao` gained a read-only `firstRowBelowAll(orientation)` query (`MAX(rowIndex + spanH)`) - a `@Query` addition, no schema change and no migration. That row overlaps nothing by construction, which is what makes "append a new row below the last occupied one" fall out of the same loop instead of needing a second code path. Seven tests added; `expected: suite passes | actual: 21 tests, 0 failures, 0 errors` - PASS.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode Unit` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the ticket - deferred to ticket closure (one entry per ticket, not per phase).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The scan and the insert share one `db.withTransaction`, every DAO call is `suspend`, and the bound is a single indexed aggregate rather than a full-table read per probe.

---

## Handoff Notes to Next Phase

`addCellInFirstFreeSlot` is the only placement path Phase 06 may use. Callers must not re-implement a scan.

---

## Rollback Plan

Revert the phase commit - the new function has no callers until Phase 06 and no schema changed.

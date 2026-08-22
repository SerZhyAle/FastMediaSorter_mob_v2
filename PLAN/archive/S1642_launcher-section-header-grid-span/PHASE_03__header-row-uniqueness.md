# Phase 03 - Refuse a second header on a row

**Strategic spec:** [`../S1642_launcher-section-header-grid-span.md`](../S1642_launcher-section-header-grid-span.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Close the one write path that could seat a narrowed header away from column 0, so "a row carries at most
one header" keeps falling out of the overlap check the desktop already runs.

**Plan correction, 2026-08-15 (found while implementing).** This phase was planned as a `headerRowTaken`
predicate called from `addCell`, `moveCell` and `findFreeAnchor`. Two of those three calls are unreachable:
`normalized()` pins every header to column 0 on both paths, so a second header on an occupied row always
overlaps the first and `findOverlapping` already refuses it. `addCellInFirstFreeSlot` is the sole gap - it
writes the anchor its scan found rather than the normalized column, and once phase 05 narrows the header to
two columns that scan can return column 2 or beyond. Restoring the column pin inside the scan closes the gap
and makes the predicate unnecessary, so the phase ships one line instead of three guards, none of them dead.

Checked against S1645 (`launcher-collapsed-section-packing`) before settling on this: that ticket packs
collapsed headers into one *drawn* row without rewriting stored coordinates (its §2.6), so one header per
*stored* row is the invariant it builds on rather than one this phase would take away from it.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 440 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt` | Modified | ≤ 380 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 03.1 - Pin a header to column 0 in the free-slot scan

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `findFreeAnchor`, bound the column loop by a `lastCol` that is 0 for a `SECTION` candidate and `columns - scanSpanW` for every other kind. Declare it after `scanSpanW`, which it reads. Record why in a comment: this scan writes the anchor it found rather than the normalized column, so it is the one write path the header's column pin does not already reach, and probing column 0 alone is what keeps a second header off an occupied row.

**Why:**

Membership is positional and keyed on the row alone, so two headers sharing a row would give both sections
the same boundary - the failure strategic §7 rates as a collapse that captures a neighbouring section.

**Verification:**

- `Grep` - `val lastCol = if (candidate.kind == LauncherCellKind.SECTION) 0` matches exactly once in that file.
- `Grep` - `for (col in 0..lastCol)` matches exactly once in that file.
- `Grep` - `val scanSpanW` is declared on an earlier line than `val lastCol`.
- `Grep -n "Log\.d\("` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - findFreeAnchor bounds the column loop by lastCol, 0 for a SECTION candidate - the free-slot scan was the one write path bypassing the header column pin. Plan corrected: the headerRowTaken predicate planned for addCell/moveCell is unreachable there, so it was dropped rather than shipped dead. Three repository cases added. check-standard-fast -Mode Unit -Tests '*LauncherDesktopRepositoryImplTest' exit 0.

---

### Step 03.2 - Cover the refusal in repository tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add three cases against the in-memory database already used by the file: `addCellInFirstFreeSlot` for a header never returns a non-zero column, a second header added to a row that already carries one is refused, and `addCellInFirstFreeSlot` for a shortcut still returns a non-zero column so content can sit beside a header.

**Why:**

The third case is the one that must not regress: strategic §2.2 exists to let shortcuts share the header's
row, so a rule written to keep headers apart must not also keep content out.

**Verification:**

- `Grep` - the three new test names are present in the file.
- Run `.\a.ps1 fu` filtered to `LauncherDesktopRepositoryImplTest` - all cases pass.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - findFreeAnchor bounds the column loop by lastCol, 0 for a SECTION candidate - the free-slot scan was the one write path bypassing the header column pin. Plan corrected: the headerRowTaken predicate planned for addCell/moveCell is unreachable there, so it was dropped rather than shipped dead. Three repository cases added. check-standard-fast -Mode Unit -Tests '*LauncherDesktopRepositoryImplTest' exit 0.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every write path now stores a header at column 0, the free-slot scan included, so "one header per row"
follows from the overlap check rather than from a rule of its own. The pin is inert while a header fills its
row and becomes load-bearing the moment phase 05 narrows it.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.

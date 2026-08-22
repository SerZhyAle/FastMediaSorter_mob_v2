# Phase 02 - Anchor a seeded header on a free row

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

Make `LauncherStarterSets.place` anchor every section header at column 0 of a row no cell occupies, so a
narrow header can never be seeded into a row a taller gadget already passes through.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 440 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt` | Modified | ≤ 540 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 02.1 - Seat a section header on the first wholly free row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `place`, branch on the item kind before calling `firstFreeAnchor`: a `SECTION` item takes column 0 of the first row at or below `sectionFloor` in which the occupancy set holds no square at all, while every other kind keeps the current row-major anchor search. Raise `sectionFloor` to that row as now. Record in the KDoc that a header opens a fresh row so nothing spanning several rows can cross it.

**Why:**

Strategic §6.11 as carried into §5.1 forbids a cell taller than one row from covering a header row, and
once the header stops filling its row the seeded clock gadget - two rows tall, placed immediately after the
first header - would otherwise be free to sit in the row the second header lands on.

**Verification:**

- `Grep` - `LauncherCellKind.SECTION` appears inside `fun place(`.
- `Grep` - `sectionFloor` still assigned inside `fun place(`.
- `Grep` - `HEADER_STORED_SPAN_W` in `private fun section(` unchanged - this phase changes no span.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - place() seats a SECTION on the first wholly empty row at or below the section floor via firstEmptyRow; test asserts no cell straddles a header row and every earlier item ends above it. check-standard-fast -Mode Unit -Tests '*LauncherStarterSetsTest' exit 0.

---

### Step 02.2 - Assert the seeded header row is exclusive

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a case that packs an item list on eight columns - header, two-row gadget, header, shortcut - and asserts two things about every placed `SECTION` item: no placed cell strictly straddles its row, and every item ahead of it in the list ends above that row. Items placed *after* a header may share its row, which is the whole point of the geometry, so do not assert the row is empty.

**Why:**

Strategic §11.5 requires that no orientation or mode ends up with overlaps or false free squares, and the
seeded desktop is the one arrangement no user action produced and therefore no manual check covers. The
second assertion is what proves the header found an empty row: strategic §6.11 forbids a cell taller than
one row from covering a header row, and a header dropped into an occupied row is how that happens.

**Verification:**

- `Grep` - the new test name is present in the file.
- Run `.\a.ps1 fu` filtered to `LauncherStarterSetsTest` - all cases pass.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - place() seats a SECTION on the first wholly empty row at or below the section floor via firstEmptyRow; test asserts no cell straddles a header row and every earlier item ends above it. check-standard-fast -Mode Unit -Tests '*LauncherStarterSetsTest' exit 0.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

A seeded header owns a row nothing else reaches into. With the header still full-width the rule changes no
seeded desktop; it becomes load-bearing the moment phase 05 narrows the header.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed. Already-seeded desktops are not
re-seeded, so no device state depends on this phase.

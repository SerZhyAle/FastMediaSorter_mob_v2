# Phase 05 - Narrow the header to two cells

**Strategic spec:** [`../S1642_launcher-section-header-grid-span.md`](../S1642_launcher-section-header-grid-span.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Store and draw every section header at two columns by one row, and narrow the headers already sitting on
test desktops so no square is free in the table while covered on screen.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Placement decision recorded: strategic §9 ADR-1 and §6.1 - owner ruling of 2026-08-15, 2x1 in both orientations.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionMembership.kt` | Modified | ≤ 180 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 460 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherCellEntity.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 440 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 960 |
| `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometryTest.kt` | Modified | ≤ 320 |
| `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt` | Modified | ≤ 100 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt` | Modified | ≤ 540 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt` | Modified | ≤ 400 |

> `LauncherHomeActivity.kt` is 951 LOC - step 05.1 takes a timestamped backup under `temp/S1642/` before
> touching it, per CLAUDE.md Rule 5.

---

## Steps

### Step 05.1 - Set the header span to two and rename the constant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionMembership.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `LauncherHomeActivity.kt` to `temp/S1642/` with a timestamp first. Then change `HEADER_STORED_SPAN_W = 12` to `HEADER_SPAN_W = 2` and update every reference in one edit: `normalized()` in the repository, `section()` in the starter sets, and `onSectionChosen` in `LauncherHomeActivity`, which currently passes `LauncherGridGeometry.MAX_COLUMNS` and must pass the constant instead. Rewrite the constant's KDoc: the stored span and the drawn span are now the same number, so the paragraph explaining why a header is stored wider than it is packed no longer describes anything. Rewrite `normalized()`'s KDoc paragraph on the same point, keeping the column-0 anchor and stating that a header opens its row.

**Why:**

Strategic §9 ADR-1 decides that a header occupies two horizontal positions and one vertical one, and the
constant is the single value every layer reads that geometry from.

**Verification:**

- `Grep` - `HEADER_SPAN_W = 2` matches exactly once across `app_v2/src`.
- `Grep` - `HEADER_STORED_SPAN_W` returns zero hits across `app_v2/src`.
- `Grep` - `LauncherGridGeometry.MAX_COLUMNS` returns zero hits in `LauncherHomeActivity.kt`.
- `Glob` - a timestamped copy of `LauncherHomeActivity.kt` exists under `temp/S1642/`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - HEADER_SPAN_W = 2 replaces HEADER_STORED_SPAN_W across model, repository, starter sets and LauncherHomeActivity (backup temp/S1642/LauncherHomeActivity.20260815-020000.kt); renderSpanW normalizes a header to that span; narrowSectionSpans + normalizeSectionSpans narrow desktops written by an S1428 build, called ahead of the seeded early-exit, no @Database bump; PlacedStarterItem.storedSpanW dropped. check-standard-fast -Mode Unit -Tests '*Launcher*Test' exit 0, 170 tests. One prior expectation corrected: a two-row gadget may now start on a header's own row - both rows belong to that section, so it is not a straddle.

---

### Step 05.2 - Draw the header at its own span

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Change `renderSpanW` so a `SECTION` cell reports `LauncherSectionMembership.HEADER_SPAN_W` clamped to the live column count instead of the column count itself. Rewrite its KDoc: the function no longer widens a header, it normalises one - a header persisted at the old full-row span still draws two cells wide, which is what keeps the renderer and the empty-square sweep agreed on a desktop written by an earlier build.

**Why:**

Strategic §2.5 requires display, editing and the free-square search to read one geometry, and this helper is
the one both the layout pass and the empty-slot sweep already call.

**Verification:**

- `Grep` - `HEADER_SPAN_W` present in `renderSpanW`.
- `Grep` - `columns.coerceAtLeast(1) else cell.spanW` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - HEADER_SPAN_W = 2 replaces HEADER_STORED_SPAN_W across model, repository, starter sets and LauncherHomeActivity (backup temp/S1642/LauncherHomeActivity.20260815-020000.kt); renderSpanW normalizes a header to that span; narrowSectionSpans + normalizeSectionSpans narrow desktops written by an S1428 build, called ahead of the seeded early-exit, no @Database bump; PlacedStarterItem.storedSpanW dropped. check-standard-fast -Mode Unit -Tests '*Launcher*Test' exit 0, 170 tests. One prior expectation corrected: a two-row gadget may now start on a header's own row - both rows belong to that section, so it is not a straddle.

---

### Step 05.3 - Narrow the headers already on disk

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherCellEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add a `@Query` to `LauncherCellDao` that sets `spanW` to a passed value for every row whose `kind` matches the passed section kind and whose `spanW` differs from it, taking both as parameters so the enum name and the constant each keep one home in Kotlin. Expose `normalizeSectionSpans()` on `LauncherDesktopRepository`, implement it in the repository on `Dispatchers.IO` over that query, and call it in `SeedLauncherDesktopUseCase` inside the existing `runCatching` block **before** the `seededPortrait && seededLandscape` early return, so a desktop that will not be re-seeded is still normalised. This adds no column and no entity field: do not bump the `@Database` version and do not write a `Migration`.

**Why:**

Strategic §6.3 rules that the 2x1 geometry applies to every desktop at once including those created while
testing, with no migration written, and §11.6 requires that compatibility pass to lose no shortcut -
narrowing a header only frees squares, as §6.3's confirming observation records.

**Verification:**

- `Grep` - `fun normalizeSectionSpans` matches once in the interface and once in the implementation.
- `Grep` - `normalizeSectionSpans()` called in `SeedLauncherDesktopUseCase.kt`.
- `Grep` - `@Database(` version literal in `AppDatabase.kt` is unchanged from its pre-phase value.
- `Grep -n "Log\.d\("` returns zero hits in every file this step touched.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - HEADER_SPAN_W = 2 replaces HEADER_STORED_SPAN_W across model, repository, starter sets and LauncherHomeActivity (backup temp/S1642/LauncherHomeActivity.20260815-020000.kt); renderSpanW normalizes a header to that span; narrowSectionSpans + normalizeSectionSpans narrow desktops written by an S1428 build, called ahead of the seeded early-exit, no @Database bump; PlacedStarterItem.storedSpanW dropped. check-standard-fast -Mode Unit -Tests '*Launcher*Test' exit 0, 170 tests. One prior expectation corrected: a two-row gadget may now start on a header's own row - both rows belong to that section, so it is not a straddle.

---

### Step 05.4 - Drop the now-identical seeded span

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Delete `PlacedStarterItem.storedSpanW` and have `SeedLauncherDesktopUseCase.seedOrientation` read `placed.spanW`, removing the comment that explains why the two differ. The packer clamps a span to the grid width and the narrowest grid is `LauncherGridGeometry.MIN_COLUMNS`, which is wider than two, so the clamp can no longer change a header's span.

**Why:**

CLAUDE.md Rule 20 requires dead weight to go in the same change that makes it dead, and a property whose
two branches now return the same value invites a reader to believe a distinction still exists.

**Verification:**

- `Grep` - `storedSpanW` returns zero hits across `app_v2/src`.
- `Grep` - `spanW = placed.spanW` present in `SeedLauncherDesktopUseCase.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - HEADER_SPAN_W = 2 replaces HEADER_STORED_SPAN_W across model, repository, starter sets and LauncherHomeActivity (backup temp/S1642/LauncherHomeActivity.20260815-020000.kt); renderSpanW normalizes a header to that span; narrowSectionSpans + normalizeSectionSpans narrow desktops written by an S1428 build, called ahead of the seeded early-exit, no @Database bump; PlacedStarterItem.storedSpanW dropped. check-standard-fast -Mode Unit -Tests '*Launcher*Test' exit 0, 170 tests. One prior expectation corrected: a two-row gadget may now start on a header's own row - both rows belong to that section, so it is not a straddle.

---

### Step 05.5 - Re-point the span assertions

**Files:** `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometryTest.kt`, `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Update `LauncherGridGeometryTest` so a header on an eight-column grid reports a render span of two and a header stored at the old twelve still reports two. Replace the parity assertion tying the header span to `MAX_COLUMNS` with one asserting the constant is two and no wider than `MIN_COLUMNS`. Fix the `LauncherStarterSetsTest` cases that read `storedSpanW`, and add a `LauncherDesktopRepositoryImplTest` case proving `normalizeSectionSpans` rewrites a header stored at twelve to two while leaving every shortcut's row, column and span untouched.

**Why:**

Strategic §11.6 requires the compatibility rule to run on existing desktops without losing shortcuts, and
the normalisation write is the only step in this ticket that touches persisted user data.

**Verification:**

- `Grep` - `MAX_COLUMNS, LauncherSectionMembership` returns zero hits in `LauncherStarterSetsParityTest.kt`.
- Run `.\a.ps1 fu` - `LauncherGridGeometryTest`, `LauncherStarterSetsParityTest`, `LauncherStarterSetsTest`, `LauncherDesktopRepositoryImplTest`, `LauncherSectionCollapseTest` and `LauncherSectionMembershipTest` all pass.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - HEADER_SPAN_W = 2 replaces HEADER_STORED_SPAN_W across model, repository, starter sets and LauncherHomeActivity (backup temp/S1642/LauncherHomeActivity.20260815-020000.kt); renderSpanW normalizes a header to that span; narrowSectionSpans + normalizeSectionSpans narrow desktops written by an S1428 build, called ahead of the seeded early-exit, no @Database bump; PlacedStarterItem.storedSpanW dropped. check-standard-fast -Mode Unit -Tests '*Launcher*Test' exit 0, 170 tests. One prior expectation corrected: a two-row gadget may now start on a header's own row - both rows belong to that section, so it is not a straddle.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The header is 2x1 stored, drawn and packed, in both orientations and in edit mode alike. Every strategic
readiness criterion except the on-device checks of §11.1-§11.5 is now expressible as a passing unit test.

---

## Rollback Plan

Revert phase commit(s). The one persisted effect is `normalizeSectionSpans`, which narrows section rows;
reverting the code leaves those rows at two while the renderer widens them again, which frees no square and
loses no cell - the pre-S1642 renderer draws a header across its whole row whatever span it was stored with.

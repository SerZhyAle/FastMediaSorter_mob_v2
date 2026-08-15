# Phase 01 - Storage clear

**Strategic spec:** [`../S1400_reset-system-launcher-settings.md`](../S1400_reset-system-launcher-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Give every store that holds launcher-owned state a wipe-everything entry point: five delete-all DAO queries and one clearing method per owning repository. No caller yet.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired for the multi-file source edit (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherCellEntity.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherStateEntity.kt` | Modified | ≤ 45 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherPinEntity.kt` | Modified | ≤ 45 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherJournalEntity.kt` | Modified | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherLaunchStatsEntity.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherPinsRepository.kt` | Modified | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherPinsRepositoryImpl.kt` | Modified | ≤ 45 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherJournalRepository.kt` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherJournalRepositoryImpl.kt` | Modified | ≤ 55 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/InstalledAppsRepository.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/InstalledAppsRepositoryImpl.kt` | Modified | ≤ 95 |

> No file in this phase is over 500 LOC, so no backup sub-step is required.
>
> No flavor-specific placement applies: every file above is in `src/main`, and the launcher surface is gated at run time by the availability contract, not by a source set.

---

## Steps

### Step 01.1 - Add delete-all queries to the five launcher DAOs

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherCellEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherStateEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherPinEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherJournalEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherLaunchStatsEntity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one `@Query("DELETE FROM <table>") suspend fun deleteAll()` to each of `LauncherCellDao`, `LauncherStateDao`, `LauncherPinDao`, `LauncherJournalDao` and `LauncherLaunchStatsDao`, using each DAO's own table name (`launcher_cells`, `launcher_state`, `launcher_pins`, `launcher_journal`, `launcher_launch_stats`). Change nothing else: no entity field, no `@Database` version, no migration.

**Why:**

Strategic §4 records that every launcher store deletes only one row at a time - a cell by id, a pin by position, the journal trimmed to its newest rows - so there is currently no way to empty them, and ADR-3 fixes the answer as new delete queries rather than a schema change.

**Verification:**

- `Grep` - `DELETE FROM launcher_cells"` matches once in `LauncherCellEntity.kt`.
- `Grep` - `DELETE FROM launcher_state"` matches once in `LauncherStateEntity.kt`.
- `Grep` - `DELETE FROM launcher_pins"` matches once in `LauncherPinEntity.kt`.
- `Grep` - `DELETE FROM launcher_journal"` matches once in `LauncherJournalEntity.kt`.
- `Grep` - `DELETE FROM launcher_launch_stats"` matches once in `LauncherLaunchStatsEntity.kt`.
- `Grep` - `version = ` in `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` is unchanged from HEAD.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 6\6 PASS. Files: data/local/db/LauncherCellEntity.kt, LauncherStateEntity.kt, LauncherPinEntity.kt, LauncherJournalEntity.kt, LauncherLaunchStatsEntity.kt (+3 LOC each). `AppDatabase` still `version = 46`.

---

### Step 01.2 - Add `clearAll` to the launcher desktop repository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Declare `suspend fun clearAll()` on `LauncherDesktopRepository` and implement it in `LauncherDesktopRepositoryImpl` on `Dispatchers.IO` inside a single `db.withTransaction`, deleting every cell of both orientations and then the `launcher_state` row. Deleting the state row is what clears the seeded flags and the stored column widths together; do not write a replacement row. Document on the interface method that this drops both orientations at once and that a later `seedIfEmpty` will therefore seed again.

**Why:**

Strategic §5 makes clearing the seeded flag the mechanism that lets the one-time starter-set seed run a second time, and doing the cell delete and the flag reset in one transaction is what stops a crash between them from leaving a desktop that is empty and still marked as seeded.

**Verification:**

- `Grep` - `suspend fun clearAll()` matches once in `LauncherDesktopRepository.kt`.
- `Grep` - `override suspend fun clearAll()` matches once in `LauncherDesktopRepositoryImpl.kt`.
- `Grep` - `db.withTransaction` appears inside the `clearAll` body in `LauncherDesktopRepositoryImpl.kt`.
- `Grep` - `Log\.d\(` returns zero hits in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4\4 PASS. Files: domain/repository/LauncherDesktopRepository.kt (+7 LOC), data/repository/LauncherDesktopRepositoryImpl.kt (+11 LOC). Cell delete and state-row delete share one `db.withTransaction`.

---

### Step 01.3 - Add clearing methods to the pins and journal repositories

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherPinsRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherPinsRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherJournalRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherJournalRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Declare `suspend fun clearPins()` on `LauncherPinsRepository` and `suspend fun clearJournal()` on `LauncherJournalRepository`, and implement both by delegating to the matching DAO `deleteAll()` on `Dispatchers.IO`, following the dispatcher handling each impl already uses for its writes.

**Why:**

Strategic §2 goal 3 states that after the reset the pinned icons and the launch journal are empty, which is the state a fresh install has, and §5.1 requires each launcher-owned store to expose its own clearing entry point so the reset operation has one place to call per store.

**Verification:**

- `Grep` - `suspend fun clearPins()` matches once in `LauncherPinsRepository.kt` and `override suspend fun clearPins()` once in `LauncherPinsRepositoryImpl.kt`.
- `Grep` - `suspend fun clearJournal()` matches once in `LauncherJournalRepository.kt` and `override suspend fun clearJournal()` once in `LauncherJournalRepositoryImpl.kt`.
- `Grep` - `Log\.d\(` returns zero hits in all four files.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 5\5 PASS. Files: LauncherPinsRepository.kt, LauncherPinsRepositoryImpl.kt, LauncherJournalRepository.kt, LauncherJournalRepositoryImpl.kt (+4..6 LOC each). Both impls delegate to the DAO on `Dispatchers.IO`, matching their existing writes.

---

### Step 01.4 - Add launch-statistics clearing to the installed-apps repository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/InstalledAppsRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/InstalledAppsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Declare `suspend fun clearLaunchStats()` on `InstalledAppsRepository` next to `observeLaunchStats`, and implement it in `InstalledAppsRepositoryImpl` by calling the launch-stats DAO `deleteAll()`. Leave the cached installed-app list untouched - it is a rebuildable mirror of the device, not launcher state.

**Why:**

Strategic §6 item 3 resolves that "as after installation" is meant literally and covers the launch statistics, while §2 goal 5 forbids the reset from touching anything the launcher does not own, which is why the installed-app cache in the same repository stays.

**Verification:**

- `Grep` - `suspend fun clearLaunchStats()` matches once in `InstalledAppsRepository.kt`.
- `Grep` - `override suspend fun clearLaunchStats()` matches once in `InstalledAppsRepositoryImpl.kt`.
- `Grep` - `replaceAll` in `InstalledAppsRepositoryImpl.kt` is unchanged from HEAD.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3\3 PASS. Files: InstalledAppsRepository.kt (+6 LOC), InstalledAppsRepositoryImpl.kt (+4 LOC). `replaceAll` untouched - the installed-app cache is not launcher state.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` BUILD SUCCESSFUL in 2m 50s, exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the phase via `post-change.ps1` (verdict `post-change: PASS`).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1: domain interface / data impl split held, no naming or comment defects. Layer 2: every new method is `suspend` on `Dispatchers.IO`. Layer 4: no schema change (`AppDatabase` still `version = 46`), the desktop wipe is one `db.withTransaction`, and the state-row delete is what clears the seeded flags - the loss of the stored column widths it implies is documented on the interface and consumed by Phase 02.

---

## Handoff Notes to Next Phase

Every launcher-owned store now has exactly one clearing call, and clearing the desktop also clears the seeded flags and the stored column widths. Phase 02 must therefore read the column widths **before** calling `clearAll`.

---

## Rollback Plan

Revert phase commit(s) - additive query and method declarations only, no schema or data migration.

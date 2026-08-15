# Phase 01 - Installed-app cache schema

**Strategic spec:** [`../S1401_launcher-all-apps-screen.md`](../S1401_launcher-all-apps-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Introduce the persistent installed-app cache and the per-command launch-statistics table with their Room migration, domain model and repository seam. No enumeration, no icons on disk and no UI yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1401 phase 01"` before the first multi-file source edit (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/InstalledAppEntity.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherLaunchStatsEntity.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration45To46.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 810 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 275 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/InstalledApp.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/InstalledAppsRepository.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/InstalledAppsRepositoryImpl.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/InstalledAppsModule.kt` | New | ≤ 50 |

> `AppDatabase.kt` is 793 LOC - back it up to `temp/S1401/` before editing (CLAUDE.md Rule 5), covered by Step 01.3.
>
> Every file in this phase lives in `src/main` on purpose: the cache is flavor-neutral and also serves `AppPickerDialogFragment`, which exists in flavors without launcher mode (strategic ADR-1).

---

## Steps

### Step 01.1 - Add the installed-app cache entity and DAO

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/InstalledAppEntity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `InstalledAppEntity` with table name `installed_app_cache` and `packageName` as the primary key. Columns: `label`, `labelSortKey` (lowercased label, so ordering needs no per-row work at query time), `firstInstallTime`, `lastUpdateTime`, `category` (Int), `isSystemApp` (Boolean), `iconFileName` (nullable String), `cacheFormatVersion` (Int) and `refreshedAt` (Long). In the same file declare `InstalledAppDao` with: a `Flow<List<InstalledAppEntity>>` read of all rows, an upsert of a list, a delete by package name, a delete of every row whose `packageName` is not in a supplied list, and a count query. Do not sort in the DAO - ordering is Phase 03's job and depends on a runtime setting.

**Why:**

The multi-second wait the strategic spec §1 describes exists because the app list has no storage at all; this table is the storage every later phase reads. The column set is exactly the free-to-read field inventory from research item 1 - name, install date, update date, category, system flag - which is what makes the five sort orders of §2 goal 4 possible without any permission.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/InstalledAppEntity.kt` exists.
- `Grep` - `@Entity(tableName = "installed_app_cache")` matches exactly once.
- `Grep` - `interface InstalledAppDao` matches exactly once.
- `Grep` - `labelSortKey` present.

**Status:** `[x]` done

---

### Step 01.2 - Add the launch-statistics entity and DAO

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherLaunchStatsEntity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `LauncherLaunchStatsEntity` with table name `launcher_launch_stats`, primary key `target` (the encoded `LauncherCellCommand`), plus `launchCount` (Int) and `lastLaunchedAt` (Long). Declare `LauncherLaunchStatsDao` with an upsert that increments the count and overwrites the timestamp for an existing target and inserts a row with count 1 otherwise, and a `Flow<List<LauncherLaunchStatsEntity>>` read of all rows.

**Why:**

Strategic ADR-3 sources the "frequency and recency" order from the app's own launch journal rather than system usage statistics, but `LauncherJournalRepositoryImpl` trims that journal to 50 rows, so counts derived from it would silently reset as the user launches things. A separate aggregate row per target is what makes the §2 goal 4 frequency order survive the trim.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherLaunchStatsEntity.kt` exists.
- `Grep` - `@Entity(tableName = "launcher_launch_stats")` matches exactly once.
- `Grep` - `interface LauncherLaunchStatsDao` matches exactly once.

**Status:** `[x]` done

---

### Step 01.3 - Register both tables, bump the schema to 46 and write the migration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration45To46.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Copy `AppDatabase.kt` to `temp/S1401/` with a timestamp before editing. Add `InstalledAppEntity` and `LauncherLaunchStatsEntity` to the `@Database` entity list, raise `version` from 45 to 46, and add abstract accessors `installedAppDao()` and `launcherLaunchStatsDao()`. Create `Migration45To46.kt` following the shape of `Migration44To45.kt` - a `val MIGRATION_45_46` that issues two `CREATE TABLE IF NOT EXISTS` statements matching the entity definitions exactly. Register `MIGRATION_45_46` in `DatabaseModule`'s `addMigrations(..)` list and add the two `@Provides` DAO functions beside `provideLauncherJournalDao`. Never alter an earlier migration.

**Why:**

The strategic §3.2 data-compatibility constraint requires the cache to be additive and recoverable rather than a user-data migration; two new tables with no touch on existing ones is the form that satisfies it. Room refuses to open a database whose declared version outruns its registered migrations, so the version bump and the migration must land in the same step or the app will not start.

**Verification:**

- `Grep` - `version = 46` matches exactly once in `AppDatabase.kt`.
- `Grep` - `val MIGRATION_45_46` matches exactly once in `Migration45To46.kt`.
- `Grep` - `MIGRATION_45_46` present in `DatabaseModule.kt`.
- `Grep` - `installedAppDao` present in both `AppDatabase.kt` and `DatabaseModule.kt`.
- `Glob` - a timestamped `AppDatabase.kt` copy exists under `temp/S1401/`.

**Status:** `[x]` done

---

### Step 01.4 - Add the domain model and sort-order type

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/InstalledApp.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create `InstalledApp` carrying `packageName`, `label`, `firstInstallTime`, `lastUpdateTime`, `category`, `isSystemApp` and `iconFile: java.io.File?`. In the same file declare `enum class InstalledAppSortOrder { LABEL, INSTALL_DATE, UPDATE_DATE, LAUNCH_FREQUENCY, CATEGORY }`. The model carries a `File`, never a `Drawable`, so a list of a hundred apps costs no bitmap memory until something asks to draw one.

**Why:**

Strategic §5.1 requires the application layer to hand the surface a ready list, and §3.2 caps the performance budget at "no visible wait"; a model holding decoded drawables would reintroduce exactly the per-open decode cost §1 blames for the current wait. The enum fixes the five orders the owner signed off in §3.3 so no later phase has to re-derive the set.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/InstalledApp.kt` exists.
- `Grep` - `enum class InstalledAppSortOrder` matches exactly once.
- `Grep` - `LAUNCH_FREQUENCY` present.
- `Grep` - `Drawable` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.5 - Add the repository seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/InstalledAppsRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/InstalledAppsRepositoryImpl.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Declare `InstalledAppsRepository` in the domain layer with `observeApps(): Flow<List<InstalledApp>>`, `replaceAll(apps: List<InstalledApp>)`, `upsert(app: InstalledApp)`, `remove(packageName: String)`, `cachedCount(): Int` and `observeLaunchStats(): Flow<Map<String, LaunchStats>>` where `LaunchStats` holds count and last-launch time. Implement it in `InstalledAppsRepositoryImpl` over the two DAOs, mapping entity to model and resolving `iconFileName` to a `File` in the cache directory. All database work runs on `Dispatchers.IO`.

**Why:**

Strategic §5.3 requires the cache to be reachable as a stream so other surfaces - the app-launch panel picker, the desktop cell picker, later widgets - can subscribe to it instead of each re-enumerating the system. Placing the interface in the domain layer keeps the dependency direction of the project's Clean layering intact.

**Verification:**

- `Grep` - `interface InstalledAppsRepository` matches exactly once.
- `Grep` - `class InstalledAppsRepositoryImpl` matches exactly once.
- `Grep` - `observeLaunchStats` present in both files.
- `Grep` - `Dispatchers.IO` present in the implementation.

**Status:** `[x]` done

---

### Step 01.6 - Bind the repository in Hilt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/InstalledAppsModule.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> Create `InstalledAppsModule` as a `@Module @InstallIn(SingletonComponent::class)` abstract class with one `@Binds @Singleton` function binding `InstalledAppsRepositoryImpl` to `InstalledAppsRepository`, following the shape of `LauncherDesktopModule`. Do not add the binding to `LauncherDesktopModule` - that module is launcher-scoped and this repository is not.

**Why:**

Strategic ADR-1 places the cache in the shared layer because `AppPickerDialogFragment` uses it in flavors that have no launcher mode at all; binding it from a launcher-named module would misstate that ownership to every later reader.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/di/InstalledAppsModule.kt` exists.
- `Grep` - `abstract class InstalledAppsModule` matches exactly once.
- `Grep` - `InstalledAppsRepository` present in that file.
- `.\a.ps1 fk` exits 0 - a missing Hilt binding does not surface until the graph is built, so a compile of the flavor is the minimum proof here.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` expected: exit 0 | actual: exit 0 (`BUILD SUCCESSFUL in 1m 45s`; `hiltJavaCompileStandardDebug` ran, so the new Hilt binding resolves).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in every file listed in "Files Touched".
- [x] Dev log entry added for the phase via `.\scripts\add_to_dev_log.ps1` - one per step through `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `catalog_sync.ps1` inside each `post-change.ps1` run.
- [x] Phase-boundary audit run - one P2 finding, fixed in phase (see Step Log).
- [x] `temp/CODE.LOCK` released - `post-change.ps1` releases it at the end of each run.

---

## Step Log

- 2026-08-05 - PHASE-BOUNDARY AUDIT. Layer 1 (architecture) and Layer 4 (Room) run over all seven files.
  - Layer 4, P2, FIXED IN PHASE: `AppDatabaseSchemaExportTest`'s KDoc states the standing convention "each version bump commits its N.json and adds an (N-1)->N test", and the plan had no such step. Added `AppDatabaseMigration45To46Test` (instrumented, asserts the pre-migration `launcher_pins` row survives and both new tables arrive empty). `46.json` was exported by the build and is present.
  - Layer 4, checked and clear: every DAO call is `suspend` or a `Flow` with `flowOn(Dispatchers.IO)`; `deleteMissing` with an empty list is valid on SQLite, which accepts an empty `IN ()` list; `MIN(cacheFormatVersion)` on an empty table returns NULL and the accessor is nullable; the migration only issues `CREATE TABLE IF NOT EXISTS` and touches no existing table.
  - Layer 1, checked and clear: entity + DAO per file matches the neighbouring `LauncherPinEntity` layout; domain model holds no Android drawable type; the repository interface sits in `domain`, its implementation in `data`.
  - Not applicable: Layers 2 and 3 - this phase adds no lifecycle owner, coroutine scope, listener or long-lived UI reference.
- 2026-08-05 - ENVIRONMENT NOTE. Mid-phase, another session archived S1038 and its four stale `Timber.d` probes, which failed one `post-change` run at 13:22 and passed on re-run once that session finished deleting them. Not a defect of this phase; recorded because the tree was being edited concurrently.
- 2026-08-05 - Step 01.6 DONE. Created `InstalledAppsModule` binding the repository as a singleton, separate from `LauncherDesktopModule`. Verification: file exists; `abstract class InstalledAppsModule` x1; `InstalledAppsRepository` present; `.\a.ps1 fk` expected: exit 0 | actual: exit 0 - KSP Room codegen accepted both entities, the `@Transaction` default DAO method and the exported schema.
- 2026-08-05 - Step 01.5 DONE. Created `InstalledAppsRepository` (+ `LaunchStats`) and `InstalledAppsRepositoryImpl`. Verification: both declarations x1; `observeLaunchStats` in both; `Dispatchers.IO` x7 in the impl. Two internal constants declared here for Phase 02 to reuse rather than redeclare: `INSTALLED_APP_ICON_DIR` and `INSTALLED_APP_CACHE_FORMAT_VERSION`.
- 2026-08-05 - Step 01.4 DONE. Created `InstalledApp` (icon as `File`) and `InstalledAppSortOrder` with the five signed-off orders plus a tolerant `fromNameOrDefault`. Verification: file exists; `enum class InstalledAppSortOrder` x1; `LAUNCH_FREQUENCY` present; zero `Drawable` hits.
- 2026-08-05 - Step 01.3 DONE. `AppDatabase` backed up to `temp/S1401/AppDatabase.kt.20260805-131400.bak`; both entities registered, `version = 46`, two DAO accessors added; `MIGRATION_45_46` created (two `CREATE TABLE IF NOT EXISTS`, no existing table touched) and registered in `DatabaseModule` with both `@Provides` DAO functions. Verification: all five predicates PASS.
- 2026-08-05 - Step 01.2 DONE. Created `LauncherLaunchStatsEntity` + `LauncherLaunchStatsDao`. Verification: file exists; `@Entity(tableName = "launcher_launch_stats")` x1; `interface LauncherLaunchStatsDao` x1. Increment implemented as UPDATE-then-INSERT inside `@Transaction` because SQLite at minSdk 26 predates the UPSERT clause.
- 2026-08-05 - Step 01.1 DONE. Created `InstalledAppEntity` + `InstalledAppDao`. Verification: file exists; `@Entity(tableName = "installed_app_cache")` x1; `interface InstalledAppDao` x1; `labelSortKey` present. No `ORDER BY` in any query - ordering is Phase 03's runtime concern.

---

## Handoff Notes to Next Phase

The cache is declared and reachable but always empty: nothing writes to it yet, and `QueryLaunchableAppsUseCase` still enumerates the system on every call. Phase 02 fills it and switches the readers over.

---

## Rollback Plan

Revert the phase commit. The migration only creates two new tables, so a revert on a device that already opened version 46 requires clearing app data or reinstalling - note this before testing on the owner's phone rather than the emulator.

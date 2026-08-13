# Phase 01 - Domain Model and Room Storage

**Strategic spec:** [`../S0623_app-launch-panel-dialog.md`](../S0623_app-launch-panel-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 6 / 6
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Introduce the tile domain model and Room persistence (entity, DAO, migration v35->v36) for the app-launch panel. No repository, UseCase, or UI yet.

---

## Prerequisites

- [ ] Working tree builds (`.\a.ps1 fk`).
- [ ] `AppDatabase` is at `version = 35` (verified 2026-06-23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppLaunchPanelTileType.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppLaunchPanelTile.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppLaunchPanelTileEntity.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppLaunchPanelTileDao.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration35To36.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 300 |

---

## Steps

### Step 01.1 - Add the tile-type enum

**Files:** `domain/model/AppLaunchPanelTileType.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `enum class AppLaunchPanelTileType` with values `OWN_APP`, `EXTERNAL_APP`, `INTERNAL_ROUTE`, `RESERVED`. `OWN_APP` = FastMediaSorter itself; `EXTERNAL_APP` = a pinned external launcher package; `INTERNAL_ROUTE` and `RESERVED` are modelled-but-unused extension points for v1 (strategic §6.4, §5.3). Add a tolerant companion `fromName(name: String?, default: AppLaunchPanelTileType): AppLaunchPanelTileType` mirroring `ScreenshotGestureAction.fromName` so unknown stored strings degrade to a default instead of throwing.

**Verification:**

- `Glob` - file exists.
- `Grep` - `enum class AppLaunchPanelTileType` matches once.
- `Grep` - `OWN_APP`, `EXTERNAL_APP`, `INTERNAL_ROUTE`, `RESERVED` all present.
- `Grep` - `fun fromName(` present.

**Status:** `[x] done`

---

### Step 01.2 - Add the tile domain model

**Files:** `domain/model/AppLaunchPanelTile.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `data class AppLaunchPanelTile` with: `slotIndex: Int` (0..14, fixed-grid position), `type: AppLaunchPanelTileType`, `targetId: String?` (package name for `EXTERNAL_APP`, route key for `INTERNAL_ROUTE`, null for `OWN_APP`), `labelOverride: String?` (user-set caption; null = use resolved label), `addedAt: Long`. Pure domain - no Android imports.

**Verification:**

- `Glob` - file exists.
- `Grep` - `data class AppLaunchPanelTile` matches once.
- `Grep` - `val slotIndex: Int` and `val type: AppLaunchPanelTileType` present.
- `Grep -n "import android"` - zero hits in this file.

**Status:** `[x] done`

---

### Step 01.3 - Add the Room entity

**Files:** `data/local/db/AppLaunchPanelTileEntity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Entity(tableName = "app_launch_panel_tiles")` `data class AppLaunchPanelTileEntity` with `@PrimaryKey val slotIndex: Int`, `val type: String`, `val targetId: String?`, `val labelOverride: String?`, `val addedAt: Long`. Mirror the column style of `StreamSourceEntity`. The `type` is stored as the enum `.name` string so new tile types never require a schema migration (strategic §3.2).

**Verification:**

- `Glob` - file exists.
- `Grep` - `tableName = "app_launch_panel_tiles"` present.
- `Grep` - `@PrimaryKey` and `val slotIndex: Int` present.
- `Grep` - `val type: String` present.

**Status:** `[x] done`

---

### Step 01.4 - Add the DAO

**Files:** `data/local/db/AppLaunchPanelTileDao.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create `@Dao interface AppLaunchPanelTileDao` with: `@Query("SELECT * FROM app_launch_panel_tiles ORDER BY slotIndex ASC") fun observeAll(): Flow<List<AppLaunchPanelTileEntity>>`; a suspend `getAll(): List<AppLaunchPanelTileEntity>` with the same ordered query; `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: AppLaunchPanelTileEntity)`; `@Query("DELETE FROM app_launch_panel_tiles WHERE slotIndex = :slotIndex") suspend fun deleteBySlot(slotIndex: Int)`; `@Query("DELETE FROM app_launch_panel_tiles") suspend fun clearAll()`; and `@Query("SELECT COUNT(*) FROM app_launch_panel_tiles") suspend fun count(): Int`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `@Dao` and `interface AppLaunchPanelTileDao` present.
- `Grep` - `fun observeAll(): Flow<List<AppLaunchPanelTileEntity>>` present.
- `Grep` - `suspend fun upsert(`, `suspend fun deleteBySlot(`, `suspend fun count(` present.

**Status:** `[x] done`

---

### Step 01.5 - Add the migration

**Files:** `data/local/db/Migration35To36.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create `val MIGRATION_35_36 = object : Migration(35, 36)` (top-level `val` in package `com.sza.fastmediasorter.data.local.db`, mirroring `Migration34To35.kt`). Its `migrate` runs one `CREATE TABLE IF NOT EXISTS app_launch_panel_tiles (slotIndex INTEGER PRIMARY KEY NOT NULL, type TEXT NOT NULL, targetId TEXT, labelOverride TEXT, addedAt INTEGER NOT NULL)`. Match the exact column names/types of the entity so Room's schema hash validates.

**Verification:**

- `Glob` - file exists.
- `Grep` - `Migration(35, 36)` present.
- `Grep` - `CREATE TABLE IF NOT EXISTS app_launch_panel_tiles` present.
- `Grep` - `slotIndex INTEGER PRIMARY KEY NOT NULL` present.

**Status:** `[x] done`

---

### Step 01.6 - Register entity, DAO accessor, version bump, migration

**Files:** `data/local/db/AppDatabase.kt`, `core/di/DatabaseModule.kt`
**Depends on:** Steps 01.3, 01.4, 01.5

**Prompt for developer:**

> In `AppDatabase.kt`: add `AppLaunchPanelTileEntity::class` to the `@Database(entities = [...])` list, change `version = 35` to `version = 36`, and add `abstract fun appLaunchPanelTileDao(): AppLaunchPanelTileDao`. In `DatabaseModule.kt`: add `Migration35To36.MIGRATION_35_36` (or the bare `MIGRATION_35_36` import) to the `.addMigrations(...)` chain that builds `AppDatabase`, and if the module exposes the new DAO via `@Provides`, add a provider returning `database.appLaunchPanelTileDao()` (follow the existing DAO-provider pattern in that module; if DAOs are obtained directly from the injected `AppDatabase`, no provider is needed).

**Verification:**

- `Grep` - `version = 36` in `AppDatabase.kt`.
- `Grep` - `AppLaunchPanelTileEntity::class` in `AppDatabase.kt`.
- `Grep` - `fun appLaunchPanelTileDao()` in `AppDatabase.kt`.
- `Grep` - `MIGRATION_35_36` in `DatabaseModule.kt`.
- Build: `.\a.ps1 fk` exits 0 (Room kapt schema validation passes at v36).

**Status:** `[x] done`

---

## Step Log

- 2026-06-23 - Steps 01.1-01.6 Verification PASS. New: AppLaunchPanelTileType.kt, AppLaunchPanelTile.kt, AppLaunchPanelTileEntity.kt, AppLaunchPanelTileDao.kt, Migration35To36.kt. Modified: AppDatabase.kt (@Database v36 + entity + dao accessor), DatabaseModule.kt (MIGRATION_35_36 import/registration + DAO provider). Build `.\a.ps1 fk` exit 0 - kapt validated Room schema v36.

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` (kapt validates Room schema v36).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the change set via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2` (deferred to Phase 07 - once per ticket).

---

## Handoff Notes to Next Phase

The persistence layer exists: query tiles via `AppLaunchPanelTileDao.observeAll()`. `slotIndex` is the stable 0..14 grid position; absence of a row = empty slot. Phase 02 wraps this DAO in a repository.

---

## Rollback Plan

Revert phase commit(s). The migration only adds a new table; downgrading the `@Database` version back to 35 and dropping `app_launch_panel_tiles` fully reverses it. No existing-table data touched.

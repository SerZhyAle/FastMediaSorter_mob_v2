# Phase 02 - Config Persistence

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04
**Steps done:** 6 / 6
**Started:** 2026-07-17
**Completed:** 2026-07-17

---

## Objective

Persist the desktop: command codec, four Room tables (cells / journal / pins / state) with migration 40→41, repositories, and the launcher fields in `AppSettings`. Single schema-change phase for the whole epic.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` still declares `version = 40` (re-check; if bumped by a sibling ticket, use current+1 everywhere below).
- [ ] CODE.LOCK acquired (`enter-code-lock.ps1 -Reason "S0404 phase 02"`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCell.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherCellEntity.kt` | New (entity + DAO) | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherJournalEntity.kt` | New (entity + DAO) | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherPinEntity.kt` | New (entity + DAO) | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherStateEntity.kt` | New (entity + DAO) | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration40To41.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified (backup first if >500 LOC) | +10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | +25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherJournalRepository.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherPinsRepository.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherJournalRepositoryImpl.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherPinsRepositoryImpl.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/LauncherDesktopModule.kt` | New | ≤ 45 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified (475 LOC - no backup needed, but re-check) | +6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified (backup first - large file) | +25 |

---

## Steps

### Step 02.1 - Command codec `LauncherCellCommand`

**Files:** `domain/model/launcher/LauncherCellCommand.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Mirror the prefix-codec style of `domain/model/panel/AppLaunchPanelRouteTarget.kt` (same KDoc discipline, tolerant decode → null). Sealed interface `LauncherCellCommand` with `fun encode(): String` and subtypes:
> - `App(packageName: String)` → `"app:<packageName>"`
> - `Feature(routeKey: String)` → `"fn:<routeKey>"` (route keys = `InternalRouteCatalog.KEY_*`)
> - `Resource(resourceId: Long, mode: LauncherResourceMode)` → `"res:<id>:<MODE>"`
> - `Stream(streamId: String)` → `"stream:<streamId>"` (id = `StreamSourceEntity.id`)
> - `OsShortcut(targetKey: String)` → `"os:<targetKey>"` (keys = `OsShortcutCatalog`)
> Plus `enum class LauncherResourceMode { BROWSE, SLIDESHOW, PLAY }` in the same file (PLAY = open in `PlayerActivity` without slideshow - covers reader and audio-playlist start; strategic §3.3 shortcut types). `companion object { fun decode(raw: String?): LauncherCellCommand? }` - unknown prefix, empty payload, malformed id or unknown mode name all return null. No bare numeric literals; no comments restating the obvious.

**Verification:**

- `Grep` - `sealed interface LauncherCellCommand` matches once.
- `Grep` - `enum class LauncherResourceMode` matches once; `BROWSE`, `SLIDESHOW`, `PLAY` present.
- `Grep` - `fun decode(raw: String?)` present.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS (sealed interface 1x, enum 1x with BROWSE/SLIDESHOW/PLAY, decode signature present; 0 lines >120). Files: domain/model/launcher/LauncherCellCommand.kt (new, 93 LOC). Five prefixes: app:/fn:/res:/stream:/os:; resource payload splits on the first `:` so mode names never collide with the id.

---

### Step 02.2 - Domain model `LauncherCell`

**Files:** `domain/model/launcher/LauncherCell.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Declare:
> ```kotlin
> enum class LauncherOrientation { PORTRAIT, LANDSCAPE }
> enum class LauncherCellKind { SHORTCUT, GADGET }
> data class LauncherCell(
>     val id: Long,
>     val orientation: LauncherOrientation,
>     val rowIndex: Int,
>     val colIndex: Int,
>     val spanW: Int,
>     val spanH: Int,
>     val kind: LauncherCellKind,
>     /** Encoded LauncherCellCommand for SHORTCUT; gadget key (Phase 06 registry) for GADGET. */
>     val target: String,
>     val labelOverride: String?,
>     val addedAt: Long,
> )
> ```
> KDoc on the class: one grid item per row; a gadget occupies `spanW×spanH` cells; layouts are independent per orientation (strategic §3.3).

**Verification:**

- `Grep` - `data class LauncherCell(` matches once; `enum class LauncherOrientation` and `enum class LauncherCellKind` present.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 1/1 PASS (data class + both enums present). Files: domain/model/launcher/LauncherCell.kt (new, 31 LOC).

---

### Step 02.3 - Room entities + DAOs (4 files)

**Files:** `data/local/db/LauncherCellEntity.kt`, `LauncherJournalEntity.kt`, `LauncherPinEntity.kt`, `LauncherStateEntity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Follow the entity+DAO-in-one-file precedent of `data/local/db/DeviceProfileEntity.kt`. All DAO methods `suspend` or `Flow` (no main-thread Room), `Flow` queries used for rendering must be consumed with `distinctUntilChanged` at repository level.
> - `@Entity(tableName = "launcher_cells")` `LauncherCellEntity(@PrimaryKey(autoGenerate = true) id: Long = 0, orientation: String, rowIndex: Int, colIndex: Int, spanW: Int, spanH: Int, kind: String, target: String, labelOverride: String?, addedAt: Long)`. DAO `LauncherCellDao`: `observeByOrientation(orientation: String): Flow<List<LauncherCellEntity>>` (ORDER BY rowIndex, colIndex), `upsert(entity)`, `update(entity)`, `deleteById(id: Long)`, `insertAll(entities: List<...>)`, `countByOrientation(orientation: String): Int`.
> - `@Entity(tableName = "launcher_journal")` `LauncherJournalEntity(@PrimaryKey(autoGenerate = true) id: Long = 0, target: String, launchedAt: Long)`. DAO: `insert(entity)`, `recent(limit: Int): Flow<List<LauncherJournalEntity>>` (ORDER BY launchedAt DESC), `trim(keep: Int)` (`DELETE FROM launcher_journal WHERE id NOT IN (SELECT id FROM launcher_journal ORDER BY launchedAt DESC LIMIT :keep)`).
> - `@Entity(tableName = "launcher_pins")` `LauncherPinEntity(@PrimaryKey position: Int, target: String)`. DAO: `observeAll(): Flow<List<LauncherPinEntity>>` (ORDER BY position), `upsert`, `deleteByPosition(position: Int)`, `replaceAll(pins)` wrapped `@Transaction`.
> - `@Entity(tableName = "launcher_state")` `LauncherStateEntity(@PrimaryKey id: Int = 1, seededPortrait: Boolean, seededLandscape: Boolean, columnsPortrait: Int, columnsLandscape: Int)`. DAO: `get(): LauncherStateEntity?`, `upsert(entity)`.

**Verification:**

- `Grep` - `tableName = "launcher_cells"`, `"launcher_journal"`, `"launcher_pins"`, `"launcher_state"` each match once.
- `Grep` - `allowMainThreadQueries` zero hits in touched files.
- `Grep` - `@Transaction` present in `LauncherPinEntity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS (4 tables 1x each, allowMainThreadQueries 0, @Transaction in LauncherPinEntity). Files: LauncherCellEntity.kt / LauncherJournalEntity.kt / LauncherPinEntity.kt / LauncherStateEntity.kt (new, entity+DAO per file). Added `getById` to LauncherCellDao beyond the plan - `moveCell` (step 02.5) needs to read a cell before repositioning it.

---

### Step 02.4 - Migration 40→41 + AppDatabase + DatabaseModule

**Files:** `data/local/db/Migration40To41.kt`, `data/local/db/AppDatabase.kt`, `core/di/DatabaseModule.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Back up `AppDatabase.kt` to `temp/S0404/` if >500 LOC. Follow the top-level-object migration precedent (e.g. `data/local/db/Migration35To36.kt` which created `app_launch_panel_tiles`): `Migration40To41` object with the four `CREATE TABLE IF NOT EXISTS` statements matching Step 02.3 column-for-column (INTEGER for Boolean, TEXT for String, exact NOT NULL / default constraints must equal what Room expects - copy the pattern of an existing migration and validate against the generated schema JSON). In `AppDatabase.kt`: bump `version = 41`, add the four entities to `entities = [...]`, add four abstract DAO accessors (`launcherCellDao()`, `launcherJournalDao()`, `launcherPinDao()`, `launcherStateDao()`). In `core/di/DatabaseModule.kt`: register `Migration40To41` in `addMigrations(...)` and add four `@Provides @Singleton` DAO providers mirroring `provideAppLaunchPanelTileDao`. Never rename or edit prior migrations.

**Verification:**

- `Grep` - `version = 41` in `AppDatabase.kt`; `Migration40To41` referenced in `DatabaseModule.kt`.
- `Glob` - `app_v2/schemas/**/41.json` exists after the Step 02.6 build.
- `Grep` - `launcherCellDao` present in both `AppDatabase.kt` and `DatabaseModule.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Grep predicates 2/3 PASS (version = 41; MIGRATION_40_41 imported + registered; launcherCellDao in both files). Schema-41 JSON predicate pends the Step 02.6 build - re-checked there.
- 2026-07-17 - Deferred predicate resolved in step 02.6: `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/41.json` exported with all four launcher tables, and the migration statements were then rewritten to be verbatim copies of its `createSql`. All 3/3 predicates PASS - status flipped from `[~]` to `[x]` (deep audit 2026-07-17 caught it as the only step marker left mid-flight across the four Done phases). Files: Migration40To41.kt (new, 4 additive CREATE TABLE), AppDatabase.kt (+4 entities, +4 DAO accessors, version 40 -> 41), core/di/DatabaseModule.kt (+4 @Provides @Singleton DAO providers, migration registered). Import order fixed to ktlint ASCII order (Launcher* before MIGRATION_*).

---

### Step 02.5 - Repositories + Hilt bindings

**Files:** `domain/repository/LauncherDesktopRepository.kt`, `LauncherJournalRepository.kt`, `LauncherPinsRepository.kt`, `data/repository/LauncherDesktopRepositoryImpl.kt`, `LauncherJournalRepositoryImpl.kt`, `LauncherPinsRepositoryImpl.kt`, `core/di/LauncherDesktopModule.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Interfaces (domain) - map entity↔domain inside the impls (String↔enum via `enumValueOf` with tolerant fallback: unknown orientation/kind rows are skipped with `Timber.w`, never crash - CSV-applier precedent):
> - `LauncherDesktopRepository`: `observeCells(orientation: LauncherOrientation): Flow<List<LauncherCell>>`; `suspend addCell(cell: LauncherCell)`; `suspend updateCell(cell)`; `suspend removeCell(id: Long)`; `suspend moveCell(id: Long, rowIndex: Int, colIndex: Int)`; `suspend seedIfEmpty(orientation, cells: List<LauncherCell>): Boolean` (`@Transaction`-style: insert only when `countByOrientation == 0` AND state flag for that orientation is false; sets the flag; returns whether it seeded); `suspend state(): LauncherDesktopState`; `suspend updateColumns(orientation, columns: Int)`. Add small `data class LauncherDesktopState(seededPortrait: Boolean, seededLandscape: Boolean, columnsPortrait: Int, columnsLandscape: Int)` in the interface file.
> - `LauncherJournalRepository`: `suspend record(target: LauncherCellCommand)` (insert encoded + `trim(50)` in one call; `MAX_JOURNAL_ROWS = 50` as companion const), `recentApps(limit: Int): Flow<List<String>>` (decode rows, keep `App` commands, distinct package order-preserved).
> - `LauncherPinsRepository`: `observePins(): Flow<List<Pair<Int, LauncherCellCommand>>>` (decoded, invalid rows dropped), `suspend setPin(position: Int, command: LauncherCellCommand)`, `suspend removePin(position: Int)`.
> Impls under `data/repository/` with `withContext(Dispatchers.IO)` at this boundary and `distinctUntilChanged()` on all observe methods. `core/di/LauncherDesktopModule.kt`: `@Module @InstallIn(SingletonComponent::class)` with `@Binds` for the three pairs (mirror `core/di/AppLaunchPanelModule.kt`).

**Verification:**

- `Grep` - `interface LauncherDesktopRepository` / `LauncherJournalRepository` / `LauncherPinsRepository` each match once.
- `Grep` - `distinctUntilChanged` present in all three impl files.
- `Grep` - `@Binds` count in `LauncherDesktopModule.kt` == 3.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS (3 interfaces 1x each, distinctUntilChanged in all impls, @Binds == 3; 0 lines >120). Files: domain/repository/{LauncherDesktopRepository,LauncherJournalRepository,LauncherPinsRepository}.kt + data/repository/*Impl.kt + core/di/LauncherDesktopModule.kt (new). Deviations from the prompt, both mechanical: (1) `addCell` returns the generated `Long` id - the edit-mode host needs it to address the new cell; (2) `moveCell` swaps via a new point query `LauncherCellDao.getAt(orientation,row,col)` inside `db.withTransaction` (AppLaunchPanelRepositoryImpl precedent) rather than loading the whole layout. `LauncherDesktopState` lives in the interface file as planned.

---

### Step 02.6 - AppSettings launcher fields + compile gate

**Files:** `domain/model/AppSettings.kt`, `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** - parallel to 02.1-02.5

**Prompt for developer:**

> Back up `SettingsRepositoryImpl.kt` to `temp/S0404/` first. Add to `AppSettings` (with defaults): `launcherDensityFactor: Float = 1.0f` (allowed values 0.75f/1.0f/1.25f/1.5f - declare `LAUNCHER_DENSITY_OPTIONS` companion const list), `launcherTaskbarShowRecents: Boolean = true`, `launcherTaskbarShowPinned: Boolean = true`, `launcherTaskbarShowTray: Boolean = true` (strategic §3.3: taskbar composition is configurable). In `SettingsRepositoryImpl` companion add the matching `floatPreferencesKey`/`booleanPreferencesKey` entries, read-mapping and write branches exactly like neighbouring fields. Do NOT add rows to `device_profile_presets.csv` (desktop content is Room-owned; ADR-4). Then run `.\a.ps1 fk` - this is the schema-change build that also exports `app_v2/schemas/**/41.json`; if kapt stalls use `scripts/utils/recover-kapt-stall.ps1`.

**Verification:**

- `Grep` - `launcherDensityFactor` present in both `AppSettings.kt` and `SettingsRepositoryImpl.kt`.
- `.\a.ps1 fk` → BUILD SUCCESSFUL (record exit code).
- `Glob` - `app_v2/schemas/**/41.json` exists.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS. `.\a.ps1 fk` expected BUILD SUCCESSFUL | actual BUILD SUCCESSFUL (45s). `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/41.json` exported with all four launcher tables. Files: AppSettings.kt (+4 fields, +companion LAUNCHER_DENSITY_OPTIONS), SettingsRepositoryImpl.kt (+4 DataStore keys, read defaults, write branches). CSV untouched (ADR-4).
- 2026-07-17 - Migration hardening: rewrote `Migration40To41` statements to be verbatim copies of `41.json`'s `createSql` (backticked identifiers, `PRIMARY KEY(col)` table constraint for the single-column keys) instead of the equivalent-but-different inline `INTEGER PRIMARY KEY NOT NULL` form. Both parse to the same TableInfo, but a verbatim copy cannot drift from what Room validates at open time. Guard `AppDatabaseSchemaExportTest` (S0731) run targeted: expected pass | actual BUILD SUCCESSFUL (1m) - it reads @Database(version) from source and asserts the matching schema JSON, so it now covers version 41. No instrumented (N-1)->N migration test exists in the repo (none for 31..40 either), so none added here.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `.\a.ps1 fk` passes; schema 41 JSON committed.
- [ ] **DEFERRED-DEVICE** - Fresh-install AND upgrade-from-v40 both open the DB (install previous debug APK, then current - `adb.ps1 log -Grep "Migration|RoomDatabase"` shows no reset notice). No device online on 2026-07-17. Static mitigation in place: the migration's CREATE statements are verbatim copies of the Room-generated `41.json` `createSql`, so the tables cannot diverge from what Room validates at open. Re-checked in the BlockNeedUserTest device pass (Phase 10 step 10.4) - **the upgrade path is the single highest-value item of that pass**.
- [x] Dev log + `catalog_sync.ps1 -Module app_v2`; CODE.LOCK released (post-change closure: all gates PASS).

---

## Handoff Notes to Next Phase

- Everything persists through `LauncherDesktopRepository` / `LauncherJournalRepository` / `LauncherPinsRepository` - later phases never touch DAOs directly.
- `LauncherCellCommand.decode` is the single tolerance point: invalid stored targets render as empty cells, never crash.
- Non-launcher flavors compile all of this but never instantiate it (UI lives in `src/launcherEnabled`); config is inert for users who never enable the mode (strategic §3.2 data-compat).

---

## Rollback Plan

Revert phase commit(s). Migration is additive (new tables only) - a revert before release ships no schema change; if a dev device already ran v41, wipe app data (debug-only exposure).

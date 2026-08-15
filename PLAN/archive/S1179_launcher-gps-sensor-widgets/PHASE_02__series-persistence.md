# Phase 02 - Persistent series with reset

**Strategic spec:** [`../S1179_launcher-gps-sensor-widgets.md`](../S1179_launcher-gps-sensor-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Introduce one persistent time series per chart, accumulating from the last reset, bounded in rows without being bounded in duration, with reset as an operation on the series rather than on a view.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] No UI decision is open here - this phase adds no view, no layout and no string.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/SensorSeriesPointEntity.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/SensorSeriesDao.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration46To47.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | 798 → ≤ 810 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | 266 → ≤ 275 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/sensors/SensorSeriesPoint.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SensorSeriesRepository.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/SensorSeriesRepositoryImpl.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/sensors/RecordSensorSeriesPointUseCase.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/sensors/ObserveSensorSeriesUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/sensors/ResetSensorSeriesUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/SensorModule.kt` | Modified | ≤ 100 |
| `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/47.json` | New (generated) | - |
| `dev/TECH_REQUIREMENTS.md` | Modified | 1 line |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration46To47Test.kt` | New | ≤ 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/sensors/RecordSensorSeriesPointUseCaseTest.kt` | New | ≤ 220 |

> Backup / split thresholds: `AppDatabase.kt` is 798 LOC, past the 500-line backup threshold - step 02.2 carries the backup sub-step. No file in this phase approaches 1500 LOC.
>
> **Flavor placement.** Every file lands in `src/main/java/` (or the test source sets) and carries no flavor guard. The series is device data, not a launcher feature.
>
> **Landscape parity.** No layout in this phase.

---

## Steps

### Step 02.1 - Add the entity and the DAO

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/SensorSeriesPointEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/SensorSeriesDao.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `SensorSeriesPointEntity` for table `sensor_series_point` with `id` (`@PrimaryKey(autoGenerate = true)` Long), `seriesId` (String), `takenAtMillis` (Long), `primaryValue` (Double) and `secondaryValue` (Double, nullable), carrying `indices = [Index(value = ["seriesId", "takenAtMillis"])]`. Add `SensorSeriesDao` with `@Insert` for one point, `@Query` returning `Flow<List<SensorSeriesPointEntity>>` for one `seriesId` ordered by `takenAtMillis` ascending, `@Query` deleting every row of one series, and a `@Query` deleting rows of one series whose `id` is not among a supplied list of ids to keep. Suffix nothing with `Table` or `Model` - the file names above are the whole naming contract. Two columns rather than one row type per chart: the speed series fills `primaryValue` alone, the altitude series fills `primaryValue` with altitude and `secondaryValue` with cumulative distance, so one table serves both and a future third chart needs no schema change.

**Why:**

Strategic ADR-2 requires the series to outlive the desktop and the process, which only a persisted table gives, and §5.3 requires the accumulator not to know what it accumulates so any future chart reuses it.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `@Entity(tableName = "sensor_series_point"` matches exactly once.
- `Grep` - `Index(value = ["seriesId", "takenAtMillis"])` present.
- `Grep` - `interface SensorSeriesDao` matches exactly once.
- `Grep` - `Flow<List<SensorSeriesPointEntity>>` present in the DAO.
- `Grep` - `suspend` present on every non-Flow DAO function - a Room call on the main thread is a defect the audit protocol treats as P1.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 6/6 PASS. Files: data/local/db/SensorSeriesPointEntity.kt (+22 LOC, new), data/local/db/SensorSeriesDao.kt (+30 LOC, new). `@Entity` kept on one line because the step's own predicate greps the literal `@Entity(tableName = "sensor_series_point"` and a wrapped annotation would not match it. Dev log recorded.

---

### Step 02.2 - Bump the schema to 47 and register the migration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration46To47.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> First copy `AppDatabase.kt` to `temp/S1179/` with a timestamped name - it is past the 500-line backup threshold. Add `Migration46To47.kt` declaring a top-level `val MIGRATION_46_47 = object : Migration(46, 47)` that executes one `CREATE TABLE IF NOT EXISTS sensor_series_point` matching the entity exactly plus its `CREATE INDEX`, following `Migration45To46.kt` in file shape and naming. In `AppDatabase.kt` add `SensorSeriesPointEntity::class` to the `entities` array, bump `version` to 47 and add `abstract fun sensorSeriesDao(): SensorSeriesDao`. In `DatabaseModule.kt` add the import, append `MIGRATION_46_47` to the existing `addMigrations(..)` list, and add `provideSensorSeriesDao` as an `@Provides @Singleton` returning `database.sensorSeriesDao()`, copying `provideLauncherLaunchStatsDao` in shape. Rename no prior migration and add no `fallbackToDestructiveMigration` - the module deliberately has none.
>
> Bump the `Room DB version` row in `dev/TECH_REQUIREMENTS.md` to 47 in the same step. `assert-doc-pin-drift` compares that row against `AppDatabase.kt` and fails closure - but only on a `Mixed`/`Doc` change type, so a step closed as `Kotlin` sails past it and the drift surfaces later, in someone else's ticket (added 2026-08-06 during implementation, after exactly that happened here).
>
> The DAO provider is not optional plumbing: every one of the 22 DAOs in this module has one, because `AppDatabase` is the only `@Provides` in the graph and an abstract accessor on it is not itself a Hilt binding. Without it step 02.3's repository fails at annotation processing with `MissingBinding`, which neither `fk` nor `fc` reports (added 2026-08-06 during implementation - the original step omitted it).

**Why:**

Strategic §3.2 requires the accumulated series to survive an application update, which a schema addition without a registered migration would break by routing every existing install into the failure recovery path instead.

**Verification:**

- `Glob` - `Migration46To47.kt` exists.
- `Grep` - `Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO)` matches exactly once in `Migration46To47.kt`, with `SCHEMA_VERSION_FROM = 46` and `SCHEMA_VERSION_TO = 47` declared above it. The literal `Migration(46, 47)` this predicate first asked for is what detekt's `MagicNumber` rejects, which is why `Migration45To46.kt` - the file this step is told to copy in shape - names the two constants instead (corrected 2026-08-06 during implementation).
- `Grep` - `version = 47` matches exactly once in `AppDatabase.kt`; `version = 46` returns zero hits.
- `Grep` - `SensorSeriesPointEntity::class` present in `AppDatabase.kt`.
- `Grep` - `sensorSeriesDao` present in `AppDatabase.kt`.
- `Grep` - `MIGRATION_46_47` present in `DatabaseModule.kt` both as an import and inside `addMigrations`.
- `Grep` - `fun provideSensorSeriesDao` matches exactly once in `DatabaseModule.kt`.
- `Grep` - `fallbackToDestructiveMigration` returns zero hits in `DatabaseModule.kt`.
- `Glob` - `temp/S1179/` contains the timestamped `AppDatabase.kt` backup.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 8/8 PASS. Files: data/local/db/Migration46To47.kt (+31 LOC, new), data/local/db/AppDatabase.kt (798 -> 800), core/di/DatabaseModule.kt (266 -> 274). Backup: `temp/S1179/AppDatabase.kt.20260806_225344.bak`. Two predicates were corrected against the real tree rather than worked around: `Migration(46, 47)` (detekt `MagicNumber`, see above) and `fallbackToDestructiveMigration` "zero hits" - the string does occur once in `DatabaseModule.kt`, inside the pre-existing comment that explains its deliberate absence, and no call exists. Dev log recorded.

---

### Step 02.3 - Add the series repository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/sensors/SensorSeriesPoint.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SensorSeriesRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/SensorSeriesRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/di/SensorModule.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `SensorSeriesPoint.kt` add the domain point (`id` defaulting to 0, `takenAtMillis`, `primaryValue`, `secondaryValue`) and a `SensorSeriesId` enum with `SPEED` and `ALTITUDE_DISTANCE`, each carrying the stable string written to the `seriesId` column - those strings are a storage format and are never renamed. Declare `SensorSeriesRepository` with `fun observe(id: SensorSeriesId): Flow<List<SensorSeriesPoint>>`, `suspend fun append(id: SensorSeriesId, point: SensorSeriesPoint)`, `suspend fun keepOnly(id: SensorSeriesId, ids: List<Long>)` and `suspend fun clear(id: SensorSeriesId)`.
>
> The `count` member this step originally declared, and the DAO `COUNT(*)` query behind it in step 02.1, were removed 2026-08-06 during implementation. Step 02.4 reads the series once per sample - it needs the newest point for the throttle and for the running total anyway - so the row count comes off that same list and a second query would only be a redundant round trip. A brand-new interface member with no caller is dead weight under CLAUDE.md Rule 20, and the step that orphaned it is the step that deleted it. Implement it in `data/sensors/SensorSeriesRepositoryImpl` as an `@Singleton` over `SensorSeriesDao`, mapping entity to domain and back, and pinning every suspend member to `Dispatchers.IO`. Bind it in the existing `SensorModule` with `@Binds`.

**Why:**

Strategic ADR-2 separates accumulation from drawing so that a chart leaving the screen does not end the series, and the layering rule in CLAUDE.md §8 puts the Room type behind a domain interface so the gadget never sees an entity.

The `id` on the domain point was added 2026-08-06 during implementation. The step originally listed three fields, which left step 02.4 unable to do what it is told to do - "keep every second point by id" - because the only read it has, `observe`, returned points carrying no id while `keepOnly` addresses rows by id. Carrying the row id is not a new leak: `keepOnly(ids: List<Long>)` had already put row ids in the domain contract, and this closes the inconsistency rather than widening it. A default of 0 keeps `append` callers from inventing one.

**Verification:**

- `Glob` - all three new files exist.
- `Grep` - `enum class SensorSeriesId` matches exactly once and contains `SPEED` and `ALTITUDE_DISTANCE`.
- `Grep` - `interface SensorSeriesRepository` matches exactly once.
- `Grep` - `Dispatchers.IO` present in the impl.
- `Grep` - `SensorSeriesPointEntity` returns zero hits outside `data/` - the entity never crosses into domain.
- `Grep` - `@Binds` count in `SensorModule.kt` is 2.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 6/6 PASS. Files: domain/model/sensors/SensorSeriesPoint.kt (+30 LOC, new), domain/repository/SensorSeriesRepository.kt (+28 LOC, new), data/sensors/SensorSeriesRepositoryImpl.kt (+54 LOC, new), di/SensorModule.kt (27 -> 39). `SensorModule`'s KDoc said "only the availability repository needs a binding" and was rewritten rather than left to go stale. Entity/domain mapping is two private file-local extensions, so the entity stays inside `data/`. Dev log recorded.

---

### Step 02.4 - Add the record, observe and reset use cases

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/sensors/RecordSensorSeriesPointUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/sensors/ObserveSensorSeriesUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/sensors/ResetSensorSeriesUseCase.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> `ObserveSensorSeriesUseCase` and `ResetSensorSeriesUseCase` are one-line delegations to `observe` and `clear`. `RecordSensorSeriesPointUseCase` takes `(id, primaryValue, secondaryDelta: Double?, takenAtMillis)` and holds three rules.
>
> First, drop a sample taken less than `MIN_SAMPLE_INTERVAL_MS` (5000) after the series' newest point, so a fast sensor cannot fill the table. Second, when a series reaches `MAX_POINTS` (720) decimate it: keep every second point by id and delete the rest through `keepOnly`, which halves the row count and doubles the effective resolution while preserving the full span since the reset. Never drop the oldest point - the series means "since the last reset" and an evicted head would silently redefine it as a moving window, which strategic §2 non-goals forbid. Third, fold `secondaryDelta` into a running total: the stored `secondaryValue` is the newest point's `secondaryValue` plus the delta, starting from zero after a reset, and stays null for a series whose caller passes null. The running total lives here rather than in the caller because a reset must zero it, and only this use case and the repository know a reset happened.
>
> Expose both constants as `internal const` so the test asserts the same numbers the code uses.

**Why:**

Strategic §3.2 delegates the point cap to this plan, §7 names an unbounded series as the risk this rule mitigates, and §2 non-goals forbid the moving window that evicting the oldest point would create.

**Verification:**

- `Glob` - all three files exist.
- `Grep` - `class RecordSensorSeriesPointUseCase` matches exactly once.
- `Grep` - `MIN_SAMPLE_INTERVAL_MS` and `MAX_POINTS` each present.
- `Grep` - `keepOnly` present.
- `Grep` - `secondaryDelta` present in the record use case's signature.
- `Grep` - `dropWhile`, `removeFirst`, `deleteOldest` each return zero hits - eviction of the head is not how the cap works here.
- `Grep` - `Log\.d\(` returns zero hits across the three files.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 8/8 PASS. Files: domain/usecase/sensors/RecordSensorSeriesPointUseCase.kt (+56 LOC, new), ObserveSensorSeriesUseCase.kt (+17 LOC, new), ResetSensorSeriesUseCase.kt (+19 LOC, new). Decimation keeps every second point AND re-adds the newest when the thinning dropped it - with an even row count, keeping even indices alone would have evicted the tail and rewound the running total the next sample builds on, which the step's own test list ("the newest point survives decimation") requires. Removed the orphaned `count`/`countSeries` pair in the same step (Rule 20) - see the correction note on step 02.3. Dev log recorded.

---

### Step 02.5 - Add the decimation and migration tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/sensors/RecordSensorSeriesPointUseCaseTest.kt`, `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration46To47Test.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Write the unit test against a fake `SensorSeriesRepository` held in memory, asserting: a sample inside the throttle window is not appended; a sample outside it is; reaching `MAX_POINTS` halves the row count; the oldest point survives decimation; the newest point survives decimation; a sequence of `secondaryDelta` values accumulates into a running total; and the total restarts from zero after `clear`. Write the migration test following `AppDatabaseMigration45To46Test`, opening the 46 schema, running `MIGRATION_46_47` and asserting the new table and index exist and that a row written before the migration in an unrelated table is still readable after it.

**Why:**

Strategic §11.4 makes "the series survives leaving the desktop and restarting" a completion criterion, and the decimation rule is the one place where a silent off-by-one turns a trip meter into a moving window without any visible symptom.

**Verification:**

- `Glob` - both test files exist.
- `Grep` - `MAX_POINTS` referenced in the unit test rather than a literal 720.
- `Grep` - `MIGRATION_46_47` present in the migration test.
- `pwsh -NoProfile -File ./a.ps1 fu` - `RecordSensorSeriesPointUseCaseTest` passes. Pre-existing failures in unrelated classes are not this step's gate.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. Files: test/.../sensors/RecordSensorSeriesPointUseCaseTest.kt (+134 LOC, new), androidTest/.../db/AppDatabaseMigration46To47Test.kt (+59 LOC, new). Ran the class targeted rather than the whole suite - `check-standard-fast.ps1 -Mode Unit -Tests "*RecordSensorSeriesPointUseCaseTest"`, the documented per-class path (S1244) - and read the verdict off the freshly written XML, not the Gradle summary line: `tests=7 failures=0 errors=0`, written 23:12:14. The two production constants are bound through file-private `const val` aliases so the test cannot drift from the code it guards, and so the alias names stay SCREAMING_SNAKE without tripping ktlint's property-naming rule. The migration test is instrumented and therefore not run here - no device is attached this session. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 dq` -> `BUILD SUCCESSFUL in 1m 48s`, APK packaged. The packaging build is also what proves the Hilt graph resolved, which no compile-only check does.
- [x] `Grep` for `TODO(phase-02)` returns zero hits - expected: 0 | actual: 0.
- [x] `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/47.json` exists and is committed - `AppDatabaseSchemaExportTest` fails without it.
- [x] Dev log entries written - one per step through `post-change.ps1`, which is the repo convention (one entry per logical change, CLAUDE.md §12) rather than the one-per-file this line asked for. `post-change.ps1 -Files` writes a single changelog row for the whole set by design; the gates cover every file, the changelog names the first.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog_sync` ran inside every step's closure, last at 2516 records.
- [x] Phase-boundary audit run - no P0/P1. One P2 (concurrent recorders, written into the Handoff Notes as a Phase 04 obligation) and one P3 (SQLite variable ceiling, fixed inline in the DAO KDoc). See the audit section above.

---

## Handoff Notes to Next Phase

- Reset is `ResetSensorSeriesUseCase`, an operation on the series. A chart view must not clear rows itself, or the reset stops working the moment a second surface reads the same series.
- The series is bounded in rows (720) and unbounded in duration. A chart drawing it cannot assume a fixed sample interval - the gap between neighbouring points doubles at each decimation.
- Nothing writes to the series yet. Phase 04 connects `MotionReadingSource` to `RecordSensorSeriesPointUseCase`.
- **Exactly one recorder per series id, and Phase 04 owns that guarantee.** `RecordSensorSeriesPointUseCase` is a read-modify-write - read the series, decide, maybe decimate, append - with nothing serializing it, and it carries no scope annotation, so every injection point gets its own instance and a `Mutex` field in it would not be shared. Two desktop cells showing the same chart would therefore each run their own recorder against one series: the running distance total gains both deltas for one movement, and two decimations can interleave. Either let only one view record (and the others draw only), or move the read-modify-write behind the `@Singleton` repository. Found by the Phase 02 boundary audit; it is P2 today only because nothing calls the use case yet, and it becomes a live defect with Phase 04's first wiring.

---

## Phase-boundary audit (2026-08-06)

Run per CLAUDE.md §13 against this phase's `Files Touched`. Layers 1, 2 and 4 apply; Layer 3 does not - the phase registers no listener and holds no view.

- **Layer 4, Room - PASS, and verified rather than assumed.** The exported `47.json` matches `MIGRATION_46_47` statement for statement: `CREATE TABLE IF NOT EXISTS ... (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, seriesId TEXT NOT NULL, takenAtMillis INTEGER NOT NULL, primaryValue REAL NOT NULL, secondaryValue REAL)` and `CREATE INDEX IF NOT EXISTS index_sensor_series_point_seriesId_takenAtMillis ON ... (seriesId, takenAtMillis)`. That comparison is what stands in for `runMigrationsAndValidate` this session, since the migration test is instrumented and no device is attached. Every non-Flow DAO member is `suspend`, so no query can land on the main thread.
- **Layer 2, concurrency - one P2, recorded in the handoff note above.** No other suspend member holds state between calls.
- **Layer 1, architecture - PASS.** Layering holds (use case -> repository -> DAO); `SensorSeriesPointEntity` never appears outside `data/`; every file is far under the size budget; naming follows `VerbNounUseCase` / `NounRepository`.
- **P3, recorded not deferred:** `deleteOutsideKept` binds one SQLite variable per kept id - about 361 at `MAX_POINTS` 720, against SQLite's 999-variable ceiling. Raising `MAX_POINTS` past roughly 1996 would fail at run time rather than at compile time, so the bound is written into the DAO's KDoc where the query is.

---

## Rollback Plan

Reverting this phase requires reverting the schema bump together with the entity, the DAO accessor and the `DatabaseModule` registration in one commit - a build carrying version 47 without the migration routes every existing install into the database recovery path. The new table holds only derived sensor samples, so dropping it loses no user-authored data.

# Phase 03 - Source Catalog Data

**Strategic spec:** [`../S0565_internet-stream-playback.md`](../S0565_internet-stream-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (steps verified; `compileStandardDebugKotlin` passes 2026-06-21 - Room kapt validated @Database v33 + DAO + MIGRATION_32_33)
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Persist user stream sources: a Room `stream_sources` table (entity + DAO), a `MIGRATION_32_33`, the `@Database` version bump, and a `StreamSourceRepository` with its Hilt binding. Holds source metadata, the local pin-to-top order, and the favorite flag - independent of the global Favorites table.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Reviewed an existing entity/DAO pair (e.g. `FavoritesEntity.kt` + `FavoritesDao.kt`) and `Migration31To32.kt`. Current `@Database(version = 32)` in `AppDatabase.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration32To33.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ +6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/<existing RepositoryModule>.kt` | Modified | ≤ +6 |

---

## Steps

### Step 03.1 - `StreamSourceEntity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Entity(tableName = "stream_sources") data class StreamSourceEntity` with: `@PrimaryKey val id: String`, `val url: String`, `val title: String`, `val mediaKind: String` (AUDIO / VIDEO / RTSP - drives launch routing), `val sourceOrigin: String` (MANUAL / IMPORTED), `val sortIndex: Int` (lower = higher in list; pin-to-top decrements), `val pinned: Boolean = false`, `val addedAt: Long`, `val lastPlayedAt: Long? = null`. No credentials column in iteration 1 (public streams). Add a unique index on `url` to support import de-duplication.

**Verification:**

- `Glob` - file exists.
- `Grep` - `@Entity(tableName = "stream_sources")` and `data class StreamSourceEntity` present.
- `Grep` - `@PrimaryKey` and `indices` (unique index on url) present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Files: data/local/db/StreamSourceEntity.kt (New). String PK, unique url index, mediaKind/sourceOrigin/sortIndex/pinned fields.

---

### Step 03.2 - `StreamSourceDao`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `@Dao interface StreamSourceDao` with: `@Query("SELECT * FROM stream_sources ORDER BY pinned DESC, sortIndex ASC, addedAt DESC") fun observeAll(): Flow<List<StreamSourceEntity>>`; `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(source: StreamSourceEntity): Long` (import de-dup by url unique index); `@Upsert suspend fun upsert(source: StreamSourceEntity)`; `@Delete suspend fun delete(source: StreamSourceEntity)`; `@Query("SELECT MIN(sortIndex) FROM stream_sources") suspend fun minSortIndex(): Int?` (for pin-to-top). Use `kotlinx.coroutines.flow.Flow`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `@Dao` + `interface StreamSourceDao` present.
- `Grep` - `observeAll(): Flow<List<StreamSourceEntity>>` and `minSortIndex` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Files: data/local/db/StreamSourceDao.kt (New). observeAll Flow, insertIgnore (de-dup), upsert/delete, minSortIndex, pin, markPlayed.

---

### Step 03.3 - Migration 32 -> 33 + version bump + DAO registration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration32To33.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `val MIGRATION_32_33 = object : Migration(32, 33)` whose `migrate()` runs `CREATE TABLE IF NOT EXISTS stream_sources (...)` matching the entity columns exactly, then `CREATE UNIQUE INDEX IF NOT EXISTS index_stream_sources_url ON stream_sources(url)`. In `AppDatabase.kt`: bump `@Database(version = 32 -> 33)`, add `StreamSourceEntity::class` to the entities array, add `abstract fun streamSourceDao(): StreamSourceDao`, and register `MIGRATION_32_33` wherever the migration list is assembled (follow how `Migration31To32` is registered). Never rename prior migrations.

**Verification:**

- `Glob` - `Migration32To33.kt` exists.
- `Grep` - `Migration(32, 33)` and `CREATE TABLE IF NOT EXISTS stream_sources` present.
- `Grep` - `version = 33` in `AppDatabase.kt`; `streamSourceDao()` and `StreamSourceEntity::class` present; `MIGRATION_32_33` referenced in the migration registration.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Files: Migration32To33.kt (New), AppDatabase.kt (version 33 + entity + DAO), core/di/DatabaseModule.kt (import + addMigrations). Migration is additive (new table + unique url index).

---

### Step 03.4 - `StreamSourceRepository` + Hilt binding

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt`, the existing repository Hilt module
**Depends on:** Step 03.3

**Prompt for developer:**

> Create `class StreamSourceRepository @Inject constructor(private val dao: StreamSourceDao)` exposing: `fun observeSources(): Flow<List<StreamSourceEntity>>`, `suspend fun add(source: StreamSourceEntity)`, `suspend fun addAllIgnoringDuplicates(sources: List<StreamSourceEntity>): Int` (returns count actually inserted, preserving local order via `insertIgnore`), `suspend fun pinToTop(id: String)` (set `pinned=true`, `sortIndex = (minSortIndex() ?: 0) - 1`), `suspend fun remove(source: StreamSourceEntity)`, `suspend fun markPlayed(id: String, atMillis: Long)`. Bind it in the existing repository Hilt module (locate via the module that already provides `FavoritesDao`-backed repos). If the repo is constructor-injectable and consumed by interface elsewhere keep it concrete; otherwise add a `@Provides`/`@Binds` matching the surrounding convention.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class StreamSourceRepository` + `pinToTop` + `addAllIgnoringDuplicates` present.
- `Grep` - `Log.d(` returns zero hits in the new files (Timber only).

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Files: data/repository/StreamSourceRepository.kt (New, @Singleton @Inject), core/di/DatabaseModule.kt (provideStreamSourceDao + import). Repo concrete + constructor-injectable; no binding module needed.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`); Room kapt validates `@Database` version 33, the new DAO, and `MIGRATION_32_33` at compile time. If a Room `exportSchema` JSON dir exists, a `33.json` is produced.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added.
- [ ] `dev/CATALOG/app_v2.jsonl` will be regenerated in Phase 08 (new public classes: entity, DAO, repository).

---

## Handoff Notes to Next Phase

- `StreamSourceRepository` is the single data entry point for Phase 05 use cases.
- `pinToTop` is the local-favorite mechanism (strategic §3.3 Favorites model) - NOT the global `FavoritesDao`.
- `addAllIgnoringDuplicates` de-dups by normalized url unique index, preserving local order (research §6 item 4).

---

## Rollback Plan

Revert phase commit(s). Migration 32->33 is additive (new table only); a forward-only revert before any release needs no down-migration. Drop the `stream_sources` table if a device already ran the migration.

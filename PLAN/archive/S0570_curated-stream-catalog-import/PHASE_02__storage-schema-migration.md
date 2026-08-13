# Phase 02 - Storage schema + migration

**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done (backend; build pending central build)
**Depends on:** -
**Blocks:** Phase 03, Phase 05

## Objective

Persist the filterable catalog fields (`category`, `topic`, `language`) on the stream source and add
the DAO surface for catalog-origin merge/prune. Schema bump 33 -> 34 with an explicitly-named
migration (Room schema change is allowed here because the migration class is named - strategic gate).

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt` | Modified | <= +6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration33To34.kt` | New | <= 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | <= +1 (version) |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | <= +2 (import + register) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | Modified | <= +20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` | Modified | <= +25 |

## Steps

### Step 02.1 - Entity columns

> Add to `StreamSourceEntity`: `val category: String? = null`, `val topic: String? = null`, `val language: String? = null`. Nullable, default null - manual/imported rows simply leave them null. Keep the existing unique `url` index.

**Verification:** `Grep` - `val category`, `val topic`, `val language` present. `.\a.ps1 fk` (will fail until version bump + migration - run after 02.3).

### Step 02.2 - `Migration33To34`

> Create `Migration33To34.kt` mirroring `Migration32To33.kt`: `val MIGRATION_33_34 = object : Migration(33, 34) { override fun migrate(db) { db.execSQL("ALTER TABLE stream_sources ADD COLUMN category TEXT"); ...topic; ...language } }`. Three nullable TEXT columns. Idempotent not required (forward-only ALTER on a v33 table).

**Verification:** `Grep` - `val MIGRATION_33_34 = object : Migration(33, 34)`; three `ALTER TABLE stream_sources ADD COLUMN` present.

### Step 02.3 - Version bump + registration

> In `AppDatabase.kt` change `version = 33` -> `version = 34`. In `core/di/DatabaseModule.kt` import `MIGRATION_33_34` and add it to the `.addMigrations(..)` list next to `MIGRATION_32_33`.

**Verification:** `Grep` - `version = 34` in AppDatabase.kt; `MIGRATION_33_34` in DatabaseModule.kt. `.\a.ps1 fk` PASS (Room kapt validates the schema).

### Step 02.4 - DAO + repository catalog surface

> `StreamSourceDao`: add `@Query("SELECT * FROM stream_sources WHERE sourceOrigin = 'CATALOG'") suspend fun catalogSources(): List<StreamSourceEntity>` and `@Query("DELETE FROM stream_sources WHERE sourceOrigin = 'CATALOG' AND url NOT IN (:keepUrls)") suspend fun deleteCatalogNotIn(keepUrls: List<String>)` and `@Query("UPDATE stream_sources SET title=:title, mediaKind=:mediaKind, category=:category, topic=:topic, language=:language WHERE url=:url AND sourceOrigin='CATALOG'") suspend fun updateCatalogByUrl(url, title, mediaKind, category, topic, language)`. `StreamSourceRepository`: add `suspend fun mergeCatalog(entries: List<StreamSourceEntity>): CatalogMergeResult` that insertIgnore-new, updateCatalogByUrl-existing, then deleteCatalogNotIn(current urls) - never touches non-CATALOG rows; returns added/updated/removed counts. Pinned/order on existing CATALOG rows is preserved (update touches only metadata columns, not sortIndex/pinned).

**Verification:** `Grep` - `catalogSources`, `deleteCatalogNotIn`, `updateCatalogByUrl`, `mergeCatalog` present. `.\a.ps1 fk` PASS.

**Status:** `[x]` done (all steps)

**Step Log:**
- 02.1: added nullable `category`/`topic`/`language` to `StreamSourceEntity` (defaults null), unique url index untouched.
- 02.2: `Migration33To34.kt` created mirroring `Migration32To33.kt` - `val MIGRATION_33_34 = object : Migration(33, 34)` with three `ALTER TABLE stream_sources ADD COLUMN ... TEXT`.
- 02.3: `AppDatabase` `version = 34`; `DatabaseModule` imports + registers `MIGRATION_33_34` next to `MIGRATION_32_33`.
- 02.4: DAO `catalogSources`/`deleteCatalogNotIn`/`updateCatalogByUrl` added; `StreamSourceRepository.mergeCatalog` + `CatalogMergeResult` added (insert-new / update-existing-CATALOG / prune-vanished; user rows untouched; sortIndex/pinned preserved by omission from the UPDATE SET).
- Self-verified via Grep for every symbol above + `version = 34`.

## Phase Done Criteria

- [x] Steps 02.1-02.4 done.
- [ ] `.\a.ps1 fk` PASS (central build, orchestrator - Room schema hash validates v34 + migration).
- [x] Migration is forward-only nullable ALTERs; no data loss; non-CATALOG rows untouched by merge/prune queries.

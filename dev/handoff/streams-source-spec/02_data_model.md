# Streams Source Spec - 02 - Data Model (persistence, DAO, repository, use cases)

Part of the FastMediaSorter "Трансляции" (Streams) source-documentation set. This file documents the
Android app's on-device persistence and domain layer for the Streams feature: the `stream_sources` Room
table, its migrations, every DAO query, the repository merge/prune algorithm, the two preference stores,
the domain enums, and every use case. It describes the **implemented Android system** as-is; it does not
prescribe a Windows design.

Companion files: `01_delivery_contract.md` (the reusable bank format), `03_catalog_format.md` (CSV +
classifier), `04_favicon_atlas.md` (atlas), `05..07` (UI, player, entry points), `08` (offline build
pipeline).

All facts cite `path:line` from the working tree (root `p:\ANDROID\FastMediaSorter_mob_v2`). Where a fact
is a delivery/consumption contract (i.e. a Windows port must match it), it is marked **[CONTRACT]**.
Where it is an Android-internal implementation detail, it is marked *(impl detail)*.

---

## 1. Layer map

```
UI (StreamsActivity / StreamsViewModel, MainActivity panels, PlayerActivity)
  -> domain/usecase/streams/*   (14 use cases, plain @Inject classes, operator fun invoke)
       -> data/repository/StreamSourceRepository        (single data entry point, S0565)
            -> data/local/db/StreamSourceDao            (Room DAO, ~20 queries)
                 -> stream_sources                      (Room/SQLite table, schema v41)
       -> data/repository/settings/StreamsSettingsStore (persistent user settings, DataStore)
       -> data/repository/settings/StreamsSessionStore  (ephemeral session memory, separate DataStore)
       -> data/repository/streams/FaviconAtlasStore     (favicon sidecar; see 04_favicon_atlas.md)
```

Data-flow rule (project-wide): `UI -> ViewModel -> UseCase -> Repository -> DataSource`. All Streams
data-layer classes are compiled unconditionally into `src/main` for every flavor; there is no
`BuildConfig.SUPPORT_STREAMS` guard inside the data layer (gating is applied only at the UI entry-point
layer - see `07_entrypoints_and_gating.md`). A `lite`/`photos` build still ships a working
`stream_sources` table and DAO, just with no UI entry point wired to it.

---

## 2. `stream_sources` table

### 2.1 Schema version 41 snapshot (Room-exported baseline) **[CONTRACT for on-device state; the bank itself is CSV, see 01/03]**

Source of truth: `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/41.json:1453-1557`
(Room's own schema-validation export, gated by `exportSchema = true` in `AppDatabase.kt:39`).

```sql
CREATE TABLE IF NOT EXISTS `stream_sources` (
    `id`                TEXT    NOT NULL,
    `url`               TEXT    NOT NULL,
    `title`             TEXT    NOT NULL,
    `mediaKind`         TEXT    NOT NULL,
    `sourceOrigin`      TEXT    NOT NULL,
    `sortIndex`         INTEGER NOT NULL,
    `pinned`            INTEGER NOT NULL,
    `addedAt`           INTEGER NOT NULL,
    `lastPlayedAt`      INTEGER,
    `category`          TEXT,
    `topic`             TEXT,
    `language`          TEXT,
    `country`           TEXT,
    `lastPlayOutcome`   TEXT,
    `lastPlayOutcomeAt` INTEGER,
    PRIMARY KEY(`id`)
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_stream_sources_url` ON `stream_sources`(`url`);
```

15 columns, primary key `id` (`autoGenerate = false` - the app supplies the UUID string itself), unique
constraint on `url`, no foreign keys.

### 2.2 Column reference

| Column | SQL type | Nullable | Meaning |
|---|---|---|---|
| `id` | TEXT | no (PK) | `UUID.randomUUID().toString()`, generated app-side on every insert. **Not stable** across re-imports; never derived from a catalog key. |
| `url` | TEXT | no | de-duplication key (unique index) and the playback target |
| `title` | TEXT | no | display name |
| `mediaKind` | TEXT | no | `"AUDIO"` \| `"VIDEO"` \| `"RTSP"` - drives launch routing (inline audio vs fullscreen player). See `03` classifier and `06` routing. |
| `sourceOrigin` | TEXT | no | `"MANUAL"` \| `"IMPORTED"` \| `"CATALOG"` (section 6) |
| `sortIndex` | INTEGER | no | lower = higher in list; pin-to-top sets `min(sortIndex) - 1` |
| `pinned` | INTEGER (bool) | no | feature-local favorite flag, independent of the app's global favorites table |
| `addedAt` | INTEGER (epoch ms) | no | insert wall-clock time |
| `lastPlayedAt` | INTEGER (epoch ms) | yes | set by `markPlayed()`; **not currently called by any use case** *(impl detail / dead path, section 9)* |
| `category` | TEXT | yes | populated only for `sourceOrigin = CATALOG` rows |
| `topic` | TEXT | yes | CATALOG-only |
| `language` | TEXT | yes | CATALOG-only; lowercase language name(s), comma-separated inside one cell |
| `country` | TEXT | yes | CATALOG-only; ISO 3166-1 alpha-2 (S0761) |
| `lastPlayOutcome` | TEXT | yes | `null` = never tried (amber), `"OK"` = green, `"FAIL"` = red, `"UNKNOWN"` = probe-only (never a real failed play). See `RecordStreamPlayOutcomeUseCase`, section 8. |
| `lastPlayOutcomeAt` | INTEGER (epoch ms) | yes | timestamp of the last outcome write |

Entity source: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt`. Note:
the inline KDoc on `sourceOrigin` says "MANUAL / IMPORTED" - **stale**; the true value set is 3 (CATALOG
was added by S0570 without updating this one comment).

### 2.3 Migration history (only migrations that touch `stream_sources`)

`AppDatabase.kt:36` current `version = 41`. Full migration chain registered in
`core/di/DatabaseModule.kt:74-115` (one `.addMigrations(...)` call, no `fallbackToDestructiveMigration`).

| Migration | Ticket | Effect |
|---|---|---|
| 32 -> 33 | S0565 | **Creates** `stream_sources` (`id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt, lastPlayedAt`) + unique index on `url` |
| 33 -> 34 | S0570 | `ADD COLUMN category TEXT`, `topic TEXT`, `language TEXT` (nullable, no backfill) |
| 34 -> 35 | S0593 | `ADD COLUMN lastPlayOutcome TEXT`, `lastPlayOutcomeAt INTEGER` |
| 36 -> 37 | S0761 | `ADD COLUMN country TEXT` |

Migrations 35->36 and 37->38..40->41 do not touch `stream_sources`. The creating SQL (migration 32->33,
`Migration32To33.kt:10-31`) matches the exported schema above. The three `ALTER TABLE .. ADD COLUMN`
migrations are forward-only with no `IF NOT EXISTS` guard.

---

## 3. `StreamSourceDao` - every query

Source: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt`.

**Observers / reads**
- `observeAll(): Flow<List<StreamSourceEntity>>` - `SELECT * FROM stream_sources ORDER BY pinned DESC, sortIndex ASC, addedAt DESC`. The full-list reactive feed for the Streams screen: pinned-first, then local order, then recency.
- `observePinned(): Flow<List<StreamSourceEntity>>` - `... WHERE pinned = 1 ORDER BY sortIndex ASC, addedAt DESC`. A dedicated query (not a filter over `observeAll`) so an unrelated catalog write does not re-emit to the main-window pinned panel (S0756).
- `pinnedSnapshot(): List<StreamSourceEntity>` (suspend) - same SQL as `observePinned`, one-shot; input to the reorder-move computation (S0938).
- `getByUrl(url): StreamSourceEntity?` - `... WHERE url = :url LIMIT 1`. Resolves the stored row behind a failing playback URL (S0581).
- `getById(id): StreamSourceEntity?` - `... WHERE id = :id LIMIT 1`. Launcher-shortcut resolution (S0404).
- `getMediaKindById(id): String?` - `SELECT mediaKind FROM ... WHERE id = :id LIMIT 1`. Lightweight lookup for the play statistics split (S0654).
- `minSortIndex(): Int?` - `SELECT MIN(sortIndex) FROM stream_sources`. Used by `pinToTop` to compute `min - 1`.

**Writes**
- `insertIgnore(source): Long` - `@Insert(onConflict = IGNORE)`; returns `-1` on a `url` unique-index collision. Used by both the playlist-import de-dup path and the catalog-merge insert path.
- `upsert(source)` - `@Upsert`; used by `add()` (manual single add).
- `delete(source)` - `@Delete` (full-row delete, any origin).
- `setSortIndex(id, sortIndex)` - single-row order rewrite during a pin reorder renumber.
- `pin(id, newSortIndex)` - `UPDATE ... SET pinned = 1, sortIndex = :newSortIndex WHERE id = :id`.
- `unpin(id)` - `UPDATE ... SET pinned = 0 WHERE id = :id` (S0770: drop pin only; row survives).
- `updateUserFields(id, url, title, mediaKind)` - `UPDATE ... SET url=:url, title=:title, mediaKind=:mediaKind WHERE id=:id AND sourceOrigin='MANUAL'` (S0660 in-place edit, SQL-scoped to MANUAL rows; `pinned`/`sortIndex`/outcome columns untouched).
- `markPlayed(id, atMillis)` - `UPDATE ... SET lastPlayedAt = :atMillis WHERE id = :id`. *(Present but not invoked by any use case - dead path.)*
- `markPlayOutcome(id, outcome, atMillis)` - `UPDATE ... SET lastPlayOutcome = :outcome, lastPlayOutcomeAt = :atMillis WHERE id = :id` (S0593 status bullet).
- `clearAllPlayOutcomes()` - `UPDATE ... SET lastPlayOutcome = NULL, lastPlayOutcomeAt = NULL` (S0659; no rows deleted).

**Catalog-scoped helpers (all SQL-guarded to `sourceOrigin='CATALOG'`)**
- `catalogSources(): List<StreamSourceEntity>` - `... WHERE sourceOrigin = 'CATALOG'`; merge-delta input.
- `deleteCatalogByUrls(urls: List<String>)` - `DELETE FROM ... WHERE sourceOrigin = 'CATALOG' AND url IN (:urls)` (S0821 bounded-batch prune; never touches user rows).
- `updateCatalogByUrl(url, title, mediaKind, category, topic, language, country)` - `UPDATE ... SET title=..., mediaKind=..., category=..., topic=..., language=..., country=... WHERE url=:url AND sourceOrigin='CATALOG'` (S0570 refresh in place; `sortIndex`/`pinned` NOT in the SET clause, so local order/pin survive re-import).

The catalog-scoping is enforced **in SQL**, not only in Kotlin - a mis-routed call cannot mutate a
non-CATALOG row.

---

## 4. Repository - `StreamSourceRepository`

Source: `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` (S0565,
the single data entry point for Streams; wraps `StreamSourceDao`).

### 4.1 `mergeCatalog` - the catalog sync algorithm *(impl detail; the observable result is what matters)*

`mergeCatalog(entries: List<StreamSourceEntity>): CatalogMergeResult`, whole method in one
`db.withTransaction` (S0732 atomicity - a process kill mid-merge cannot leave a half-synced catalog).

```
existingCatalogUrls = { url of every sourceOrigin='CATALOG' row }
newUrls             = { url of every incoming entry }
for entry in entries:
    if entry.url in existingCatalogUrls:
        updateCatalogByUrl(...)          # refresh title/mediaKind/category/topic/language/country
        updated++                        # sortIndex/pinned/outcome NOT touched
    else if insertIgnore(entry) != -1:   # -1 = url owned by a MANUAL/IMPORTED row -> leave it
        added++
urlsToDelete = existingCatalogUrls - newUrls
for chunk in urlsToDelete.chunked(900): deleteCatalogByUrls(chunk)   # S0821
return CatalogMergeResult(added, updated, removed = urlsToDelete.size)
```

Key invariants:
- A URL collision with an existing MANUAL/IMPORTED row **always** favors the user row (the catalog insert
  is silently ignored). A catalog import can never overwrite or hide a hand-created/imported row.
- Local order (`sortIndex`) and pin state survive a catalog re-import (not in the update SET clause).
- Prune is CATALOG-scoped only; vanished CATALOG rows are removed, user rows never.
- `SQLITE_IN_CLAUSE_LIMIT = 900` chunking (S0821) avoids SQLite's 999 bind-variable ceiling that once
  crashed large imports on API 29. The delete delta is computed in memory, then issued as N bounded
  `DELETE ... url IN (:urls)` calls, all inside the one transaction.

`CatalogMergeResult(added: Int, updated: Int, removed: Int)` is surfaced to the caller as
`CatalogImportResult.Success(added, updated, removed)` (see `01`/`03`).

Verified by `StreamSourceCatalogMergeTest.kt` (Robolectric + in-memory Room): a 1500-row import
(added=1500), a full 1100-row replacement of 1500 (added=1100, removed=1500), and manual-row-wins-collision
(added=0, removed=0, the MANUAL row untouched).

### 4.2 Other repository methods
- `observeSources()` / `observePinnedSources()` - passthroughs to the DAO observers.
- `add(source)` -> `dao.upsert`; `remove(source)` -> `dao.delete`.
- `addAllIgnoringDuplicates(sources): List<...>` - one transaction, `insertIgnore` per row, duplicates by
  `url` silently skipped; used by playlist import (S0732 atomicity, mirrors `mergeCatalog`).
- `pinToTop(id)` -> `pin(id, minSortIndex - 1)`; `unpin(id)`; `reorderPinned(orderedIds)` renumbers the
  whole pinned set contiguously `0..N-1` in one transaction (S0938).
- `updateUserFields(...)`, `markPlayOutcome(...)`, `clearPlayOutcomes()`, `getByUrl(url)`.
- `markPlayed(id, atMillis)` exists but has no calling use case *(dead path)*.

---

## 5. `sourceOrigin` - provenance

Three raw string literals (no Kotlin enum); the column is a plain `String`.

| Value | Set by | Semantics |
|---|---|---|
| `MANUAL` | `AddStreamSourceUseCase` (`:25`) | user typed/pasted a single URL. **Only** MANUAL rows are editable (`UpdateStreamSourceUseCase` / `updateUserFields`). |
| `IMPORTED` | `ImportStreamPlaylistUseCase` (`:47`) | row came from a user-supplied `.m3u`/`.m3u8` playlist URL (see `03`). |
| `CATALOG` | `ImportStreamCatalogUseCase` (`:73`) | row came from the curated `stream-catalog.zip` (see `01`/`03`). **Only** CATALOG rows are eligible for `mergeCatalog` update/prune and get `category/topic/language/country`. |

Mutability is enforced in SQL (`WHERE ... AND sourceOrigin='MANUAL'|'CATALOG'`), not only in application
logic - defense in depth.

---

## 6. Preference stores

### 6.1 `StreamsSettingsStore` (persistent user settings; DataStore of Preferences, mirrored into `AppSettings`)

Source: `data/repository/settings/StreamsSettingsStore.kt`.

| Preference key | `AppSettings` field | Type | Default | Meaning |
|---|---|---|---|---|
| `enable_streams` | `enableStreams` | Boolean | `false` | S0575 Streams master switch (a device-profile preset may raise it - see `07`) |
| `streams_default_sort` | `streamsDefaultSort` | `StreamDefaultSort` | `NAME` | seeds the list sort on first open / after cleared session |
| `streams_default_media_filter` | `streamsDefaultMediaFilter` | `StreamMediaTypeFilter` | `ALL` | seeds the default media-kind facet |
| `streams_catalog_refresh_policy` | `streamsCatalogRefreshPolicy` | `StreamsCatalogRefreshPolicy` | `ON_OPEN` | automatic-refresh behavior on screen open |
| `show_streams_panel_main_window` | `showStreamsPanelInMainWindow` | Boolean | `false` | S0756 pinned-channels panel on the main window; effective only when `enableStreams` and the flavor ships Streams |

Enum values are stored by `.name`; decode uses each enum's `fromName(...)` with a fallback to its DEFAULT.

### 6.2 `StreamsSessionStore` (ephemeral session memory; SEPARATE DataStore file `"streams_session"`)

Source: `data/repository/settings/StreamsSessionStore.kt`. Deliberately isolated from the main settings
DataStore.

| Key | Field | Type | Default | Meaning |
|---|---|---|---|---|
| `last_sort` | `lastSort` | String? | null | last-used sort mode (raw enum name) |
| `last_media_filter` | `lastMediaFilter` | String? | null | last-used media-kind filter |
| `last_category` | `lastCategory` | String? | null | S0697 facet; the key is **removed** (not set null) when cleared |
| `last_language` | `lastLanguage` | String? | null | S0697 facet |
| `last_country` | `lastCountry` | String? | null | S0697 facet |
| `last_pinned_only` | `lastPinnedOnly` | Boolean? | null | pinned-only toggle |
| `last_catalog_refresh_at` | `lastCatalogRefreshAt` | Long | 0 (never) | last catalog-refresh timestamp; throttle input for the refresh policy |
| `last_display_mode` | `lastDisplayMode` | String? | null | list/grid display-mode memory (S0699) |
| `last_scroll_position` | `lastScrollPosition` | Int? | null (top) | first-visible adapter position, restored on next open (S0699) |

The free-text **search query is intentionally NOT persisted** (S1054) - it resets on every screen open.
Write API: `read()`, `writeFilterState(...)` (facets each SET or explicitly removed by nullability),
`writeCatalogRefreshAt(...)`, `writeDisplayMode(...)`, `writeScrollPosition(...)`.

---

## 7. Domain enums

Each enum has `companion object { val DEFAULT; fun fromName(name: String?) = values().firstOrNull { it.name == name } ?: DEFAULT }`.

- `StreamDefaultSort { NAME, TOPIC, LANGUAGE, COUNTRY, RECENT }` - DEFAULT `NAME` (`RECENT` = `addedAt DESC`).
- `StreamMediaTypeFilter { ALL, AUDIO, VIDEO }` - DEFAULT `ALL`. Note: **no RTSP value**; how an RTSP row
  is bucketed under the `VIDEO` filter is a UI/ViewModel decision (see `05`).
- `StreamsCatalogRefreshPolicy { MANUAL, ON_OPEN, PERIODIC_WIFI }` - DEFAULT `ON_OPEN`.
  - `MANUAL` - refresh only via the toolbar import action.
  - `ON_OPEN` (shipped default) - on screen open, offer a **dismissible** refresh suggestion, throttled by
    `lastCatalogRefreshAt`; never a silent download.
  - `PERIODIC_WIFI` - opportunistic auto-refresh on open, WiFi-only, ~daily; **not** a WorkManager job
    (fires on-open only, with a WiFi gate, no dismissible prompt).

`StreamMediaKindClassifier` (AUDIO/VIDEO/RTSP from a URL) is documented in `03_catalog_format.md`.

---

## 8. Use cases (`domain/usecase/streams/`, 14 files)

All are plain `@Inject constructor()` classes exposing `operator fun invoke(...)`.

| Use case | Signature | Behavior |
|---|---|---|
| `ObserveStreamSourcesUseCase` | `(): Flow<List<StreamSourceEntity>>` | passthrough to `observeSources()` (pinned-first order) |
| `ObservePinnedStreamSourcesUseCase` | `(): Flow<List<StreamSourceEntity>>` | `observePinnedSources().distinctUntilChanged()` (S0756 main-window panel) |
| `AddStreamSourceUseCase` | `suspend (url, title?): AddResult` | validate scheme (`isSupportedScheme`); derive title from URL host if blank; build MANUAL entity (fresh UUID, sortIndex 0); `add()`; record `StatsEvent.StreamAdded`. Returns `Success`/`InvalidUrl`; `Duplicate` is declared but never returned. |
| `UpdateStreamSourceUseCase` | `suspend (source, url, title?): UpdateResult` | reject non-MANUAL (`NotEditable`); validate scheme; re-derive `mediaKind` from the new URL; `updateUserFields(...)` |
| `RemoveStreamSourceUseCase` | `suspend (source)` | `remove(source)` = hard delete (any origin) |
| `PinStreamSourceUseCase` | `suspend (id)` | `pinToTop(id)` = `sortIndex = min - 1`, `pinned = 1` |
| `UnpinStreamSourceUseCase` | `suspend (id)` | `unpin(id)` = `pinned = 0` only; row + catalog metadata kept |
| `ReorderPinnedStreamUseCase` (+ `enum PinnedStreamMove { UP, DOWN, TO_TOP }`) | `suspend (id, move)` | read `pinnedSnapshot()`, compute new index in memory, no-op on edge, else `reorderPinned(orderedIds)` renumbers the whole pinned set `0..N-1` |
| `GetStreamSourceByUrlUseCase` | `suspend (url): StreamSourceEntity?` | `getByUrl(url)`; null lets the player fall back to generic error handling (S0581) |
| `RecordStreamPlayOutcomeUseCase` | `suspend (id, ok)` + `suspend recordProbe(id, reachable)` | `invoke` writes OK/FAIL for a real play; on `ok=true` also records `StatsEvent.StreamPlayed(kind)`. `recordProbe` writes OK/UNKNOWN (never FAIL) for a reachability probe / grid-frame capture (S0700: red is reserved for a real failed play). Constants: `OUTCOME_OK="OK"`, `OUTCOME_FAIL="FAIL"`, `OUTCOME_UNKNOWN="UNKNOWN"`. |
| `ClearStreamPlayOutcomesUseCase` | `suspend ()` | `clearPlayOutcomes()` = null out both outcome columns on every row (no deletes) |
| `ImportStreamCatalogUseCase` | `suspend (): CatalogImportResult` | see `01`/`03` |
| `ImportStreamPlaylistUseCase` | `suspend (listUrl): ImportResult` | see `03` |
| `StreamMediaKindClassifier` | `isSupportedScheme(url)`, `classify(url)` | see `03` |

So `lastPlayOutcome` has a 4-value practical domain: `null` (never tried), `"OK"`, `"FAIL"`, `"UNKNOWN"`
(probe-only).

---

## 9. Notable asymmetries / dead paths (facts, not recommendations)

- `ImportStreamPlaylistUseCase.download()` uses the shared `OkHttpClient` with **no `callTimeout`
  override**, unlike `ImportStreamCatalogUseCase` (which adds a 30s overall deadline). The playlist path's
  only ceiling is the shared client's per-phase 10s connect/read/write timeouts.
- `AddStreamSourceUseCase.AddResult.Duplicate` is declared but never returned (a single upserting add
  cannot detect it there).
- `StreamSourceDao.markPlayed` / `repository.markPlayed` (sets `lastPlayedAt`) exist but have no caller.
- `StreamMediaTypeFilter` has 3 values (ALL/AUDIO/VIDEO) while `mediaKind` has 3 different values
  (AUDIO/VIDEO/RTSP); RTSP-under-VIDEO bucketing is a UI-layer decision (see `05`).
- Ten catalog-only CSV fields (`protocol`, `format`, `bitrate`, `is_live`, `https`, `homepage`,
  `source_kind`, `license_note`, `notes`, `confidence`) are parsed but never persisted to the entity (see
  `03`); `homepage` additionally feeds the offline favicon packer (see `08`).

---

## 10. Test coverage (as-is)

Well covered: `StreamCatalogCsvParser` (17 cases), `StreamSourceRepository.mergeCatalog` (3 cases),
`FaviconAtlasStore` (6 cases incl. `extractCatalog` zip-walk). Untested: `ImportStreamCatalogUseCase`
full `invoke()`, `M3uPlaylistParser`, `ImportStreamPlaylistUseCase`, the settings/session stores, the
enums, and 9 of the 14 use cases (only `UpdateStreamSourceUseCase` is indexed with a test).

---

## 11. Ticket index for this file

S0565 (table create), S0570 (catalog columns + merge), S0575 (master toggle), S0581 (getByUrl), S0593
(play-outcome columns), S0654 (kind lookup), S0659 (settings defaults + session store + clear marks),
S0660 (manual edit), S0697 (facet session), S0699 (display-mode/scroll session), S0700 (probe outcome
UNKNOWN), S0732 (transactional merge), S0756 (pinned panel), S0761 (country column), S0770 (unpin vs
delete), S0821 (chunked prune), S0938 (pinned reorder), S1054 (search not persisted).

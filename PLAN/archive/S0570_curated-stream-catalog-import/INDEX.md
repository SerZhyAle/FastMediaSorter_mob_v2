# S0570 - Tactical Plan: Curated stream catalog import

**Strategic spec:** [`../S0570_curated-stream-catalog-import.md`](../S0570_curated-stream-catalog-import.md)
**Status:** Tactical
**Related:** S0565 (base "Трансляции" feature)

One-tap download/update of the official FastMediaSorter curated stream catalog (CSV-in-zip,
GitHub Release asset) from the existing "Import list" toolbar action, plus topic/language storage and
a flat list with filter, sort and search. The catalog data + maintenance liveness script already
exist (`delivery/stream-catalog/`, `scripts/stream_catalog/check-liveness.ps1`).

## Decisions carried from strategic §3.3 / Quiz (2026-06-21)

- Hosting: GitHub Release asset zip, fixed name `stream-catalog.zip` (contains `streams.csv`), not SHA-pinned, app always fetches latest.
- "Import list" button: chooser - "Update FastMediaSorter catalog" (primary) + "Import from URL" (existing manual `.m3u`).
- Stored filterable fields: rubric (`category`) + `topic` + `language` + `mediaKind`. Extended fields not persisted in iteration 1.
- List presentation: flat list, topic/language chips per row, top filter + sort + search; sectioned grouping deferred.
- Update semantics: catalog rows tagged `sourceOrigin = CATALOG`; re-import adds new + removes catalog rows that vanished from the catalog, never touching MANUAL/IMPORTED or pinned entries.
- Catalog origin reuses the existing `sourceOrigin` column with a new value `CATALOG` (no separate origin column).

## Phases

- [PHASE_01__catalog-csv-parser.md](PHASE_01__catalog-csv-parser.md) - Done. RFC-4180 CSV parser for the catalog format.
- [PHASE_02__storage-schema-migration.md](PHASE_02__storage-schema-migration.md) - Done. add `category`/`topic`/`language` columns; `Migration33To34`; `@Database` v34; DAO catalog queries.
- [PHASE_03__catalog-import-usecase.md](PHASE_03__catalog-import-usecase.md) - Done. download zip, unzip, parse, merge (origin=CATALOG, prune vanished catalog rows).
- [PHASE_04__import-chooser-ux.md](PHASE_04__import-chooser-ux.md) - Implemented. "Import list" chooser (catalog vs URL); ViewModel `onImportCatalog`/`CatalogUpdated`; strings. Central build pending.
- [PHASE_05__list-filter-sort-search.md](PHASE_05__list-filter-sort-search.md) - Implemented. flat list filter/sort/search; topic/language chips; portrait + landscape parity. Central build pending.
- [PHASE_06__docs-strings-cleanup.md](PHASE_06__docs-strings-cleanup.md) - localisation parity, settings/docs sync, catalog/dev-log, ALL_FEATURES.

## Dependency order

01 (parser) and 02 (schema) are independent -> 03 needs 01+02 -> 04 needs 03 -> 05 needs 02 (filter fields) + 04 (list intact) -> 06 last.

## Build anchors (current state, verified 2026-06-21)

- `@Database(version = 33)` in `data/local/db/AppDatabase.kt`; newer migrations are separate files (e.g. `Migration32To33.kt` -> `val MIGRATION_32_33`) registered in `core/di/DatabaseModule.kt` `.addMigrations(..)`.
- `StreamSourceEntity` (id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt, lastPlayedAt) - unique index on `url`.
- `StreamSourceDao`: observeAll / insertIgnore / upsert / delete / minSortIndex / pin / markPlayed.
- `StreamSourceRepository.addAllIgnoringDuplicates(..)` returns inserted count.
- `ImportStreamPlaylistUseCase` injects `OkHttpClient` (reuse for the catalog download) + `StreamMediaKindClassifier`.
- `StreamsViewModel` exposes onAdd/onImport/onPin/onRemove + `StreamsUiState` + `StreamsEvent`.
- Toolbar `res/menu/menu_streams.xml` action `action_stream_import` (string `streams_import`).

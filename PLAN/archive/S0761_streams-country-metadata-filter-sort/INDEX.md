# S0761 - Tactical plan: stream country metadata (filter + sort + list)

**Ticket:** S0761
**Status:** Tactical
**Strategic:** `PLAN/S0761_streams-country-metadata-filter-sort.md`
**Research:** `research/01__streams-language-handling-and-mirror-plan.md` (mirror plan, exact file:line targets)

> Approach: mirror the existing `language` metadata end-to-end as a new `country` field. v1 is catalog-only (the curated CSV already supplies an ISO 3166-1 alpha-2 code that is parsed but dropped on import). No TLD derivation. Unknown country mirrors empty language: hidden chip, nulls-last sort, hidden when a specific country filter is active.

Ground truth pinned 2026-06-28: `AppDatabase` version = 36; migrations are top-level `val MIGRATION_XX_YY = object : Migration(XX, YY)` registered in `core/di/DatabaseModule.kt` `addMigrations(..)` + imported there. Country migration = `MIGRATION_36_37`, bump version to 37.

---

## Phase 1 - Data layer (entity + migration + DAO + import)

- [x] `StreamSourceEntity` (`data/local/db/StreamSourceEntity.kt`): add nullable column `country: String?` right after `language`. Mirror the `language` column annotation/`@ColumnInfo` style exactly.
  - Verification: field present; entity compiles.
- [x] New `data/local/db/Migration36To37.kt`: `val MIGRATION_36_37 = object : Migration(36, 37) { override fun migrate(db) { db.execSQL("ALTER TABLE stream_sources ADD COLUMN country TEXT") } }`. Copy the exact shape of `Migration35To36.kt`.
  - Verification: file compiles; SQL mirrors the `language` add-column migration.
- [x] `AppDatabase.kt`: bump `version = 36` -> `37`.
  - Verification: value is 37; schema export test regenerates.
- [x] `DatabaseModule.kt`: import `MIGRATION_36_37`; append it to `addMigrations(.., MIGRATION_35_36, MIGRATION_36_37)`.
  - Verification: import + registration present, ordered after 35_36.
- [x] `StreamSourceDao.updateCatalogByUrl`: add `country = :country` to the SET clause and a `country: String?` parameter, mirroring `language`.
  - Verification: query + signature updated; callers compile.
- [x] `ImportStreamCatalogUseCase`: write `country = entry.country.ifBlank { null }` at the `ParsedCatalogEntry -> StreamSourceEntity` mapping AND pass it through any `updateCatalogByUrl` call. Manual rows stay null.
  - Verification: import maps country; `mergeCatalog`/`updateCatalogByUrl` carry it.

## Phase 2 - Domain sort enum

- [x] `domain/model/StreamDefaultSort.kt`: add `COUNTRY` mirroring `LANGUAGE`.
  - Verification: enum value present; exhaustive `when` sites updated.

## Phase 3 - ViewModel filter + facets + sort

- [x] `StreamsViewModel`: add `country` to `StreamsFilter`; `countries` to `StreamsFacets`; single-value `facetsOf` extraction for country (like `categories`, not comma-split - country is one code); `countryHit` equality predicate in `applyFilter`; `SortMode.COUNTRY` with `compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.country }`; `toSortMode()` branch for `StreamDefaultSort.COUNTRY`.
  - Verification: filter/sort honor country; unknown country nulls-last and hidden under active country filter.

## Phase 4 - UI list row

- [x] `res/layout/item_stream_source.xml`: insert a `tvCountry` chip BETWEEN `tvTopic` and `tvLanguage` (country before language), same `@drawable/bg_stream_chip` / `wrap_content` style. No `layout-land` counterpart exists for this row - none to mirror.
  - Verification: `tvCountry` id present, positioned before `tvLanguage`.
- [x] `StreamSourceAdapter`: `bindChip(binding.tvCountry, <countryDisplay>)`; extend `chipRow` visibility OR to include `tvCountry`. Display = `flag+code` (e.g. `🇺🇦 UA`) via `TranslationLanguageCatalog.getFlagEmoji(code)` + the raw code.
  - Verification: country chip renders flag+code; row visible when only country present.

## Phase 5 - Filter dialog (3rd facet)

- [x] New `StreamCountryOptionMapper` mirroring `StreamLanguageOptionMapper`: map each country code to a picker `Option` with `flag+code` label.
  - Verification: options built from facet country codes.
- [x] `dialog_streams_filter.xml` + `StreamsFilterDialogManager`: add a full-width second row holding the `Country` facet (label+chevron+value), under the existing `Category + Language` two-column row. Wire tap -> country picker -> `StreamsFilter.country`. Mirror the language row's focus/click handling.
  - Verification: country row present full-width; picker selects/clears country; existing two-column row unchanged.

## Phase 6 - Persistence

- [x] `StreamsSessionStore`: add `KEY_LAST_COUNTRY = stringPreferencesKey("last_country")`, a `lastCountry` field on `Session`, read/write mirroring `KEY_LAST_LANGUAGE` in `writeFilterState()`, and restore in `seedInitialFilter()`.
  - Verification: country selection survives session like language.

## Phase 7 - Tests

- [x] `StreamsFilterTest`: add country facet/filter/sort cases (catalog row with country, unknown-country nulls-last, country filter excludes unknown).
- [x] `StreamCatalogCsvParserTest`: add a `country` column parse case (already parsed; assert it survives).
  - Verification: `testStandardDebugUnitTest --tests *StreamsFilterTest --tests *StreamCatalogCsvParserTest` green.

## Phase 8 - Build + device gate

- [x] Standard debug build green; `AppDatabaseSchemaExportTest` regenerated/green (schema 37).
- [x] Insert `Timber.d("S0761: ..")` probe at the country bind/filter entry; status -> `BlockNeedUserTest`.
  - Device test: catalog channel shows `flag+code` before language; filter by country; sort by country; unknown-country row stays sane; app upgrades cleanly (migration 36->37).

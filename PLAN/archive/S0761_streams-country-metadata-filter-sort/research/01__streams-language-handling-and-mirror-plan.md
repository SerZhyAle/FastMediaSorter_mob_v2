# S0761 research 01 - Stream language handling + country mirror plan

**Date:** 2026-06-28
**Scope:** app_v2, streams feature (data + domain + ui)
**Goal:** Document how the existing `language` metadata flows end-to-end so `country` can mirror it, and pin the Room migration mechanics.

---

## Headline finding

The curated catalog CSV **already carries a `country` column**, it is **already parsed**, and it is **silently dropped** on import. There is no need to derive country from the URL for catalog channels.

- `StreamCatalogCsvParser` parses `country = cell(fields, "country")` into `ParsedCatalogEntry.country`
  (`data/repository/StreamCatalogCsvParser.kt:47`, field at `:149`).
- `ImportStreamCatalogUseCase` maps `ParsedCatalogEntry -> StreamSourceEntity` but never assigns
  `country` (no entity column exists) - the parsed value is discarded (`:65-78`).
- The catalog generator writes `country` as an **ISO 3166-1 alpha-2 code** (uppercase): radio-browser
  `$s.countrycode`, iptv-org `$c.country`, hand seeds `'US'`/`'DE'`
  (`scripts/streams/collect-stream-candidates.ps1:125,624,664,719,732-734`).

Consequence: for catalog channels, country = a clean 2-letter code, available for free the moment a
`country` column is added to the entity and wired through. TLD-from-URL derivation is only relevant for
manual / imported rows, which today also have no language - so "mirror language" means v1 leaves those
rows' country null.

The 2-letter-code shape also unlocks flags: `TranslationLanguageCatalog.getFlagEmoji(code)`
(`ui/.../TranslationLanguageCatalog.kt:118-127`) converts any 2-letter uppercase code to a flag emoji.
The language filter picker already uses this via `StreamLanguageOptionMapper`.

---

## 1. Entity model

`StreamSourceEntity` (`data/local/db/StreamSourceEntity.kt`, table `stream_sources`) holds `language: String?`
as a nullable column (added by `Migration33To34`). No domain `StreamSource` class exists - the Room entity
is passed straight to the adapter and ViewModel. Adding `country: String?` to the entity propagates the
field everywhere automatically (same design as language).

## 2. Language derivation

1. CSV `language` cell -> `ParsedCatalogEntry.language` (may be multi-value, comma-separated, e.g.
   `russian,ukrainian`).
2. `ImportStreamCatalogUseCase:77` writes `entry.language.ifBlank { null }` to the entity.
3. `StreamSourceRepository.mergeCatalog` inserts new rows (`insertIgnore`) and refreshes existing rows via
   `StreamSourceDao.updateCatalogByUrl` (`StreamSourceDao.kt:76-87`), whose SET clause includes `language`.
4. Manual rows (`AddStreamSourceUseCase`, `ImportStreamPlaylistUseCase`) never set language -> null.
5. No URL-based derivation exists anywhere.

`country` mirror: add `country = entry.country.ifBlank { null }` at the import mapping; add `country = :country`
to `updateCatalogByUrl`. Manual rows leave it null.

## 3. List row display

`StreamSourceAdapter.bind` calls `bindChip(binding.tvLanguage, source.language)` and shows `chipRow`
when `tvTopic.isVisible || tvLanguage.isVisible` (`StreamSourceAdapter.kt:107-110,225-232`). `bindChip`
shows the **raw stored value** as chip text. Row layout `res/layout/item_stream_source.xml`: `chipRow`
(horizontal) contains `tvTopic` (`:79-93`) then `tvLanguage` (`:95-108`); both are `wrap_content`
`@drawable/bg_stream_chip` chips. **No `layout-land` counterpart** for this row file.

`country` mirror: insert `tvCountry` between `tvTopic` and `tvLanguage` (country BEFORE language, per request);
extend the `chipRow` visibility OR; call `bindChip(binding.tvCountry, <country display>)`. Display string is
a product decision (raw code vs flag vs flag+code) - see "Owner decisions".

## 4. Filter

- State: `StreamsFilter` (in `StreamsViewModel.kt:~415`) holds `category`, `language`, etc.; facets in
  `StreamsFacets` (`:~409`).
- Facet extraction `facetsOf` (`:381-394`): `languages` split on comma, lowercased, distinct, sorted;
  `categories` is single-value (`:382`).
- Predicate `applyFilter` (`:348-379`): `languageHit` uses `.tokens()` to match comma-separated values
  (`:356-358`); `categoryHit` is a plain equality (`:355`).
- Dialog `StreamsFilterDialogManager` + `dialog_streams_filter.xml`: a horizontal row with two
  `layout_weight=1` tappable columns `rowCategory` + `rowLanguage`, each a label+chevron over a value
  TextView. `StreamLanguageOptionMapper.languageOptions()` maps each language name to a picker `Option`
  with a flag emoji.

`country` mirror: add `country` to `StreamsFilter`, `countries` to `StreamsFacets`, a `facetsOf` extraction
(single-value like categories, since country is one code), a `countryHit` predicate (equality), a
`StreamCountryOptionMapper` (flag emoji from the 2-letter code), and a 3rd facet control in the dialog.
The dialog only has room for 2 equal columns today - the 3rd-facet placement is a layout decision.

## 5. Sort

`StreamsViewModel.SortMode` (`:~426`) + `StreamDefaultSort` domain enum (NAME/TOPIC/LANGUAGE/RECENT,
`domain/model/StreamDefaultSort.kt`). Comparator for language:
`SortMode.LANGUAGE -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.language }` (`:374`).
`toSortMode()` maps `StreamDefaultSort -> SortMode`.

`country` mirror: add `COUNTRY` to both enums, a `nullsLast` comparator on `it.country`, and a `toSortMode`
branch. Unknown/null country lands last, same as language.

## 6. Country / TLD / locale utilities

No TLD->country mapping exists anywhere in `app_v2/src`. The only country-code utility is
`TranslationLanguageCatalog.getFlagEmoji(2-letter-code) -> flag emoji`. ISO country lists via
`java.util.Locale` are available API 1+ (legacy minSdk 23 safe). So no TLD table is needed unless the
owner wants derivation for manual rows.

## 7. Room migration mechanics

- `AppDatabase` is currently **version 36**; chain ends at `MIGRATION_35_36`.
- Canonical add-column pattern: `Migration33To34.kt` (`ALTER TABLE stream_sources ADD COLUMN language TEXT`),
  `Migration34To35.kt`. Each is its own `MigrationXXToYY.kt` file, registered in
  `core/di/DatabaseModule.kt` `addMigrations(...)`.

`country` mirror: bump to **version 37**, add `Migration36To37.kt`
(`ALTER TABLE stream_sources ADD COLUMN country TEXT`), register it. (Verify the exact current max version
at impl time - working tree is truth.)

## 8. Filter / sort persistence

`StreamsSessionStore` (DataStore `streams_session`) persists the live filter selection + sort:
`writeFilterState()` stores `language` under `KEY_LAST_LANGUAGE` (null removes the key); `seedInitialFilter()`
restores it. `StreamsSettingsStore` persists the user-default sort.

`country` mirror: add `KEY_LAST_COUNTRY = stringPreferencesKey("last_country")`, a `lastCountry` field on
`Session`, read/write mirroring `KEY_LAST_LANGUAGE`, and restore in `seedInitialFilter()`.

---

## MIRROR PLAN (one line each)

- **Entity + migration:** `country: String?` after `language`; `Migration36To37` (`ADD COLUMN country TEXT`); register in `DatabaseModule`; bump DB to 37; add `country` to `StreamSourceDao.updateCatalogByUrl` SET clause.
- **Derivation:** `country = entry.country.ifBlank { null }` at import; manual rows stay null. Catalog already supplies an ISO alpha-2 code.
- **Row display:** `tvCountry` chip between `tvTopic` and `tvLanguage`; extend `chipRow` visibility; `bindChip(tvCountry, <display>)`.
- **Filter:** `country` in `StreamsFilter`, `countries` in `StreamsFacets`, single-value `facetsOf`, equality `countryHit`, `StreamCountryOptionMapper` (flag from code), 3rd facet control in dialog.
- **Sort:** `COUNTRY` in `SortMode` + `StreamDefaultSort`, `nullsLast` comparator on `it.country`, `toSortMode` branch.
- **Persistence:** `KEY_LAST_COUNTRY` in `StreamsSessionStore` mirroring `KEY_LAST_LANGUAGE`.

## Owner decisions (cannot be inferred from code)

1. **TLD-derivation scope.** Catalog already provides ISO country; is v1 catalog-only (mirror language), or also derive from URL TLD for manual/imported rows (needs a new TLD->country table)?
2. **Display format.** Country is a 2-letter ISO code; flag helper exists. Show in the row chip as flag-only (🇺🇦), code-only (UA), or flag+code (🇺🇦 UA)? Filter picker recommended: flag+code (mirrors language picker).
3. **Filter 3rd-facet placement.** 2 equal columns exist today; Country as a full-width 2nd row, a cramped 3-up row, or a 2x2 grid?

All other §4 questions (fallback for unknown, sort ordering of unknowns, centralization, persistence) resolve by mirroring language.

## Test impact

Extend `StreamsFilterTest` (country facet/filter/sort cases) and `StreamCatalogCsvParserTest`
(a `country` column case). DAO/migration covered by `AppDatabaseSchemaExportTest` schema export.

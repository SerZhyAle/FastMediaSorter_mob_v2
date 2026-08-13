# Research 01 - Stream catalog language column format

Strategic §6 item 1. Resolved 2026-06-21 by inspecting the live catalog.

## Source

- Catalog URL (hard-coded in `ImportStreamCatalogUseCase.CATALOG_URL`): GitHub release asset `stream-catalog.zip` → `streams.csv`.
- Header: `category,topic,name,url,media_kind,protocol,format,bitrate,is_live,https,language,country,homepage,source_kind,license_note,notes,confidence`.
- 384 data rows at time of inspection.

## Findings

- `language` is a lowercase ENGLISH display name, not a language code. Examples: `english`, `russian`, `ukrainian`, `german`, `brazilian portuguese`, `persian`, `sanskrit`, `tagalog`, `malayalam`.
- Multi-language rows exist as comma-separated names in a single cell: `russian,ukrainian`, `english,french`, `english,spanish`, `chinese,english,korean,russian,spanish,tagalog,vietnamese`, `dutch,english`.
- Some `language` cells are empty.
- `country` is a 2-letter ISO country code: `UA`, `RU`, `US`, `GB`, `DE`, .. plus `XX` for unknown and empty values. Currently parsed by `StreamCatalogCsvParser` but DROPPED at import (`ImportStreamCatalogUseCase` maps only `language` to the entity).
- Distinct categories: `Live TV`, `Open movies`, `Radio`, `Radio (SomaFM)`, `Test stream` (small set - a plain searchable picker is enough; flags not applicable to category).

## Consequences for the spec

1. Facet language list must SPLIT each cell on comma and offer individual language names. The current `facetsOf` uses the whole string, so `russian,ukrainian` is one bogus facet that matches no single-language query. Equality match (`source.language == filter.language`) must become per-token membership.
2. Language values are NAMES, not codes - `TranslationLanguageCatalog.findLanguage(code)` cannot be used directly. Flags require a name→code resolver (English display name → known language code → existing `LanguageItem`/`LanguageFlagFormatter`). Names outside the translator catalog (e.g. `sanskrit`, `tagalog`, `malayalam`) degrade to plain text, per strategic §3.2.
3. No Room schema change: the `country` column would give reliable flags, but capturing it needs a migration, which strategic §3.2 forbids. Flags are derived from the language name via the resolver instead; `country` stays unused. Capturing `country` for richer flags is a possible follow-up, not part of S0580.
4. Filtering by language must be case-insensitive and trimmed (names are lowercase in the catalog but manual rows are null).

## Verbatim sample (distinct language values)

```
(empty)
arabic
brazilian portuguese
chinese,english,korean,russian,spanish,tagalog,vietnamese
dutch
dutch,english
english
english,francaise
english,french
english,german
english,italian
english,jamaican
english,persian
english,spanish
french
german
hungarian
italian
japanese
korean
malayalam
norwegian
persian
polish
portuguese
russian
russian,ukrainian
sanskrit
serbian
slovak
spanish
turkish
ukrainian
```

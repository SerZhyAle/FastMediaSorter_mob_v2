# Phase 03 - Catalog import use case

**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done (backend; build pending central build)
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04

## Objective

Download our curated catalog (zip from the GitHub Release asset), unzip `streams.csv`, parse, map to
`StreamSourceEntity` (origin=CATALOG, with category/topic/language), and merge-with-prune via the
repository. Reuse the injected `OkHttpClient`; no new networking dependency.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamCatalogUseCase.kt` | New | <= 150 |

## Steps

### Step 03.1 - `ImportStreamCatalogUseCase`

**Prompt for developer:**

> Create `class ImportStreamCatalogUseCase @Inject constructor(okHttpClient, parser: StreamCatalogCsvParser, classifier: StreamMediaKindClassifier, repository: StreamSourceRepository)` with `suspend operator fun invoke(): CatalogImportResult` on `Dispatchers.IO`. Constant `private const val CATALOG_URL = "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip"`. Steps: GET the zip via OkHttp (use `response.body?.byteStream()`); read the zip with `java.util.zip.ZipInputStream`, take the first entry named `streams.csv` (or the first `.csv`), decode UTF-8 to text; `parser.parse(text)`; map each `ParsedCatalogEntry` -> `StreamSourceEntity(id = UUID, url, title = name, mediaKind = entry.mediaKind.uppercase().ifBlank { classifier.classify(url) }, sourceOrigin = "CATALOG", sortIndex = 0, addedAt = now, category = entry.category, topic = entry.topic, language = entry.language)`; call `repository.mergeCatalog(list)`. Return a sealed `CatalogImportResult { Success(added, updated, removed) | Empty | Failure(reason) }`. Wrap network/zip/parse failures in `Failure` with a single `Timber.w(e, "...")` (no empty catch, no `Sxxxx`). Guard against a malformed/oversized zip (cap read, e.g. ignore entries > a few MB).

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ImportStreamCatalogUseCase`, `ZipInputStream`, `mergeCatalog`, `CatalogImportResult` present.
- `Grep` - `CATALOG_URL` contains the release-asset zip path; `sourceOrigin = "CATALOG"` present.
- `Grep` - no empty `catch {}`; no `Sxxxx` in log strings.
- Build: `.\a.ps1 fk` PASS.

**Status:** `[x]` done

**Step Log:** Created `ImportStreamCatalogUseCase` (`@Inject` ctor: OkHttpClient + parser + classifier + repository). `invoke()` on `Dispatchers.IO`: GET `CATALOG_URL` (release-asset `stream-catalog.zip`) via OkHttp `byteStream()`, scan with `ZipInputStream` preferring a `streams.csv` entry (any `.csv` as fallback), per-entry read capped at 8 MB (oversized entries skipped), UTF-8 decode -> `parser.parse`. Maps to `StreamSourceEntity` (origin=CATALOG, `mediaKind = entry.mediaKind.uppercase().ifBlank { classifier.classify(url) }` - reuses the shared classifier, no duplicated body; category/topic/language blank->null) then `repository.mergeCatalog`. Sealed `CatalogImportResult { Success(added,updated,removed) | Empty | Failure(reason) }`; each download/parse/merge failure logged once via `Timber.w(e, ...)` with no ticket id; empty parse / missing csv -> `Empty`. Self-verified via Grep (all symbols present, no `Sxxxx` in any `Timber.` string, no empty catch).

## Phase Done Criteria

- [x] Step 03.1 done.
- [ ] `.\a.ps1 fk` PASS (central build, orchestrator).
- [x] mediaKind classification helper reused (no duplicated body vs `AddStreamSourceUseCase`/import).

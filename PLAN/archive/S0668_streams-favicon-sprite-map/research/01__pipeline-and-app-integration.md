# S0668 Research - Favicon sprite-atlas pipeline and app integration

**Spec:** [`../../S0668_streams-favicon-sprite-map.md`](../../S0668_streams-favicon-sprite-map.md)
**Date:** 2026-06-25
**Scope:** Ground the strategic §6 open items against the live stream-catalog pipeline. Read-only investigation; no code written. Every cited file was confirmed to exist on disk this session.

---

## 1. End-to-end pipeline (verified)

The downloadable stream catalog is a single flat CSV plus (proposed) one atlas PNG, shipped together inside one zip GitHub release asset; the app downloads the zip, unzips, parses the CSV, merges rows into a Room table, and renders each row in a RecyclerView.

| Stage | Owner | File (verified) |
|-------|-------|------|
| Offline collection / CSV authoring | PowerShell 7 | `scripts/streams/collect-stream-candidates.ps1` |
| CSV schema | `$Schema` array, lines 105-109 | same |
| Publish (zip + upload) | `Invoke-PublishCatalog`, lines 791-807 | same |
| Download + unzip + extract CSV | `ImportStreamCatalogUseCase.downloadCsv()` | `app_v2/.../domain/usecase/streams/ImportStreamCatalogUseCase.kt` |
| CSV parse (header-keyed) | `StreamCatalogCsvParser.parse()` | `app_v2/.../data/repository/StreamCatalogCsvParser.kt` |
| Row entity | `StreamSourceEntity` (Room) | `app_v2/.../data/local/db/StreamSourceEntity.kt` |
| Merge into table | `StreamSourceRepository.mergeCatalog()` line 64 | `app_v2/.../data/repository/StreamSourceRepository.kt` |
| Row render | `StreamSourceAdapter.VH.bind()` | `app_v2/.../ui/streams/StreamSourceAdapter.kt` |
| Row layout | single layout, no land variant | `app_v2/src/main/res/layout/item_stream_source.xml` |

---

## 2. Resolution of strategic §6 open items

### §6 #1 - Favicon source (RESOLVED -> homepage column)

The CSV already carries a `homepage` column (schema index 13). It is populated from the channel's own site:
- iptv-org path: `-homepage ($c.website)` (line 700).
- radio-browser path: `-homepage ($s.homepage)` (line 645).
- curated extras: explicit `homepage = 'https://www.nasa.gov/nasatv'` etc. (lines 713-715).

So the favicon source domain is the `homepage` cell, NOT the stream `url` (which is m3u8/RTSP and has no favicon). The parser already reads it: `homepage = cell(fields, "homepage")` and surfaces it on `ParsedCatalogEntry.homepage`. Coverage is partial - many rows have an empty `homepage`, which is an accepted empty-favicon row.

### §6 #2 - Atlas build location + format (RESOLVED location / DECIDED format)

Built offline by the same PowerShell collector (strategic already says "resolved: offline"). This plan decides: **PNG grid atlas, fixed tile size 32x32 px**, packed left-to-right / top-to-bottom into a fixed-width grid. 32 px balances recognisability against archive size; favicons are decoded/normalised to exactly the tile size during packing. Atlas size budget: a 16-column grid of 32 px tiles holds 256 icons per 8 rows; a catalog of ~1-2k rows with partial coverage yields a PNG well under ~1 MB after compression, acceptable inside the existing zip.

### §6 #3 - CSV coord encoding + backward-compat (RESOLVED -> single `favicon_index`)

**Decision: one new column `favicon_index`** = the zero-based tile ordinal in a fixed-grid atlas (empty = no favicon). The app reconstructs the pixel rect from the index plus two atlas constants it already knows (tile size + columns-per-row): `col = index % COLS`, `row = index / COLS`, `rect = (col*TILE, row*TILE, TILE, TILE)`. Rationale over `x,y,w,h`:
- The PowerShell packer writes one integer, not four - trivial to emit and diff.
- The app stores one int per url in the sidecar, not a 4-tuple.
- A fixed grid removes any per-row geometry, so a malformed coord cannot point outside the atlas (clamp on `index >= tileCount`).

Backward-compat is structural: `StreamCatalogCsvParser` matches columns BY HEADER NAME and "tolerates unknown extra columns" (class KDoc + `cell()` returns `""` for absent columns). Adding `favicon_index` to `$Schema` and reading `cell(fields, "favicon_index")` cannot break an older catalog (missing column -> empty -> no favicon) nor an older app reading a newer catalog (extra column ignored).

### §6 #4 - Delivery (RESOLVED -> same zip)

`Invoke-PublishCatalog` currently runs `Compress-Archive -Path $CsvPath -DestinationPath temp/stream-catalog.zip -Force` (line 801) then `gh release upload delivery-so-v1` (line 804). Extending `-Path` to an array `@($CsvPath, $AtlasPath)` bundles both into the one asset the app already fetches (`CATALOG_URL` -> `delivery-so-v1/stream-catalog.zip`). `ImportStreamCatalogUseCase.downloadCsv()` already `ZipInputStream`-walks every entry and currently ignores non-CSV entries; a sibling extraction of the PNG entry by name is additive.

---

## 3. Architecture decisions baked into the tactical plan

### 3.1 Sidecar over Room column (RECOMMENDED - no migration)

`StreamSourceEntity` is a shipped Room entity. Adding a coord column forces a Room version bump + migration. Avoid it:

- **Persist a sidecar at import time**: write the extracted atlas PNG to app files storage, and write a coords map `url -> favicon_index` (a small JSON or properties file) alongside it. Both are app-private files re-written wholesale on every catalog import (the catalog is fetched fresh, never SHA-pinned - same lifecycle as the CSV).
- **Render-time lookup by url**: the adapter already has `source.url` per row; the sidecar is keyed by url. This is mandatory because the import assigns `id = UUID.randomUUID().toString()` on every run (line ~53 of the use case), so the entity id is NOT stable across imports and cannot key the coords. `url` is stable and is already the entity's unique index (`index_stream_sources_url`) and the repository's `getByUrl()` lookup key.
- **No schema change, no migration, no DAO change.** The Room table stays exactly as shipped.

If a future maintainer instead chooses a Room column, the tactical step MUST name the explicit new schema version number and the `Migration(N-1, N)` (per the /spec-all hard-stop on unnamed Room migrations). This plan does NOT take that path.

### 3.2 Atlas region decode (proposed; final choice deferred to the render phase)

Three candidates evaluated for slicing a tile out of the atlas without janking scroll:
- **Decode-once + in-memory crop**: decode the whole atlas PNG once on first use, cache the `Bitmap`, then `Bitmap.createBitmap(atlas, x, y, TILE, TILE)` per row off the main thread. At 32 px tiles the full atlas bitmap is small (a 16x64 grid of 32 px = 512x2048 px ~= 4 MB ARGB_8888) - one cached decode covers the whole list. Simplest, lowest per-row cost. **Recommended.**
- **`BitmapRegionDecoder`**: decodes only the sub-rect from the encoded stream per tile; avoids holding the full bitmap but re-reads/re-decodes per region. Better if the atlas were very large (it is not). Keep as fallback if memory profiling flags the full-atlas cache.
- **Glide custom loader/decoder**: the project already ships this pattern (`data/glide/NetworkPdfThumbnailLoader.kt`, `PdfPageDecoder.kt`, `EpubCoverDecoder.kt`), so a `FaviconAtlasLoader` keyed by `(atlasFile, index)` is feasible and gives Glide's lifecycle + memory cache for free. Heaviest to wire; justified only if decode-once shows jank. The render phase picks decode-once first, escalates only on measured jank.

### 3.3 Row layout - no land counterpart

`res/layout/item_stream_source.xml` is the ONLY layout for the stream row; there is NO `res/layout-land/item_stream_source.xml` (verified absent). RecyclerView rows reuse one layout for both orientations. CLAUDE.md Rule 11 (land parity) therefore does NOT apply to this file - the tactical plan must not invent a land-edit step. Current leading edge order: `ivPlayStatus` (14dp bullet) -> `ivKind` (32dp kind icon) -> text column -> pin -> overflow. The favicon thumbnail is a NEW leading `ImageView` (proposed 24-28dp) inserted at the leading edge; it coexists with - does not replace - `ivPlayStatus` (S0593 status bullet) and `ivKind`.

### 3.4 Flavor reach - rides existing rows, no gating

Owner-locked flavors: standard / legacy / noLegal / vr. The favicon render path is pure data + view binding on rows that ALREADY ship in those flavors (the stream catalog itself is the flavor-gated surface, established by S0575). No new `BuildConfig.IS_*` guard and no flavor source-set split is needed for the rendering path; it inherits the catalog's existing reach. Follow flavor source-set discipline (Rule 14): zero flavor guards in `src/main`.

---

## 4. Test fixture strategy

The app-side phases (parser field, import extraction, region decode, adapter render) need a real atlas + CSV to verify against before the owner runs the live offline collection. The offline-tooling phase therefore lands EARLY and includes generating a small committed test fixture: a hand-built mini atlas PNG (a few tiles) plus a CSV snippet carrying `favicon_index` values, stored under the tactical folder or `app_v2/src/test/resources`, so unit tests assert parse + index->rect math deterministically without network.

---

## 5. External / manual closure steps (cannot be auto-done)

These are owner-run and gate final verification; the tactical plan flags them as EXTERNAL, not code:
1. Run the offline collector with the new favicon-fetch + atlas-pack path to build a REAL atlas over the live catalog (network fetch of thousands of favicons; partial coverage expected).
2. `gh release upload delivery-so-v1 stream-catalog.zip --clobber` to publish the new CSV+atlas bundle (`Invoke-PublishCatalog`).
3. On-device verification that real rows render recognisable favicon thumbnails and favicon-less rows show an empty slot.

---

## 6. Open decisions for the orchestrator (could not be resolved from the codebase)

- **Favicon fetch strategy / fallback chain** in the offline tool: `/favicon.ico` vs parsing `<link rel="icon">` vs Google s2 favicon service (`https://www.google.com/s2/favicons?domain=`). This is a network/heuristic policy choice with no in-repo precedent; recommended default = try direct `/favicon.ico`, then `<link rel=icon>`, then s2 fallback, but the owner should confirm whether the s2 third-party fetch is acceptable for catalog building. Not code-blocking for the app side.
- **Atlas tile size final value (16 vs 32 vs 48 px)** and **grid column count**: this plan proposes 32 px / 16 columns as the contract both sides hard-code; if the owner wants larger/sharper icons it changes the shared constant in both the packer and the app. Flagged, not blocking.
- **Sidecar file format** (JSON object vs Java properties vs a tiny CSV): plan defaults to a compact JSON `{ "url": index }`; trivial, no external dependency, decided at the import phase.

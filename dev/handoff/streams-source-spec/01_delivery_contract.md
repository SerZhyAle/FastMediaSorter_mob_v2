# Streams Source Spec - 01 - Delivery Contract (the reusable stream bank)

Part of the FastMediaSorter "Трансляции" (Streams) source-documentation set. **This is the crown-jewel
file**: the self-contained specification of the stream **bank** and favicon **atlas** as a delivery
artifact, so a separate app can consume (and, with `08`, regenerate) the exact same data.

Everything here is a **[CONTRACT]** unless marked *(impl detail)*. Facts cite `path:line` (root
`p:\ANDROID\FastMediaSorter_mob_v2`) or a directly-measured live artifact. Deeper breakdowns:
`03_catalog_format.md` (every CSV column), `04_favicon_atlas.md` (atlas internals),
`08_build_publish_pipeline.md` (how the bank is produced).

---

## 1. What the bank is, at a glance

The bank is a single ZIP asset hosted on a GitHub Release. It contains exactly two files:

```
stream-catalog.zip
├── streams.csv          (entry 0, always first)   - the channel catalog, RFC-4180 UTF-8 (no BOM)
└── favicon-atlas.png    (optional, appended after) - one sprite-atlas PNG of channel favicons
```

- The catalog is a flat list of channels: name, URL, media kind, and metadata.
- The atlas is one PNG holding all channel favicons as a 32x32-tile / 16-column grid; each catalog row
  points at its tile via the `favicon_index` column.
- A third file, `favicon-coords.json` (a `url -> favicon_index` map), is **NOT** in the ZIP - the app
  derives it from the CSV at import time. It is documented here only because it is the app's on-disk form
  of the same mapping (section 5.3).

Live sizes (measured 2026-07-19): `streams.csv` = 966,495 bytes / 2,691 channels; `favicon-atlas.png` =
2,426,865 bytes / 512x3296 px / 1,636 favicons; ZIP ~2.5 MB.

---

## 2. Hosting & fetch **[CONTRACT]**

- **URL** (`ImportStreamCatalogUseCase.kt:189-190`, verbatim):
  ```
  https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip
  ```
- GitHub Release, permanent tag **`delivery-so-v1`**, asset name **`stream-catalog.zip`**.
- **Not SHA-pinned** - the bank is meant to change; a consumer always fetches the latest asset. (Other
  assets on the same `delivery-so-v1` tag, e.g. `.so`/`.mp4`, are SHA-pinned; the stream catalog is the one
  mutable asset there, fetched by separate logic with no hash pinning.)
- The producer overwrites the asset in place via `gh release upload delivery-so-v1 stream-catalog.zip
  --clobber`, so the URL is stable across updates.
- **Fetch discipline in the app** *(impl detail; a consumer may choose its own)*: OkHttp with a 30 s overall
  `callTimeout` (DNS+connect+write+full-body read as one deadline), on top of the shared client's 10 s
  per-phase timeouts. This 30 s budget is why the atlas is capped at 3 MiB at publish time (section 4.4).
- Re-fetch is only ever triggered by an **explicit** user action (Streams refresh/import or Welcome
  onboarding) - **never** automatically on app update.

---

## 3. `streams.csv` contract **[CONTRACT]**

Full column semantics are in `03_catalog_format.md`; this is the contract summary.

### 3.1 Encoding & shape
- **UTF-8, no BOM.** RFC-4180. One header row + one data row per channel.
- The producer double-quotes every field; the consumer parser does **not** require quoting (it is an
  RFC-4180 superset: quoted commas, doubled `""` escapes, embedded newlines in quoted fields, mixed
  `\n`/`\r\n`, tolerant trailing newline).
- **Columns are matched by header NAME, case-insensitive, not by position.** A consumer must key off the
  header row. The producer may reorder or append columns without breaking older consumers; a consumer newer
  than the catalog reads missing columns as blank.

### 3.2 The 18 columns (producer write order)

```
category, topic, name, url, media_kind, protocol, format, bitrate,
is_live, https, language, country, homepage, source_kind,
license_note, notes, confidence, favicon_index
```

Minimum a consumer must read: **`name`**, **`url`** (both required - a row missing either is dropped),
**`media_kind`**, and (for favicons) **`favicon_index`**. Useful metadata: `category`, `topic`, `language`,
`country`, `homepage`. The remaining columns (`protocol`, `format`, `bitrate`, `is_live`, `https`,
`source_kind`, `license_note`, `notes`, `confidence`) are maintainer/tooling metadata the Android app
parses but does not store.

### 3.3 Key field semantics
- **`url`** - the **direct, playable** stream URL (playlists already resolved to the underlying stream). It
  is the stable de-duplication key across the whole system.
- **`media_kind`** - `AUDIO` | `VIDEO` | `RTSP`. Drives playback routing. If blank, derive it from the URL
  (section 3.4). Note the catalog's declared kind **wins** over URL-derived classification (e.g. an HLS
  `.m3u8` radio stream is legitimately `AUDIO`).
- **`favicon_index`** - zero-based tile ordinal into `favicon-atlas.png`. Blank / non-numeric / negative /
  absent column all mean "no favicon". **Not stable** across catalog regenerations - always resolve against
  the atlas shipped in the **same** ZIP (section 4).
- **`homepage`** - the channel's website; the **favicon source** for the offline packer (section 4.5). Not a
  playback field.
- **`language`** - lowercase language name(s), comma-separated inside one cell (e.g. `english,german`).
- **`country`** - ISO 3166-1 alpha-2.

### 3.4 URL -> media-kind classification **[CONTRACT]** (used when `media_kind` is blank, or by manual/playlist adds)
```
rtsp://...                                        -> RTSP     (scheme wins outright)
extension in {m3u8, mpd, mp4, mkv, webm, ts, mov} -> VIDEO    (last path segment, after stripping ?query/#frag)
otherwise (audio ext, pathless, unknown ext)      -> AUDIO    (radio default)
```
Launchable schemes are only `http://`, `https://`, `rtsp://`.

---

## 4. `favicon-atlas.png` contract **[CONTRACT]**

Full internals in `04_favicon_atlas.md`; this is the contract summary.

### 4.1 Geometry
- Tiles are **32 x 32 px**, laid out in a **16-column**, row-major grid.
- **index -> pixel rect**: `col = index % 16`, `row = index / 16` (integer floor),
  `rect = (col*32, row*32, col*32+32, row*32+32)` (right/bottom exclusive).
- **Width is fixed at 512 px** (`16 * 32`). **Height = `ceil(tileCount / 16) * 32`** and grows with content.
- Pixel format RGBA; the canvas is cleared to fully transparent, so unpacked cells (including trailing cells
  of a partial last row) are transparent.

### 4.2 Ordinal assignment
- Ordinals are assigned in **favicon-decode order** while the producer iterates catalog rows top-to-bottom -
  a row with no fetchable favicon consumes no ordinal (no gaps). Therefore `favicon_index` is **not stable**
  across regenerations. **A consumer must always pair the CSV with the atlas from the same ZIP.**

### 4.3 Bounds safety
- A stale/oversized/negative index that does not fit the actual decoded atlas must degrade to "no favicon",
  never crash. (The app: `index < 0` or `rect.right/bottom > atlas.width/height` -> null tile.)
- Edge case: an index in `[packedCount, rowsNeeded*16 - 1]` (a partly-filled last row) is geometrically
  in-bounds but decodes to a transparent tile. A strict consumer can treat "index > max emitted ordinal" as
  invalid (see `04` §1.4).

### 4.4 Size budget
- Producer publish cap **3 MiB** (`$MaxAtlasBytes = 3145728`); over the cap -> the ZIP ships CSV-only.
- App accept cap **4 MiB** (`MAX_ATLAS_BYTES`); over the cap -> the atlas is dropped, the CSV is kept.
- (The 1 MiB gap is headroom, not a two-tier design.)

### 4.5 Favicon source
- The producer fetches each channel's favicon from its **`homepage`** column (not the stream URL), via
  `favicon.ico` -> parsed `<link rel=icon>` -> Google s2 fallback. See `08`.

---

## 5. ZIP packaging invariants **[CONTRACT]**

### 5.1 Entry order
- **`streams.csv` MUST be entry 0** (first). The producer packs the CSV alone first, then appends the atlas
  in a second pass, and self-verifies entry 0 ends with `streams.csv` before uploading.
- Rationale: an older consumer walking the ZIP as a stream reaches the CSV without first streaming past the
  whole atlas.

### 5.2 Entry names
- CSV entry name ends with **`streams.csv`** (a `.csv` entry not so named is a fallback only).
- Atlas entry name ends with **`favicon-atlas.png`** (case-insensitive).
- Any other entry is ignored.

### 5.3 `favicon-coords.json` is app-derived, NOT shipped
- The ZIP has only `streams.csv` (+ optional `favicon-atlas.png`).
- The app builds a `url -> favicon_index` map from the CSV at import and persists it as
  `filesDir/streams/favicon-coords.json` (flat JSON, keyed by the **stream url** because the entity id is
  randomized per import). A reuse may keep the mapping in memory or on disk as it likes - it is fully
  reconstructible from the CSV's `favicon_index` column. Shape:
  ```json
  {"http://chan/a.m3u8": 2, "http://chan/b.mp3": 0}
  ```

### 5.4 Producer publish guard (S0925) *(producer-side; see 08)*
- The producer **refuses to publish** a CSV that carries `favicon_index` values without a bundled atlas
  (unless explicitly overridden), because that combination makes the app wipe every user's favicons.

---

## 6. How the app applies the bank *(impl detail; a reuse may differ)*

Reference behavior of `ImportStreamCatalogUseCase` + `StreamSourceRepository.mergeCatalog` (full detail in
`02_data_model.md`):

1. Download the ZIP (30 s deadline); single-pass walk capturing both the CSV text and the atlas bytes.
2. Parse the CSV -> rows (drop rows missing `url`/`name`).
3. Build the `url -> favicon_index` coords map from rows with a numeric index; write the atlas PNG + coords
   sidecar wholesale (a null/absent atlas clears both to empty).
4. Map rows to entities (`id = random UUID`, `sourceOrigin = "CATALOG"`, `sortIndex = 0`).
5. **Merge-with-prune, keyed by `url`, in one transaction**:
   - existing CATALOG row with this url -> update metadata in place (local order/pin preserved);
   - new url -> insert (but a url already owned by a user-added/imported row **wins** - the catalog insert
     is silently ignored);
   - CATALOG rows whose url vanished from the new catalog -> pruned (chunked to stay under SQLite's
     bind-variable limit).
6. Result = `(added, updated, removed)` counts.

Key idempotency property a reuse should preserve: **re-importing the same bank is a no-op**, and
**user-created channels are never overwritten or removed by a catalog import** (collision by url always
favors the user row; prune only ever removes catalog-origin rows).

Provenance model: each stored channel has an origin - `CATALOG` (from this bank), `MANUAL` (user typed a
URL), or `IMPORTED` (user imported an `.m3u`/`.m3u8` playlist). Only CATALOG rows participate in
merge/prune and carry `category/topic/language/country`.

---

## 7. Backward / forward compatibility **[CONTRACT]**

The bank format is additively versioned via header-named columns and an optional atlas entry:
1. **Consumer newer than the bank** (bank lacks `favicon_index`/atlas): every row reads no favicon; no error.
2. **Consumer older than the bank** (bank has the column + atlas): the extra column is ignored, the atlas
   entry is skipped by name; no error, favicons simply invisible to that consumer. (This is why CSV is
   entry 0.)
3. **Atlas over a consumer's size cap**: atlas dropped, CSV still applied.
4. **Stale coords vs a smaller atlas**: out-of-bounds indices degrade to no-favicon.

A reuse should follow the same rules: match columns by name, tolerate unknown/extra columns and a missing
atlas entry, and bounds-check every `favicon_index`.

---

## 8. Minimal consumer checklist (for reuse)

To consume the bank correctly, a client must:
1. Download `stream-catalog.zip` from the fixed URL (section 2); expect it to change over time.
2. Read `streams.csv` as UTF-8 (no BOM), RFC-4180, matching columns **by header name**.
3. Drop rows missing `url` or `name`; treat `media_kind` (falling back to the URL classifier) as the
   playback route.
4. If a `favicon-atlas.png` entry exists, slice tile `favicon_index` at `(index%16*32, index/16*32, 32, 32)`
   from the 512-wide grid; bounds-check; degrade to no-favicon otherwise.
5. Pair `favicon_index` only with the atlas from the **same** ZIP (indices are not stable).
6. Merge by `url`; never overwrite/remove user-created channels from a catalog import.
7. Re-fetch only on explicit user action.

---

## 9. Ticket index for this file

S0565 (Streams feature + `stream_sources`), S0570 (catalog CSV + import + merge), S0583 (30 s import
timeout, atlas cap rationale), S0668 (favicon atlas), S0761 (country column), S0821 (chunked prune), S0925
(publish guard). Parked during this pass: **S1108** (stale README publish snippet).

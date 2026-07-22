# Streams Source Spec - 04 - Favicon Sprite-Atlas + Grid Live-Frame Thumbnails

Part of the FastMediaSorter "Трансляции" (Streams) source-documentation set. This file is the exhaustive
specification of the channel favicon sprite-atlas (`favicon-atlas.png` + `favicon-coords.json`) as
consumed by the Android app, and of the grid-mode live-frame thumbnails that take precedence over it.

The **atlas geometry, the coords keying, and the sidecar file names are a [CONTRACT]** shared with the
offline packer (`08_build_publish_pipeline.md`) and any reuse of the bank. Everything about how the app
decodes/caches/renders is *(impl detail)*. Facts cite `path:line` (root `p:\ANDROID\FastMediaSorter_mob_v2`).

Origin tickets: **S0668** (favicon atlas, Archived, 6 phases), **S0675** (grid frame capture, Archived,
6 phases), plus S0700/S0712/S0784/S0785/S0925/S0933/S1067.

---

## 1. Atlas PNG geometry contract **[CONTRACT]**

Two constants, hardcoded **independently and identically** on both sides (no shared config file - they
must be kept in sync by hand):

```
TILE = 32   // one favicon tile is 32 x 32 px
COLS = 16   // fixed column count per atlas row
```
- App: `FaviconAtlasSlicer.kt:93-94` (`const val TILE = 32` / `const val COLS = 16`).
- Packer: `collect-stream-candidates.ps1:779-780` (`$script:FaviconTile = 32` / `$script:FaviconCols = 16`).

### 1.1 index -> pixel rect (row-major) **[CONTRACT]**

```
col   = index % 16
row   = index / 16            (integer floor division; indices are always >= 0 when in range)
rect  = (col*32, row*32, col*32+32, row*32+32)   // right/bottom exclusive
```
App `FaviconAtlasSlicer.rectFor` and packer `collect-stream-candidates.ps1:927-931` compute the identical
math. A consumer of the bank must slice tile `index` from exactly this rect.

### 1.2 Canvas dimensions **[CONTRACT]**

- **Width is fixed** at `COLS * TILE = 16 * 32 = 512 px`, regardless of tile count.
- **Height grows** with content: `height = ceil(packedTileCount / 16) * 32`.
- Pixel format RGBA; the canvas is cleared to fully **transparent** before painting, so every unpacked
  cell (including the trailing cells of a partly-filled last row) is transparent, not garbage.
- Live production atlas (measured): **512 x 3296 px** = 16 cols x 103 rows = 1,648 tile slots, of which
  **1,636 are packed** (`ceil(1636/16) = 103` rows; the last row has `1648 - 1636 = 12` blank trailing
  cells). Decoded size ~6.4 MB RGBA; PNG on disk ~2.31 MiB.
- The app never assumes a fixed height - it reads the decoded `Bitmap.width`/`Bitmap.height` at runtime.

### 1.3 Tile packing order **[CONTRACT-adjacent, important]**

Ordinals are assigned **sequentially in the order favicons were successfully decoded** while the packer
iterates catalog rows top-to-bottom - **not** alphabetical, **not** by any display sort, **not** the CSV
row position. A row whose favicon failed to fetch/decode consumes **no ordinal** (no gaps). Therefore
`favicon_index` is **not stable** across catalog regenerations: the same channel URL can get a different
ordinal in the next build. A consumer must resolve `favicon_index` against the atlas shipped in the
**same** zip.

### 1.4 Edge case: "in bounds but never packed"

Bounds checking (section 2) only verifies a tile rect fits the decoded canvas; it cannot know how many
tiles were actually packed. An index in `[packedCount, rowsNeeded*16 - 1]` (e.g. 1636..1647 above) is
numerically in-bounds and decodes to a valid, non-null, **fully transparent** 32x32 bitmap - it does not
hit the no-favicon fallback. A fresh packer+CSV pair never emits such an index (only mapped ordinals are
written), but a tampered/foreign coords sidecar could. A stricter consumer can treat "index > max emitted
ordinal" as invalid.

---

## 2. Bounds checking - stale/oversized index degrades to no-thumbnail, never a crash

`FaviconAtlasSlicer.isInBounds(index, atlasWidth, atlasHeight)` (`:46-50`):
```kotlin
if (index < 0) return false
val rect = rectFor(index)
return rect.right <= atlasWidth && rect.bottom <= atlasHeight
```
`tileFor(index)` composes it: negative index -> `null`; absent atlas file -> `null`; out of bounds ->
`null`; else crop and return a `Bitmap`. **No exception is ever thrown for a bad index** - every caller
gets a nullable `Bitmap?` and degrades to its own no-favicon fallback. This is the guard against "an old
coords sidecar pointing past a shrunk atlas".

Unit-tested against a committed 64x64 (2x2-tile) fixture: index 0/1/16/17 in bounds; index 3 (which a
naive "4-wide" read would expect at x=96) is **out** of bounds because the grid is **always 16 columns**
regardless of the atlas's actual pixel width; index -1 rejected.

---

## 3. Sidecar files (app-private) **[CONTRACT for names; storage is impl detail]**

Location: `filesDir/streams/` (Android app-private internal storage, survives restarts; cleared only by
app-data wipe or re-import).

| File | Name | Content |
|---|---|---|
| Atlas PNG | `favicon-atlas.png` | raw PNG bytes, a verbatim copy of the zip entry (no re-encode) |
| Coords sidecar | `favicon-coords.json` | flat JSON object, `url -> favicon_index` |

Both names are also the exact zip-entry names the app matches inside the downloaded catalog archive
(`name.endsWith("favicon-atlas.png")`, `name.endsWith("streams.csv")`).

### 3.1 Coords JSON shape **[CONTRACT]**

A flat object, one key per channel **URL**, value = zero-based tile ordinal (`Int`):
```json
{"http://chan/a.m3u8": 2, "http://chan/b.mp3": 0}
```
Decode is defensive: each value may be a `Number` or a numeric `String`; a malformed single entry is
skipped (not fatal); a corrupt whole file yields an empty map (logged at info, never throws).

### 3.2 Why keyed by URL, not entity id

Every import builds brand-new `StreamSourceEntity` rows with `id = UUID.randomUUID()`; `mergeCatalog`
matches existing rows **by url**. The coords map, built in the same import pass, carries transient
just-generated ids that do not correlate to persisted ids - only `url` is agreed on by both sides. Every
render-time resolver looks up `coords[source.url]`, never `[source.id]`.

**Note:** `favicon-coords.json` is **not** shipped in the zip - it is built app-side from the CSV's
`favicon_index` column at import time (see `08` / section 6). The zip carries only `streams.csv` +
`favicon-atlas.png`.

---

## 4. Atomic write; null atlas clears to empty

`FaviconAtlasStore.write(atlasBytes: ByteArray?, coords: Map<String,Int>)`:
- `dir.mkdirs()` first.
- **null OR empty `atlasBytes`** -> DELETE the atlas file and write an **empty** coords map. This is the
  "clears to empty state" path: every row falls back to no-favicon on next resolve. Triggered when the
  downloaded zip has no `favicon-atlas.png` entry (an old catalog).
- non-empty bytes -> write both files via `writeAtomically`.

`writeAtomically` = write to `<name>.tmp`, then `renameTo` (atomic on the same filesystem); on rename
failure, direct overwrite fallback. Both files are rewritten **wholesale** on every import (no
incremental update). The two files are each atomic individually but **not atomic across the pair** (atlas
then coords, sequential) - a crash between them could pair a new atlas with old coords. A sidecar-write
failure does **not** fail the catalog import (rows still merge; favicons stay absent until the next
import).

---

## 5. Decode-once caching + invalidation *(impl detail)*

`FaviconAtlasSlicer` decodes the **whole** atlas PNG exactly **once** per instance
(`BitmapFactory.decodeFile`, mutex-guarded; a missing-atlas outcome is cached too). Per-row `tileFor(index)`
runs on `Dispatchers.IO` and **crops** a 32x32 sub-bitmap via `Bitmap.createBitmap(atlas, ...)` (a copy,
not an alias) - list scrolling never re-decodes the PNG. `invalidate()` recycles the cached bitmap and
resets the decoded flag so the next call re-reads from disk.

### 5.1 Per-host instance scoping (invalidation gap)

`FaviconAtlasSlicer` is constructed **once per host** (Streams screen, Favorites/browse, main panel,
launcher picker dialog, launcher gadget - the gadget's is `@Singleton`-scoped, decoded once per process).
`invalidate()` is called from **exactly one** site: `StreamsActivity.kt:309` after an in-app catalog
import. Consequence: after a re-import, the Streams screen picks up the new atlas immediately, but every
**other** already-decoded host keeps serving tiles from its own now-stale cached bitmap until that host is
torn down and rebuilt. The **coords** map, by contrast, is re-read fresh on demand by every host, so
coords staleness self-heals; only the decoded atlas bitmap can go stale per-instance. *(This gap is a
documented fact, not a filed bug.)*

---

## 6. Zip extraction + coords assembly (app import path)

Single-pass `ZipInputStream` walk (`ImportStreamCatalogUseCase.extractCatalog`, `:127-156`): capture BOTH
the CSV text and the atlas PNG bytes; **do not early-return** on the CSV (that would skip a later PNG).
An entry named exactly `streams.csv` wins over any other `.csv` (captured as `fallbackCsv`). Unknown
entries are skipped. `CatalogPayload(csv, atlasPng: ByteArray?)` - `atlasPng == null` means no atlas entry
(old catalog) and flows to `write(null, coords)`.

Read caps (`:189-199`): `MAX_CSV_BYTES = 8 MB` (over-cap CSV aborts the import), `MAX_ATLAS_BYTES = 4 MB`
(over-cap atlas is dropped, CSV kept), `CATALOG_CALL_TIMEOUT_SECONDS = 30`.

Coords assembly (`:56-63`):
```kotlin
val coords = entries.filter { it.faviconIndex != null }.associate { it.url to it.faviconIndex!! }
faviconAtlasStore.write(payload.atlasPng, coords)
```
Keyed by the CSV `url` column (the stream address), **not** the `homepage` column the packer fetched the
icon from. Called on every import, wrapped in its own try/catch so a sidecar failure never aborts the DB
merge.

---

## 7. Backward / forward compatibility **[CONTRACT]**

1. **New app, old catalog** (no `favicon_index`, no atlas entry): every row's `faviconIndex == null` ->
   empty coords -> `write(null, {})` -> atlas deleted, coords `{}` -> every row renders no-favicon. No crash.
2. **Old app, new catalog** (has the column + atlas entry): a pre-S0668 app never reads a `favicon_index`
   column it doesn't know (extra columns tolerated) and never matches the `favicon-atlas.png` entry by
   name (skipped like any unknown entry). No crash; images simply invisible to that old app. This is why
   the packer writes the CSV entry **first** (an old app's zip walk reaches `streams.csv` without
   streaming past the whole atlas).
3. **Atlas oversized**: dropped on read (>4 MB) or never bundled by the packer (>3 MB); CSV still processed.
4. **Stale coords vs a smaller atlas**: `isInBounds` rejects -> no thumbnail. (Normally prevented because
   both files are rewritten together from one import.)
5. **Publish guard (S0925)**: the packer refuses to publish a CSV with any `favicon_index` but no bundled
   atlas (override `-AllowFaviconlessPublish`) - a producer-side safety net for case #1, since shipping
   indices without an atlas would wipe every user's working favicons on next import.

---

## 8. Consumers - every thumbnail-resolution site

Common pattern (independently duplicated per class, not shared):
```
url   = <channel's stable URL string>
index = coords[url]                          // null => no favicon
tile  = index?.let { slicer.tileFor(it) }    // suspend; null => absent atlas / out-of-bounds / decode fail
```
`coords` is loaded via `FaviconAtlasStore.coords()` once per screen-open / dialog build / collection cycle.
Every list/grid holder is **rebind-safe**: it records `boundUrl` before the async decode, drops a stale
result if rebound, and cancels the in-flight decode `Job` on recycle.

| Consumer | Resolver key | No-tile fallback |
|---|---|---|
| Streams LIST row (`StreamSourceAdapter`) | `source.url` | **country-flag glyph** if `country` set, else empty slot (only consumer with the flag fallback, S0785) |
| Streams GRID tile (`StreamGridAdapter`) | `source.url` | nothing painted (favicon is only the "no live frame yet" placeholder) |
| Main-window pinned-channel chip (`StreamPanelChannelAdapter`) | `source.url` | label-only |
| Favorites list row (`AdapterThumbnailLoader.loadStreamFavicon`) | `file.path` (= the channel URL for a STREAM-kind Favorites row) | generic kind vector (`ic_audio`/`ic_video`) |
| Launcher "pick a stream" dialog | `source.url` | `ic_cast` generic icon |
| Launcher desktop gadget (`StreamsGadget`) | `source.url` | `ic_cast` (tinted); the favicon bitmap itself is never tinted |
| Pinned home-screen shortcut icon (`StreamShortcutPinManager`, S1067) | `coords[source.url]` -> `Bitmap?` | generic AUDIO/VIDEO vector; uses `createWithBitmap` (not adaptive - adaptive masking would crop the square favicon ~25%) |

### 8.1 UI geometry per surface

| Surface | ImageView | Size | scaleType |
|---|---|---|---|
| LIST row | `ivFavicon` | 24x24 dp | `fitCenter` |
| LIST row flag fallback | `tvFaviconFlag` (glyph) | 24x24 dp, 18sp | text |
| GRID tile | `ivFrame` | column width x 16:9 | `centerCrop` (a 32px favicon is upscaled to fill -> visibly soft by construction) |
| Main-panel chip | `ivChannelFavicon` | 28x28 dp | `fitCenter` |
| Favorites row | shared file-thumbnail slot | generic | `CENTER_INSIDE` (favicon), `CENTER_CROP` (real tile) |
| Offscreen capture TextureView | (code) | 640x360 px | raw decoder surface, pushed off-viewport |

---

## 9. Grid-mode live-frame thumbnails (S0675)

In grid mode, an http(s) **VIDEO** tile shows a **captured current frame** of the stream; the favicon is
only the placeholder until a frame lands, and the **permanent** rendering for AUDIO/RTSP/non-http (which
are never captured). Three collaborating classes, all keyed by URL:

### 9.1 `StreamFrameCache` (in-memory)
- LRU `LinkedHashMap` (`accessOrder = true`), `MAX_ENTRIES = 64`, capacity-eviction only.
- `FRAME_TTL_MS = 60_000` (60 s) governs **freshness only**, not eviction.
- `get(url)` **always** returns the last frame once any exists (live or restored), regardless of age
  (S0784 - a tile never reverts to the favicon once it has shown a real frame). `null` only if never captured.
- `isFresh(url)` = a **live** entry younger than 60 s (a disk-restored entry is never "fresh", so the
  engine always attempts one real capture for it).
- `put` = live entry; `putRestored` = seeds a restored entry only if none exists.

### 9.2 `StreamFramePersistentStore` (on-disk cold-start layer)
- Directory `filesDir/stream_frames/`; filename = `SHA-256(url)` hex + `.jpg` (the hash IS the key, no
  mapping file).
- `save` = JPEG quality 75, temp-then-rename, then `enforceCap`; evicts oldest-by-mtime until the total
  `.jpg` footprint fits `MAX_DISK_BYTES = 150 MB` (size budget, not a file count - S1130).
- Broad catch-to-null on all I/O; failure just falls back to the favicon next bind.

### 9.3 `StreamFrameSnapshotManager` (offscreen capture engine)
- Scope: **only** http(s) VIDEO (`mediaKind == "VIDEO"` && http/https). AUDIO/RTSP never captured.
- Pipeline: a transient 640x360 `TextureView` added to an Activity-owned off-viewport host
  (`translationX = -10000f`), a **muted** ExoPlayer with a shallow `DefaultLoadControl`
  (`bufferDurations 2000/8000/1000/1000`) and a `LiveConfiguration` (target 10s, min 4s, max 20s),
  awaiting `onRenderedFirstFrame` bounded by `CAPTURE_TIMEOUT_MS = 12_000` (raised from 6 s for slow HLS).
  On success `tv.getBitmap(640,360)` -> cache put + fire-and-forget disk save. `finally` always detaches
  the video surface, releases the player, removes the view.
- Concurrency: `MAX_CONCURRENT_CAPTURES = 1` (Semaphore(1)); a queue + pending-set dedups URLs.
  `request(url, force=false)` skips if `isFresh`; `force=true` (pull-to-refresh) re-captures anyway.
  `onOutcome(url, ok)` is reused directly as the tile's green/red status (no separate probe for VIDEO grid
  tiles).
- History: this TextureView approach (S0933) replaced an earlier `ImageReader` approach (S0700) that
  native-killed the process on some HW decoders (Samsung Exynos / API 36). `CAPTURE_ENABLED = true` is
  kept as an explicit kill-switch.

### 9.4 Precedence (`StreamGridAdapter.kt:168-181`)

```
frame = StreamFrameCache.get(url)
if (frame != null)  -> show frame            // (1) cached frame (any age) always wins
else                -> show favicon tile      // (2) favicon placeholder
                       if isCaptureableVideo -> requestCapture(url)   // (3) fill (1) later
```
Cadence (`StreamGridModeManager`): prewarm from disk on entering grid mode; periodic re-capture of
**visible** tiles every 60 s (`force=false`); on scroll re-request the visible range; pull-to-refresh
re-captures visible tiles with `force=true`. Grid sizing: `MIN_TILE_WIDTH_DP = 160`, landscape multi-column
list floor `MIN_LIST_COLUMN_WIDTH_DP = 360`.

---

## 10. Size / timeout budget cross-reference

| Budget | Value | Side |
|---|---|---|
| Whole-zip download deadline | 30 s (`CATALOG_CALL_TIMEOUT_SECONDS`, OkHttp `callTimeout`) | app |
| CSV entry accept cap | 8 MB (`MAX_CSV_BYTES`) | app |
| Atlas entry accept cap | 4 MB (`MAX_ATLAS_BYTES`) | app |
| Atlas **publish** cap | 3 MB (`$MaxAtlasBytes`) - stricter, enforced before the app sees it | packer |
| Per-favicon fetch timeout | 8 s (`$FaviconTimeoutSec`) | packer |
| Favicon fetch concurrency | 16 (`$FaviconThrottle`) | packer |
| Live-frame capture timeout | 12 s (`CAPTURE_TIMEOUT_MS`) | app |
| Live-frame TTL / periodic refresh | 60 s / 60 s | app |

(The 4 MB app accept vs 3 MB packer publish gap is headroom, not a two-tier design.)

---

## 11. Reimplementer flags (facts to preserve when reusing the bank)

1. Coords JSON keys are the **stream url**; the packer fetches favicons from the **homepage** column - two
   different URL fields per row; only the stream url is ever a coords key.
2. "In bounds" is a canvas-geometry check, not a "was packed" check - an unpacked partial-last-row index
   decodes to a transparent tile (section 1.4).
3. `favicon_index` is **not stable** across catalog builds; always resolve against the same-zip atlas.
4. The country-flag no-favicon fallback exists **only** on the Streams LIST row; all other surfaces show a
   plain empty slot or a generic icon.
5. A captured grid frame, once it exists for a url, **permanently** outranks the favicon (S0784).
6. Atlas tiles are packed in favicon-decode order, not sorted or CSV-position order.

---

## 12. Ticket index for this file

S0668 (atlas: column + packer + import + slicer + list render), S0675 (grid frame capture), S0700 (probe
outcome reuse + capture timeout), S0712 (persistent frame store + prewarm), S0783 (Favorites-list stream
favicon), S0784 (always-show-last-frame), S0785 (list country-flag fallback), S0925 (publish guard), S0933
(TextureView capture), S1067 (shortcut icon favicon).

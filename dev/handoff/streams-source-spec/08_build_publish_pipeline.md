# Streams Source Spec - 08 - Offline Build & Publish Pipeline (the producer side)

Part of the FastMediaSorter "Трансляции" (Streams) source-documentation set. This file documents how the
stream **bank** (`stream-catalog.zip` = `streams.csv` + `favicon-atlas.png`) is produced and published -
the PRODUCER side. The CONSUMER side (how the app downloads and applies it) is in `01_delivery_contract.md`.

This is standing maintainer tooling, run by the owner from a Windows machine - **not** part of the Android
app. It matters to a reuse of the bank only if the reuse wants to **regenerate or extend** the bank (vs
merely consume it). Facts cite `path:line` (root `p:\ANDROID\FastMediaSorter_mob_v2`).

Origin tickets: **S0570** (catalog founding), **S0668** (favicon atlas + offline packer), **S0805**
(deep-signal liveness), **S0925** (publish guard), S0583 (timeout budget), S0843 (webcam seeds), S0785
(country-flag fallback), S1106 (onboarding hang).

---

## 1. Script identity

- **One script**: `scripts/streams/collect-stream-candidates.ps1` - 167,720 bytes, 2,902 lines (measured
  2026-08-19; the 2026-07-19 snapshot this file was originally written from measured 67,202 bytes / 1,271
  lines - the script has grown substantially since).
- `#requires -Version 7` - PowerShell 7+ only (uses `ForEach-Object -Parallel`,
  `System.Net.Http.HttpClient` with `CancellationToken`, and `System.Drawing`/GDI+ imaging).
- **No sibling scripts.** All candidate harvesting, liveness probing, favicon fetching, atlas packing, zip
  assembly, and `gh` upload live as PowerShell functions inside this single file. There is no separate
  "packer" script.
- Outbound User-Agent for every HTTP call: `FastMediaSorter-catalog/1.0 (+stream-candidate-collector)`.
- **Windows-only imaging dependency**: the atlas is built with GDI+ via `Add-Type -AssemblyName
  System.Drawing`. On Windows this is native and needs no setup; on Linux/macOS .NET, `System.Drawing.Common`
  needs `libgdiplus` and is largely unsupported since .NET 6. A Windows companion app can call
  `System.Drawing.Common` directly (same API surface) or shell out to this script.

---

## 2. Parameters (28 as of the 2026-07-19 snapshot)

The `param()` block (`:56-126` in the 2026-07-19 snapshot; now `:70-226`). Grouped:

> **Not re-audited for this refresh:** the live `param()` block now declares **64** parameters, not 28 -
> the script has grown substantially since 2026-07-19 (new modes/axes such as `-ArtworkCacheOnly` and the
> `-LogoCacheDir` incremental favicon cache, S1201's separate logo-atlas pass, and more). The groupings
> below are the original 28 and are still accurate for what they describe, but are **not a complete list**.
> Read the live `param()` block for the current full set before relying on flags not named here.

**Discovery axes / sources**
- `-Axis` (`ValidateSet 'livetv','genres','geo','webcam'`, default all four) - which harvest axes to run.
- `-GenreTags` (16 radio-browser genre tags), `-GeoCountries` (15 ISO codes), `-LiveTvCategories` (8
  iptv-org categories) - seed lists.
- `-PerQuery` (default 20) - per-query result cap.

**Modes**
- `-CatalogOnly` - maintenance over the existing CSV (probe/prune/re-publish), no discovery.
- `-PreviewOnly` - discovery + report only; never touches `streams.csv`.
- `-PruneDead` - actually remove dead rows (default is a dry run).

**Liveness**
- `-SkipLiveness` - skip probing entirely.
- `-LivenessTimeoutSec` (12), `-Throttle` (12; auto-bumped to 48 under `-DeepSignal` unless pinned).
- `-DeepSignal`, `-SignalBytes` (16384), `-SignalMinBytes` (2048), `-SignalTimeoutSec` (8) - deep byte probe.
- `-SkipDeepSignal` - skip the S0805 discovery append gate.

**Favicon atlas (S0668)**
- `-WithFavicons` - **default OFF**; when set, fetch each row's favicon from its `homepage`, pack the
  atlas, stamp `favicon_index`.
- `-FaviconS2Fallback` (default ON) - allow the Google s2 third-party fallback.
- `-AtlasPath` (`delivery/stream-catalog/favicon-atlas.png`), `-FaviconTimeoutSec` (8),
  `-FaviconThrottle` (16), `-MaxAtlasBytes` (31457280 = 30 MB publish cap).

**Publish**
- `-Publish` - after the run, zip and upload.
- `-PublishTag` (`delivery-so-v1`), `-AllowFaviconlessPublish` (override the S0925 guard).

**Paths**
- `-ExistingCsv` (`delivery/stream-catalog/streams.csv`), `-OutDir` (`temp`),
  `-CatalogLivenessReport` (`temp/stream-catalog-liveness.csv`), `-PruneStatuses` (`@('dead')`), `-Limit`.

---

## 3. Two modes

### 3.1 Mode A - `-CatalogOnly` (maintenance)

`Invoke-CatalogMaintenance` (`:967-1039`):
1. Load `$ExistingCsv` (throws if missing/empty).
2. `-Limit` + `-PruneDead` together is an error (prune needs a full probe).
3. If `-WithFavicons`: stamp `favicon_index` over the **full** catalog (`Set-FaviconIndices`, which also
   builds the atlas PNG) **before** probing. If not also pruning, back up and rewrite the CSV here (the
   "just rebuild atlas + CSV" path used by `-WithFavicons` alone).
4. Optional `-Limit N` slices the probe set.
5. Probe: `-SkipLiveness` -> none; `-DeepSignal` -> `Invoke-SignalProbe`; else -> `Invoke-LivenessProbe`.
6. Write the liveness report (`temp/stream-catalog-liveness.csv`); print a status histogram.
7. Compute prune candidates = rows whose status is in `-PruneStatuses` (default `dead` only, never unknown).
8. Without `-PruneDead`: dry run ("Would prune N"). With `-PruneDead`: back up, filter the full catalog to
   survivors, rewrite the CSV.
Then `if ($Publish) Invoke-PublishCatalog`.

### 3.2 Mode B - default (discovery)

Main body (`:1123-1271`):
1. **Axis collection** - harvest candidates per `-Axis` from the sources in section 4.
2. **Dedupe** - two HashSets from the existing catalog: `url` (lower/trimmed) and `host|name`; drop
   candidates already in-catalog or repeating in-batch.
3. **Header liveness probe** (`Invoke-LivenessProbe`), then the **S0805 deep-signal append gate**: unless
   `-SkipDeepSignal`, the header-alive subset is re-probed with `Invoke-SignalProbe` for real media bytes;
   a header-2xx-but-no-segment row is downgraded and **not** appended.
4. Write `temp/stream-candidates-report.csv` (all rows + diagnostics) and `temp/stream-candidates.csv`
   (kept rows only).
5. `-PreviewOnly` returns here (CSV untouched).
6. **Append**: if zero new alive rows, return. Else `merged = existing + new`; if `-WithFavicons`, stamp
   `favicon_index` over the **full merged set** (so existing rows' favicons are recomputed too); back up;
   rewrite the CSV.
7. `if ($Publish) Invoke-PublishCatalog`.

---

## 4. Candidate sources (discovery mode)

| Axis | Source | Notes |
|---|---|---|
| `genres` | radio-browser `bytagexact` over 16 genre tags | `category='Radio'`, topic = station's first tag; requires `lastcheckok == 1` |
| `geo` | radio-browser `bycountrycodeexact` + iptv-org, over 15 under-represented ISO codes | topic `General` (radio) / mapped (iptv) |
| `livetv` | iptv-org `channels.json` + `streams.json`, 8 categories | `category='Live TV'` |
| `webcam` | 12 hard-coded 24/7 public HLS feeds (`Get-WebcamSeeds`) | refreshed under S0843; CDN paths rotate, re-verify with `-Axis webcam -PreviewOnly` |

- **radio-browser** mirrors (de1/de2/nl1/at1), first success wins; `hidebroken=true&order=clickcount`.
- **iptv-org** downloaded once per run (memoised). Inclusion filter drops blank URL, any stream requiring a
  `referrer`/`user_agent` (the app can't supply those), unresolvable channel ids, and closed channels;
  keeps everything else including grey-area restreams (see policy note, section 9).
- `New-Candidate` normalizes name/url, derives `https` from the scheme, and carries five in-run bookkeeping
  fields (`axis`, `score`, `liveness_status`, `http_code`, `liveness_note`, `dup`) that are stripped by
  `Select-Object $Schema` before anything reaches `streams.csv`.
- `Get-ProtocolFromUrl`/`Get-FormatFromUrl`: extension sniffing (`.m3u8`->HLS, `.mpd`->DASH,
  `.aac/.mp3/.ogg`, `rtsp://`->RTSP; else `protocol='ICECAST'`).

---

## 5. Liveness probes (two depths)

### 5.1 Header probe - `Invoke-LivenessProbe` (default)
- Runspace-parallel (`-Throttle`, default 12), per-URL `-LivenessTimeoutSec` (12 s).
- `rtsp://` -> raw `TcpClient` connect probe (port from URL or 554); alive on connect, dead on refused,
  else unknown (never auto-dead on timeout).
- `http(s)` -> `HttpClient` with `ResponseHeadersRead` (**body never downloaded**), redirect cap 6, header
  `Icy-MetaData: 1` (so Icecast/Shoutcast non-HTTP `ICY 200 OK` replies are handled). HEAD first, GET on
  HEAD failure/non-2xx.
- Mapping: 2xx/3xx -> alive; 404/410 -> dead; else unknown. Exception patterns classify DNS-fail/refused ->
  dead; ICY/status-line -> alive; timeout -> unknown.

### 5.2 Deep-signal probe - `Invoke-SignalProbe` (`-DeepSignal` / S0805 append gate)
Pulls **real media bytes** (`-SignalBytes` cap 16 KB, alive threshold `-SignalMinBytes` 2 KB, per-fetch
`-SignalTimeoutSec` 8 s, cancellation-bounded so an endless live body is never fully downloaded):
- **RTSP**: raw-socket `OPTIONS ... RTSP/1.0` handshake; `RTSP/1.0 200` -> alive.
- **HLS**: fetch the playlist; if a master (`#EXT-X-STREAM-INF`), resolve the first variant and re-fetch;
  then pull the first `#EXT-X-MAP` init segment or first media segment. `>= SignalMinBytes` -> alive; 404 ->
  dead; a playlist with **no segment line** -> dead ("declared but not playing", the target case).
- **DASH**: fetch the manifest; alive only if it contains `<MPD`.
- **ICECAST/progressive**: pull raw bytes; same threshold.

### 5.3 Composition (load-bearing asymmetry)
- Discovery: header probe first, then deep-signal verify (unless `-SkipDeepSignal`); a row must survive
  **both** to be appended. `-SkipLiveness` skips both.
- Maintenance: header **or** deep-signal (not both), for reporting/pruning.
- **APPEND is strict** (deep-signal gated); **PRUNE is conservative** (header-only, human-driven via
  `-PruneDead`, dead-only, never unknown). Rationale: a geo-restricted stream can read dead from the
  maintainer's network yet work on a user's device.

---

## 6. Favicon atlas packer (S0668, inside the same script)

Gated by `-WithFavicons` (default OFF). Header constants (`:779-780`): `$script:FaviconTile = 32`,
`$script:FaviconCols = 16` - the same grid the app slices (see `04_favicon_atlas.md`).

### 6.1 Acquisition - `Get-FaviconBytes($homepage)` (`:786-847`)
Source is the row's **`homepage`** column (the channel's website), never the stream URL. Blank homepage ->
skipped, no fetch. Fallback chain (first success wins, failures swallowed):
1. `GET {scheme}://{host}/favicon.ico`.
2. Parse the homepage HTML for `<link rel="icon"|"shortcut icon"|"apple-touch-icon" href=...>` (resolve
   relative), fetch it.
3. `https://www.google.com/s2/favicons?domain={host}&sz=32` (gated by `-FaviconS2Fallback`, `sz=32` to
   match the tile).

### 6.2 `Build-FaviconAtlas` (`:855-950`)
1. `Add-Type System.Drawing` (GDI+).
2. Fetch **distinct** homepages once each, parallel (`-FaviconThrottle`, default 16), `-FaviconTimeoutSec` 8 s.
3. Decode + normalize single-threaded (GDI+ not runspace-safe): walk `$Rows` **in order**; a row enters
   `$packable` only if its homepage decoded (`System.Drawing.Image.FromStream`, handles ico/png/gif/jpg);
   corrupt images are skipped, not fatal.
4. **Packing math** (the geometry contract):
   ```
   rowsNeeded = ceil(packable.Count / 16)
   atlasW     = 16 * 32 = 512    (always)
   atlasH     = rowsNeeded * 32
   Graphics.Clear(Transparent); InterpolationMode = HighQualityBicubic; PixelOffsetMode = HighQuality
   for i in 0..packable.Count-1:
       col = i % 16; row = floor(i / 16)
       DrawImage(packable[i].Bitmap, Rectangle(col*32, row*32, 32, 32))   // scales source -> 32x32
       map[packable[i].Url] = i                                           // zero-based ordinal, keyed by STREAM url
   ```
   The source favicon (any native size) is scaled directly into the 32x32 cell by the single `DrawImage`
   blit (no separate resize; no aspect preservation). Ordinal = `$packable` index = **favicon-decode
   order**, not CSV row position; unfetched rows get no ordinal and no gap. The returned map is keyed by the
   **stream url** (multiple rows sharing a homepage each get their own url->ordinal pointing at the same tile).
5. Save PNG to `$AtlasPath` (default `delivery/stream-catalog/favicon-atlas.png`), backing up any prior file
   first. If **zero** favicons decoded, no file is written (an existing atlas is not overwritten with empty).

### 6.3 `Set-FaviconIndices` (`:955-965`)
Calls `Build-FaviconAtlas`, then stamps each row's `favicon_index` from the returned map (blank for
unmatched rows), **before** the caller's `Write-CsvUtf8` persists the CSV - this is what keeps the on-disk
`favicon_index` consistent with the atlas built in the **same** call. Invoked from both maintenance (full
catalog) and the discovery append path (full merged set).

---

## 7. `streams.csv` producer output

- Written by `Write-CsvUtf8` = `$Rows | Select-Object $Schema | Export-Csv -NoTypeInformation -Encoding utf8`.
- PS7 `-Encoding utf8` = **UTF-8 without BOM** (unlike Windows PowerShell 5.1). All fields quoted.
- Column order = the `$Schema` array (19 columns, `favicon_index` second-to-last, `access` last, existing
  columns never reordered). See `03_catalog_format.md` for the full column table.
- Live snapshot (measured 2026-08-19): 5,834,634 bytes, 19,534 data rows - AUDIO 16,616 / VIDEO 2,917 /
  RTSP 1; `favicon_index` populated on 5,624 rows (highest index in use 5,742), blank on 13,910.

## 8. `favicon-atlas.png` producer output

- `delivery/stream-catalog/favicon-atlas.png`, 6,992,874 bytes (~6.67 MiB), **512 x 11,488 px** = 16 x 359
  tiles = 5,744 capacity. Under the 30 MB publish cap.
- **`favicon-coords.json` is NOT produced here** - it does not exist on the producer side at all. The app
  derives it at import time from the CSV `favicon_index` column (see `01`/`04`).

---

## 9. Publish - `Invoke-PublishCatalog` (`:1052-1115`)

Requires the `gh` CLI on PATH (`C:\Program Files\GitHub CLI\gh.exe`, not on PATH by default). Steps:

1. **CSV first** (entry 0): `Compress-Archive -Path streams.csv -DestinationPath temp/stream-catalog.zip -Force`
   creates a zip whose sole first entry is the CSV. `Compress-Archive` does not guarantee entry order for a
   multi-path call, so the CSV is packed **alone first** deliberately.
2. **Atlas appended** (`-Update`) only if it exists **and** `size <= $MaxAtlasBytes` (30 MB). Over the cap ->
   warning, CSV-only publish (the S0583 30 s import-timeout budget rationale).
3. **S0925 guard** (`:1082-1091`): if the atlas was NOT bundled (missing or over-cap) **and** the CSV has
   any row with a numeric `favicon_index`, **throw and refuse to publish** unless `-AllowFaviconlessPublish`.
   Rationale: shipping indices without an atlas triggers the app's null-atlas import path, which **wipes
   every user's favicons** app-wide.
4. **Self-check invariant**: re-open the built zip (`System.IO.Compression.ZipFile.OpenRead`) and throw if
   entry 0 doesn't end with `streams.csv`, or if no entry does.
5. **Upload**: `gh release upload delivery-so-v1 temp/stream-catalog.zip --clobber` (overwrites the asset in
   place; no versioned asset history beyond the git history of the source CSV).

Publish target matches the app's `CATALOG_URL` exactly: tag `delivery-so-v1`, asset `stream-catalog.zip`.
Note: `delivery-so-v1` also hosts SHA-pinned `.so`/`.mp4` assets, but the stream catalog is the **one
mutable, un-pinned** asset on that tag and is fetched by wholly separate logic (`ImportStreamCatalogUseCase`,
no hash pinning). A different `gh release upload` in `scripts/release/publish-github-release.ps1` uploads the
APK/AAB to a `v<version>` tag - a completely separate release; do not conflate.

---

## 10. Temp artifacts (per project convention, `temp/` root)

| Path | Written when | Contents |
|---|---|---|
| `temp/stream-catalog.zip` | every `-Publish` | the exact bytes uploaded to the release |
| `temp/stream-catalog-liveness.csv` | every `-CatalogOnly` | per-row probe status |
| `temp/stream-candidates.csv` | every discovery run | kept `$Schema` rows |
| `temp/stream-candidates-report.csv` | every discovery run | full report + diagnostics |
| `temp/<name>.<timestamp>.bak` | before every CSV/atlas overwrite | verbatim pre-write copy (throws if backup verification fails) |

---

## 11. Operational commands (real usage)

- **One-shot refresh + publish** (rebuild atlas + push): `pwsh -NoProfile -File
  scripts/streams/collect-stream-candidates.ps1 -WithFavicons -Publish`.
- **Safer two-step**: run `-WithFavicons` **without** `-Publish` first, validate `streams.csv` (19 cols,
  `favicon_index` in range) and the atlas (512 wide, < 30 MB), then `-CatalogOnly -SkipLiveness -Publish`
  (no re-probe/re-atlas).
- **Re-publish an already-consistent pair** (no favicon re-fetch): `-CatalogOnly -SkipLiveness -Publish`.

> **Publish-safety rule (S0925 recurrence lesson):** the S0925 guard lives **only** inside
> `Invoke-PublishCatalog`. Any raw `gh release upload delivery-so-v1 stream-catalog.zip` (or raw
> `Compress-Archive` of just the CSV) bypasses it and can silently drop the atlas app-wide - which happened
> twice (2026-07-04, 2026-07-12). Always publish through the guarded packer. (See parked ticket **S1108** for
> the stale README snippet that reproduces this hazard.)

Consistency self-check for a manual atlas/CSV pair: `max(favicon_index) < (atlasWidth/32) * (atlasHeight/32)`.
The app guards out-of-bounds indices (no crash), but a mismatched atlas can show the **wrong** tile for an
in-bounds index packed against a different ordering.

The app re-fetches the catalog (and atlas) only on an **explicit** user action (Streams refresh/import, or
Welcome enable-all onboarding) - never automatically on app update. A user who imported a broken (atlas-less)
catalog keeps seeing no favicons until they manually re-import, even after the server asset is fixed.

---

## 12. Ticket index for this file

S0570 (founding catalog), S0583 (30 s import budget), S0668 (favicon atlas + packer), S0805 (deep-signal
liveness + append gate), S0843 (webcam seeds), S0785 (country-flag fallback symptom), S0925 (publish guard),
S1106 (onboarding hang). Parked during this documentation pass: **S1108** (stale README publish snippet).

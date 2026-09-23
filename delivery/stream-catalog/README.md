# FastMediaSorter curated stream catalog

A curated, human-maintainable catalog of **clearly-free** internet audio, video and RTSP streams,
organised by rubric, topic and language. The app's Streams screen ("Трансляции") "Import list"
action downloads this catalog from our GitHub, parses it, and merges the entries into the user's
local stream list (de-duplicated by URL, keeping the user's pins and order). Re-running the import
updates the list with newly-added entries.

This is a downloadable resource distributed like the other on-demand extensions in
[`../INVENTORY.md`](../INVENTORY.md), but it is intentionally **mutable** - we revise it periodically
and the change ships to users without an app release.

> **The format is `STREAM-BANK` 2.1 and is not defined here.** The CSV columns, the atlas and tile-pack
> geometry, the ZIP container rules, the merge that never deletes a user's own rows and the shape of
> `artwork-manifest.json` all live in the shared contracts catalog, because a second player reads them
> and a copy in the producer's own tree is how two readers start to disagree.
> `docs/CROSS_PROJECT_CONTRACTS.md` says where it is. This file is the **producer's operating page**:
> what is live today, which command publishes it, and what the maintainer must not do by hand. The
> catalog's 2026-08-20 amendment points back here for exactly one thing - which asset revision is
> current, which is a fact about today rather than a contract.

## Hosting (GitHub Release asset, zipped)

Distributed the same way as the other downloadable extensions: as a **GitHub Release asset** under the
permanent delivery tag, packaged as a zip:

```
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip
   (zip contains: streams.csv (entry 0, always first) + optional favicon-atlas.png)
```

- `streams.csv` is committed to the repo as the source of truth (full git history of revisions).
- For distribution it is zipped together with `favicon-atlas.png` and uploaded as the release asset.
- Unlike the immutable `.so`/`.mp4` delivery assets, the catalog is **not SHA-pinned** - it is meant to
  change; the app always fetches the latest asset and merges idempotently by URL.
- The optional `delivery-so-v1/delivery-manifest.json` URL-override (delivery pattern) can repoint the
  asset without an app release.

Publishing the asset (the ONLY safe path):

```
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -Publish -SkipLiveness
```

> **Never** hand-package the catalog with `Compress-Archive` + `gh release upload`. A CSV-only zip ships
> a catalog whose `favicon_index` column points at a missing atlas: the app receives `atlasPng=null`,
> `FaviconAtlasStore.write(null, coords)` wipes the atlas, and **every** channel loses its favicon
> app-wide (S0785 2026-07-03; recurred 2026-07-12). The command above (`Invoke-PublishCatalog`) carries
> the S0925 guard - it bundles `streams.csv` (entry 0) **and** `favicon-atlas.png` (<= 30 MiB) and refuses
> to publish a `favicon_index` CSV with no atlas. `-SkipLiveness` skips the URL probe without touching the CSV.

### Atlas byte ceiling - 31457280 B (30 MiB), shared with three consumers (S1827)

The favicon atlas is bundled only while it fits **31457280 bytes** - our side of the ceiling, held here
as `$MaxAtlasBytes` in `scripts/streams/collect-stream-candidates.ps1`, where `Assert-AtlasBudget` stops
the build and rolls the atlas back rather than letting publish bundle the CSV alone. The number is not a
local choice and neither is what a consumer does when it is exceeded: the `STREAM-BANK` consumer
registry names every code base that carries its own copy, and the amendment of 2026-08-20 says what each
must do. Read it before changing the number. `-AllowFaviconlessPublish` is the acknowledged override.

Current occupancy, measured 2026-08-20: **6 992 874 B = 22,2 %** of the ceiling, 5 743 tiles packed at
about 1 218 B per tile - room for roughly 25 800 tiles at that density. Every atlas build now prints
this line, so the headroom is visible per run rather than discovered at publish.

## Channel preview atlas (separate release asset)

The **channel-preview atlas** is an optional companion to the catalog: a single sprite sheet of
per-channel preview frames that the app shows for a VIDEO channel in grid mode before the user's first
watch. It is published as its **own** versioned release asset - NOT bundled inside `stream-catalog.zip` -
because it is large (10-50 MB; the 2026-08-20 build is 15.9 MB) and has an independent lifecycle from the CSV.

```
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/channel-preview-atlas-v2.webp
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/channel-preview-coords-v2.json
```

The `-v2` suffix is the element revision: a rebuilt atlas that is not tile-compatible is published
under a new suffix, so an older app keeps resolving the payload it was pinned against. The `-v1` pair
from 2026-07-26 stays published unchanged for consumers pinned against it.

The sheet's geometry, its two ceilings and the rule that a consumer derives the row count from the image
rather than assuming one are `STREAM-BANK`, in the atlas document and the 2026-08-20 amendment - not
here. What this page records is the state of the build:

- The 2026-08-20 build is `8160 x 11340` with 2830 tiles in 84 rows, encoded at 15.9 MiB - roughly a
  third of the ceiling the consumer declared for this asset.
- Until 2026-08-20 the sheet was capped at 60 rows (2040 tiles) by a self-imposed `8192 x 8192` budget,
  and anything past it was dropped with a warning while the run still succeeded - 877 of 2917 video
  channels had no tile for that reason alone. A build that cannot place every tile now fails and names
  how many channels it would have left uncovered (S1831).
- Only VIDEO channels have a tile; audio/radio rows are skipped by the packer. A channel that did not
  answer during the capture pass also has no tile - 87 of 2917 in the 2026-08-20 build, and that is now
  the *only* reason a video channel lacks one.
- Rebuild command: `pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithChannelPreviews -PublishPreviewAtlas`
  (needs `ffmpeg` and `gh`; captured frames are cached under `temp/channel-preview-frames/`, so an
  interrupted pass resumes instead of recapturing). Add `-PreviewFromCacheOnly` to repack from the cache
  and open no stream at all - the way to rebuild the sheet without spending requests on broadcasters.

The sidecar `channel-preview-coords.json` and its `url -> index` keying are `STREAM-BANK` as well. The
one thing worth repeating in an operator's page: the geometry is a shared invariant between this offline
packer and every slicer, so changing it on one side alone drifts every rect on the other.

## Station logo atlas (separate release asset)

The **stream logo atlas** covers what the preview atlas structurally cannot: a station with no video
track has no frame to capture, so every radio channel would otherwise be stuck with a 32 px favicon. It
is a sprite sheet of station logos, published as its own versioned release asset for the same reasons
as the preview atlas, and downloaded/refused independently of it.

```
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-logo-atlas-v2.webp
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-logo-coords-v2.json
```

The grid, the square tile, the even side, the transparent padding and the consumer's obligation to
letterbox and decode ARGB are `STREAM-BANK` - the atlas document and the 2026-08-20 amendment. State of
the build: the 2026-08-07 sheet is `8024 x 4624`, 6.9 MB, 2006 tiles covering 2350 channels. This tier
is not restricted to radio - a video channel whose frame capture failed lands here too.

Source artwork comes from the favicon crawl's cache, `temp/stream-logo-src/`, keyed by SHA-1 of the
station homepage. A `<hash>.img` is the largest artwork that site offered (apple-touch-icon, og:image,
icon links, `/favicon.ico`); a `<hash>.img.miss` marker records a site that offered nothing, so a rerun
does not re-crawl it. Two filters apply before packing:

- Sources below **96 px** on the larger side are skipped - that is a tab icon, and upscaling it is the
  problem this atlas exists to avoid. Those stations fall through to the favicon tier.
- Tiles are de-duplicated **by cache file**, not by url: several stations of one network share a
  homepage, and giving each its own copy of the identical logo wasted ~300 slots. All urls of such a
  group point at the same index.

Rebuild command: `pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithStreamLogos -PublishStreamLogoAtlas`
(needs `ffmpeg` and `gh`; reads only the cache, so it costs minutes and no network). The app-side half
of the geometry contract is `StreamLogoAtlasSlicer` - change one side without the other and every rect
drifts. A rebuilt sheet that is not tile-compatible gets the next element revision plus fresh SHA-256
pins in `DeliverableDescriptorCatalog.streamLogoAtlas()`, never a silent re-upload under the same name.

## Tile packs - the same artwork, addressable one tile at a time

Both sprite sheets above are also published cut into **tile packs**, and that is what the app itself
downloads. A sheet is not randomly addressable: a WebP decoder walks the stream from the top to reach
a given row, so cropping one tile out of the `8160 x 11340` preview sheet costs a share of a full
61,7 Mpx decode (measured at 1,48 s on a desktop). A grid asking for one tile per cell filled in one
cell at a time. Each pack entry is its own small image, so a tile costs one small decode.

```
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/channel-preview-tiles-v3.zip
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-logo-tiles-v3.zip
```

The container - a ZIP of STORED entries named by decimal slot index - is `STREAM-BANK`, item G2 of the
2026-08-20 amendment. State of the build: the 2026-08-07 packs hold 1949 preview entries (10,7 MiB) and
2006 logo entries (6,0 MiB), cut from the published sheets in lossy WebP, with alpha on the logo tiles,
so an index resolves to the same picture in either container.

- Rebuild command: `pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithTilePacks -PublishTilePacks`
  (needs `ffmpeg` and `gh`; reads a finished sheet, so it costs seconds).

The sheets stay published unchanged - a consumer that prefers cropping one sheet can keep doing so.

### `artwork-manifest.json` - what is currently live (S1483)

```
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/artwork-manifest.json
```

The app fetches the tile packs under **stable names** - `channel-preview-tiles.zip`,
`channel-preview-coords.json`, `stream-logo-tiles.zip`, `stream-logo-coords.json` - and learns that a
rebuild happened from this manifest, published by the same run that uploads the payload.

Its shape and the rules around it are `STREAM-BANK`, item L of the 2026-09-22 amendment - written there
on 2026-09-22, when this page turned out to be the only place in the portfolio that described a file
every consumer had already been told to poll.

What stays here, because it is about our publishing and not about the format: the revisioned `-vN` packs
are **never deleted**. Builds shipped before S1483 pin them by hash and would lose their artwork if
those names disappeared.

### Why a rebuild needed fresh pins before S1483 (S1200)

Both atlases above say a rebuilt sheet takes a new element revision plus new SHA-256 pins. That is not
bookkeeping - the pins are what the app compares an installed copy against to notice it is out of date.
A re-upload under the same asset name reaches nobody: the download would work, but no installed copy
would ever look stale, so nobody is offered it. Publishing a rebuilt atlas therefore means shipping the
new pins in an app build, and the mirror is deliberately not allowed to change hashes at runtime.

## File: `streams.csv`

UTF-8, no BOM, RFC-4180 (fields with `,` `"` or newline are quoted; inner `"` doubled).
First row is the header. One stream per row, grouped by `media_kind`, then `category`, then `topic`,
then `name` - a maintainer convention, not something a consumer may rely on.

**The 19 columns, their value sets and their blank defaults are `STREAM-BANK`**, in the catalog's
catalog-format document. They are not restated here: a consumer matches by header name and a second
copy of the column list in the producer's own tree is how the two stop agreeing. What binds the
producer is narrower and belongs on this page - existing columns are never reordered and never
removed, and a new column is appended at the end.

Two of the folding rules the producer applies before writing a cell are ours and are documented with
the scripts that apply them: facet normalization (S2233) and name repair (S2645), both below.

## Inclusion policy

The catalog accepts a live TV stream only when it has both a playable signal and explicit source
provenance. Liveness is necessary but not sufficient: a responsive restream is not promoted merely
because it answers an HLS request.

Allowed live TV sources are:

- Direct public HLS feeds published by the broadcaster or public institution, including Red Bull TV,
  Bloomberg TV, Euronews, DW, France 24, Al Jazeera, CGTN and RT India. NASA Live is admitted only
  when its current official event feed passes the same segment-level signal check.
- A deliberately small subset of [iptv-org](https://github.com/iptv-org/iptv), where both the stable
  channel id and actual delivery host are in the collector's official-source allowlist. The index is
  a discovery aid, not proof that every listed stream is authorised.
- Other direct broadcaster sites after a maintainer verifies that the page and HLS delivery host
  belong to the same broadcaster or its documented CDN.

The collector does not import grey-area IPTV restreams, anonymous IP-address streams, or an
unreviewed iptv-org entry. Existing catalog rows are not retroactively deleted by discovery; review
them separately before any future curated rebuild.

### Community sources (S1476, opt-in axes)

Added 2026-08-07. Each is keyless by requirement - no token, no registration - and each is its own
`-Axis` value, absent from the default set so a routine collection run keeps its current cost.

| Axis | Source | What it contributes |
| --- | --- | --- |
| `lautfm` | [laut.fm](https://api.laut.fm/stations) | ~15,8k private German community stations, one request for the whole index. Station artwork comes from the API (`images.station_*`) and is seeded straight into the artwork cache - every station's page is on the same domain, so a homepage favicon crawl would stamp one identical platform icon on all of them. |
| `xiph` | [Xiph Icecast YP](https://dir.xiph.org/yp.xml) | ~12,7k self-hosted stations that opted in to the public directory. Only ~5% are HTTPS; plain `http` is admitted here and recorded truthfully in the `https` column. |
| `webradiodb` | [WebRadioDB](https://jcorporation.github.io/webradiodb/) | ~700 curated, CI-validated stations - the best per-station metadata of any source here. |
| `iptvcam` | iptv-org `weather` / `outdoor` / `travel` / `relax` | Public webcams already in the index the collector downloads. Reached by category instead of the broadcaster allowlist, which no webcam can ever satisfy. |
| `tfl` | [TfL JamCams](https://api.tfl.gov.uk/Place/Type/JamCam) | 882 London traffic cameras, Open Government Licence. **Not live**: each is a short clip republished every few minutes, so rows carry `is_live=false` and their own `Traffic cams` rubric. |
| `akc` | akc.tv broadcast ids | Enumerated, not indexed: every id becomes a candidate and the liveness gate decides. |
| `radioparadise` | [Radio Paradise](https://api.radioparadise.com/api/list_streams) | 8 listener-funded channels; the highest non-FLAC variant is taken, since the app cannot negotiate down from FLAC. |

Rejected for cause, so the same ground is not re-searched: Windy and webcams.travel (API key),
Skyline (per-session token), EarthCam (403), IPCamLive and everything built on it (rotating URLs plus
`apisecret`), explore.org / africam / NPS / zoo pages / HDOnTap / FL511 (YouTube-backed, no direct
media), Radio Garden (bot wall), Live365 (401), SHOUTcast YP (developer id), internet-radio.com (no
machine index), Zeno.fm (index carries no stream url), `HasBahCa/m3u_Links` (repository DMCA-blocked).

The collector also drops entries that cannot actually play:

- defunct channels (`closed` in the iptv-org index),
- header-gated streams that require a `referrer` / `User-Agent` the app cannot supply,
- confirmed-dead URLs - DNS failure, connection refused, HTTP 404/410, or an HLS playlist that
  serves no segment data (see the deep-signal probe below),
- non-geo deep-signal failures on a full-catalog prune - timeout / SSL / `401` auth / `5xx`
  (S1117; these are dropped, region-locked `403`/`451` are **kept** and tagged `access=geo`).

Region-restricted channels (`access=geo`) are **kept**: they fail from the maintainer's network but
may play for a user in their own country. They carry the `access=geo` tag so the app can surface a
"may be region-locked" hint instead of a bare failure.

Sources: [radio-browser.info](https://www.radio-browser.info/) community radio,
[SomaFM](https://somafm.com/) listener-supported radio, verified official broadcaster / government live
feeds, vendor TEST/sample streams, and Creative-Commons / public-domain media. iptv-org is used only
through the official-source allowlist described above.

## Maintenance: unified collector

`scripts/streams/collect-stream-candidates.ps1` is now the single entrypoint for both:

- discovering + validating direct official streams and approved iptv-org records, then appending
  them to `streams.csv`
- probing the current catalog and pruning confirmed-dead rows when requested

```
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -PreviewOnly
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -PruneDead
```

- http(s): HEAD/GET with `ResponseHeadersRead` (the endless radio body is never downloaded - reads
  headers only). `2xx/3xx` => alive; `404/410`, DNS failure, connection refused => dead;
  `401/403/429`, `5xx`, timeouts, other `4xx` => unknown (auth/geo/rate/transient, kept conservatively).
- rtsp: TCP connect probe (weak; never auto-dead on timeout).
- Probes run from the maintainer's machine; geo-restricted streams may read dead/unknown locally yet
  work on a user's device. Only drop confirmed-dead (DNS/refused/404/410).

### Deep-signal append gate (discovery default, S0805)

Discovery mode verifies **real media signal** before a new row can be appended. The header probe alone
only reads the playlist status, so a channel whose playlist returns `200` but serves no segment reads
`alive` and would enter the shipped catalog ("pseudo-alive"). To stop that, discovery runs the
deep-signal probe below as a **second stage**: only the header-alive candidates are re-probed for real
media bytes, and only signal-verified rows are appended. The candidate report gains a `signal_bytes`
column showing why a pseudo-alive row was dropped.

- On by default for every discovery run; pass `-SkipDeepSignal` for a fast prowl (header-alive rows
  appended without signal verification, the pre-S0805 behaviour).
- `-SkipLiveness` skips both stages (appends everything, no probing).
- Pruning of the **existing** catalog stays deliberately conservative and is **not** auto-driven by the
  deep signal: a channel dead from the maintainer's machine may be alive on a user's device.

### Deep-signal probe (`-DeepSignal`)

The default header probe only reads the response status of the playlist/manifest URL, so an HLS master
that returns `200` but serves no segments still reads `alive`. `-DeepSignal` (catalog-only) pulls a few
KB of **real media body** to confirm the stream actually carries signal:

- HLS: walks master -> media playlist -> first segment and reads bytes off the segment. Playlist `200`
  but segment `404`/empty => `dead` (the "declared but not playing" case).
- Region-locked (S1117): a playlist / segment / manifest / body returning HTTP `403` or `451` =>
  `geo`, a distinct verdict from `dead`/`unknown` (region-restricted from here, may play in-region).
- DASH: fetches the manifest and confirms it parses as `<MPD>`.
- ICECAST / progressive / direct media: pulls body bytes straight off the stream (ICY non-HTTP replies
  count as alive). RTSP: OPTIONS handshake over a raw socket.
- Runs many concurrent runspaces (default `-Throttle 48`); each fetch is `CancellationToken`-bounded so
  endless live bodies are never fully downloaded. `-SignalBytes` (default 16384) caps the pull,
  `-SignalMinBytes` (default 2048) is the alive threshold, `-SignalTimeoutSec` (default 8) bounds each
  fetch, `-Limit N` probes only the first N rows (for a quick sample; cannot combine with `-PruneDead`).

```
# Deep-signal report over the whole catalog (no writes):
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal

# More threads for a faster full sweep:
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal -Throttle 80

# Apply the prune after reviewing the report:
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal -PruneDead
```
- Default run appends only **signal-verified** `alive` new rows (header-alive + deep-signal confirmed,
  see the append gate above) to `delivery/stream-catalog/streams.csv` and writes a timestamped backup
  under `temp/` first.
- Preview run writes `temp/stream-candidates.csv` + `temp/stream-candidates-report.csv` and does not
  touch the catalog.
- Catalog maintenance report: `temp/stream-catalog-liveness.csv` (per-URL status + http code + note).

### Pruning dead rows

The unified script can also delete non-playable rows from the CSV. Pruning is **opt-in** (`-PruneDead`).

- **Header-only prune** stays conservative: only `dead` (DNS-fail / connection-refused / HTTP 404|410)
  is eligible; `unknown` (auth / geo / rate / timeout) is never removed.
- **Deep-signal prune** (`-DeepSignal -PruneDead`, un-pinned) widens to `dead` + `unknown`: since the
  deep-signal probe now separates region-locked channels into their own `geo` verdict, the surviving
  `unknown` rows are non-geo failures (timeout / SSL / `401` / `5xx`) safe to drop. `geo` rows are
  **kept** and tagged `access=geo` (S1117). Pin `-PruneStatuses dead` to force the conservative set.

```
# 1. Dry-run first - lists what WOULD be removed, writes nothing:
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly

# 2. Review temp/stream-catalog-liveness.csv; ideally re-probe from a second
#    network vantage (geo-restricted streams read dead locally yet work elsewhere).

# 3. Apply - backs up the CSV to temp/<name>.<timestamp>.bak before writing:
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -PruneDead
```

- `-CatalogOnly` stays a non-destructive report and prints a `Would prune N row(s)` preview.
- `-CatalogOnly -PruneDead` writes a timestamped backup under `temp/` first, then rewrites the CSV keeping original row
  and column order (quoted fields round-trip losslessly).
- `-PruneStatuses dead,unknown` widens the set if you deliberately want to drop `unknown` too - not
  recommended for a publish.
- After pruning, re-publish the release asset with the guarded packer (see Hosting above).

### Facet normalization rewrite (S2233)

`-NormalizeFacets` rewrites the four grouping columns (`category`, `topic`, `language`, `country`) of the
existing catalog into their canonical values. It is a reviewable metadata-only mode: no network
collection runs, the row count and URL multiset stay unchanged, a timestamped backup and a per-value
move report land under `temp/S2233/`, and nothing is uploaded unless `-Publish` is passed. Blank cells
stay blank; an unknown non-blank value stays verbatim as a visible fallback instead of being dropped.
The legacy `-NormalizeTopics` switch remains the topic-only subset of the same operation.

```
# Review the diff first - writes the backup + move report, publishes nothing:
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -NormalizeFacets

# Publish the normalized catalog after reviewing the move report:
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -NormalizeFacets -Publish
```

The canonical value contract itself (which aliases fold where) is documented in
`stream-catalog/03_catalog_format.md` §2.4.

### Name repair rewrite (S2645)

`-NormalizeNames` repairs the `name` column of the existing catalog and collapses rows that fold to one
channel identity. Same shape as the facet mode: no network collection, a timestamped backup, reports under
`temp/S2645/`, nothing uploaded unless `-Publish` is passed. It is idempotent - a second pass over a
repaired bank reports zero moves.

What it does: decodes HTML entities (including double-encoded ones), strips the serialised
`- 0 N - ` encoder-slot prefix, collapses whitespace, and rebuilds a name that carries no information -
`(null)`, a bare `-`, or an encoder default such as `Online Radio` - from the row's own `host:port`. A name
the broadcaster actually wrote keeps its words and gains the token beside them; one that only asserts the
absence of a name is replaced by the token. Leading punctuation is left alone: `.977 Country` and
`#joint radio Blues Rock` are real station names.

It never drops a named row - the inclusion policy above is unchanged. The only rows it removes are exact
identity duplicates, which are one channel entered twice; the app already resolved both to the same key, so
a pin on one showed on the other.

```
# Review the diff first - writes the backup + both reports, publishes nothing:
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -NormalizeNames

# Publish the repaired catalog after reviewing the two reports from the run above:
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -NormalizeNames -Publish
```

Both reports are timestamped - `name-normalization-moves.<stamp>.csv` and
`identity-duplicates-dropped.<stamp>.csv` - so the publishing run does not overwrite the reports the
review run produced. The second run finds nothing left to change and writes two empty ones of its own.

Publishing refuses outright when the name column was never repaired (`Assert-CatalogNamesClean`). The
refusal names the count per class and points back at this mode; it never strips anything itself, because a
silent repair on the publish path is an unrecorded change to the shipped bank. The guarantees a published
bank now carries are in `stream-catalog/03_catalog_format.md` §2.5.

The 2026-09-06 pass: 112 rows repaired, 1 224 given a token beside their name, 398 replaced by their token,
62 identity duplicates collapsed, 19 211 -> 19 149 rows.

## Inventory (snapshot 2026-07-23, post-webcam replenishment)

- Total: **2361** streams. The catalog includes live TV, public webcams under topic `Webcam`, radio, and test streams.
- Region-locked (`access=geo`): **42** kept + tagged (national broadcasters 403/451 from the build
  machine - CBS, Cubavision, DR1, Puls 2, ..).
- A full-catalog deep-signal prune removed the accumulated ballast: 375 hard-dead first, then 134
  more (`dead` + non-geo `unknown`), from a 2691-row peak.
- Rubrics: Live TV 1809, Radio 279, Radio (SomaFM) 56, Test stream 25, Open movies 13.
- Topics: 39 distinct (News, Movie, Ambient, Electronic, Jazz, Classical, Lo-fi, Documentary,
  Science & Space, Sports, ..).
- Languages (top): english 221, french 49, german 42, italian 19, ukrainian 18, russian 15,
  spanish 14, dutch 13, plus others (polish, korean, arabic, turkish, portuguese, slovak, ..).
- Cleartext `http://` radio entries exist (importing them is only useful if the app permits cleartext
  for stream hosts - S0565 strategic §3.3 owner decision).

## Attribution

- Radio entries are drawn from the **radio-browser.info** community database (community-contributed,
  under its open terms). Keep the `homepage` column as station attribution.
- SomaFM is **listener-supported free radio** - please support it at <https://somafm.com/support/>.
- NASA TV is U.S. Government public content. DW, France 24, Al Jazeera, Euronews, NHK World, TRT World,
  Arirang, RTVE, RFE/RL, Red Bull TV publish their own free-to-air web streams.
- Test/sample and open-movie URLs belong to their respective vendors / Blender Foundation.

## Regeneration notes

- Generated via a fan-out research workflow (radio-browser API by tag, SomaFM channels.json, vendor
  test streams, public-domain video, official free live TV) plus a strict legality audit.
- Vendor TEST URLs and demo RTSP endpoints rotate - re-verify before trusting `confidence` on `TEST`/
  RTSP rows. Re-run the liveness checker before each publish.

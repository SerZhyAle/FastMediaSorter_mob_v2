# FastMediaSorter curated stream catalog

A curated, human-maintainable catalog of **clearly-free** internet audio, video and RTSP streams,
organised by rubric, topic and language. The app's Streams screen ("Трансляции") "Import list"
action downloads this catalog from our GitHub, parses it, and merges the entries into the user's
local stream list (de-duplicated by URL, keeping the user's pins and order). Re-running the import
updates the list with newly-added entries.

This is a downloadable resource distributed like the other on-demand extensions in
[`../INVENTORY.md`](../INVENTORY.md), but it is intentionally **mutable** - we revise it periodically
and the change ships to users without an app release.

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

## Channel preview atlas (separate release asset)

The **channel-preview atlas** is an optional companion to the catalog: a single sprite sheet of
per-channel preview frames that the app shows for a VIDEO channel in grid mode before the user's first
watch. It is published as its **own** versioned release asset - NOT bundled inside `stream-catalog.zip` -
because it is large (20-50 MB) and has an independent lifecycle from the CSV.

```
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/channel-preview-atlas-v1.webp
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/channel-preview-coords-v1.json
```

The `-v1` suffix is the element revision: a rebuilt atlas that is not tile-compatible is published
under a new suffix, so an older app keeps resolving the payload it was pinned against.

Slicing contract (a third-party consumer of this catalog can crop the same tiles):

- One sheet, at most `8192 x 8192` px (the 2026-07-26 build is `8160 x 7560` with 1881 tiles), holding
  a fixed grid of `240 x 135` tiles, `34` columns per row.
- A tile's ordinal maps to its cell by `col = index % 34`, `row = index / 34`; its pixel rect is
  `left = col * 240`, `top = row * 135`, `right = left + 240`, `bottom = top + 135`. Equivalently
  `index = row * 34 + col`.
- Only VIDEO channels have a tile; audio/radio rows are skipped by the packer. A channel that did not
  answer during the capture pass also has no tile (196 of 2077 in the 2026-07-26 build).
- Rebuild command: `pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithChannelPreviews -PublishPreviewAtlas`
  (needs `ffmpeg` and `gh`; captured frames are cached under `temp/channel-preview-frames/`, so an
  interrupted pass resumes instead of recapturing).

Sidecar `channel-preview-coords.json` - a flat JSON object mapping each channel `url` to its zero-based
tile `index` (keyed by `url`, the stable per-channel key, mirroring the favicon sidecar):

```
{ "https://chan/a.m3u8": 0, "https://chan/b.m3u8": 33, "https://chan/c.m3u8": 68 }
```

Non-integer values are skipped defensively; an absent sidecar means "no atlas installed" (every tile
falls back to the favicon). The tile geometry above is the shared invariant between the offline packer
and the on-device slicer - changing it on one side without the other drifts every rect.

## Station logo atlas (separate release asset)

The **stream logo atlas** covers what the preview atlas structurally cannot: a station with no video
track has no frame to capture, so every radio channel would otherwise be stuck with a 32 px favicon. It
is a sprite sheet of station logos, published as its own versioned release asset for the same reasons
as the preview atlas, and downloaded/refused independently of it.

```
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-logo-atlas-v1.webp
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-logo-coords-v1.json
```

Slicing contract:

- One sheet (the 2026-07-26 build is `8024 x 4352`, 6.1 MB, 1838 tiles covering 2156 channels), holding
  a fixed grid of `136 x 136` tiles, `59` columns per row.
- A tile's ordinal maps to its cell by `col = index % 59`, `row = index / 59`; its pixel rect is
  `left = col * 136`, `top = row * 136`, `right = left + 136`, `bottom = top + 136`.
- Tiles are **square**, unlike the preview sheet's 16:9 frames: a logo is fitted whole rather than
  cropped, and is almost always square, so a 16:9 tile spent nearly half its width on empty padding.
  The consumer letterboxes the square tile into its own cell.
- The side is **even** on purpose. The sheet is lossy WebP, which is always 4:2:0, so an odd tile size
  would put every second boundary mid-chroma-block and bleed one tile's edge colour into the next.
- Padding around a logo is **transparent**, so one sheet serves both light and dark themes. Decode
  tiles as ARGB - flattening them paints the padding black.
- Not restricted to radio: a video channel whose frame capture failed uses the same tier.

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
drifts. A rebuilt sheet that is not tile-compatible gets a new `-v2` element revision plus fresh SHA-256
pins in `DeliverableDescriptorCatalog.streamLogoAtlas()`, never a silent re-upload under the same name.

### Why a rebuild needs fresh pins (S1200)

Both atlases above say a rebuilt sheet takes a new element revision plus new SHA-256 pins. That is not
bookkeeping - the pins are what the app compares an installed copy against to notice it is out of date.
A re-upload under the same asset name reaches nobody: the download would work, but no installed copy
would ever look stale, so nobody is offered it. Publishing a rebuilt atlas therefore means shipping the
new pins in an app build, and the mirror is deliberately not allowed to change hashes at runtime.

## File: `streams.csv`

UTF-8, no BOM, RFC-4180 (fields with `,` `"` or newline are quoted; inner `"` doubled).
First row is the header. One stream per row, grouped by `media_kind`, then `category`, then `topic`,
then `name`.

| Column | Meaning |
|--------|---------|
| `category` | High-level rubric: `Radio`, `Radio (SomaFM)`, `Live TV`, `Open movies`, `Test stream`. |
| `topic` | Genre / theme for filtering (e.g. `Jazz`, `Classical`, `Ambient`, `Lo-fi`, `Electronic`, `News`, `Science & Space`, `Movie`, `Test pattern`). |
| `name` | Display title. |
| `url` | Direct playable stream URL (playlists already resolved to the underlying stream). |
| `media_kind` | `AUDIO` \| `VIDEO` \| `RTSP` - drives launch routing (inline audio vs fullscreen video). |
| `protocol` | `PROGRESSIVE` \| `HLS` \| `DASH` \| `ICECAST` \| `SHOUTCAST` \| `RTSP` \| `UNKNOWN`. |
| `format` | Container/codec hint (`mp3`, `aac`, `ogg`, `opus`, `flac`, `m3u8`, `mpd`, `mp4`, ...). |
| `bitrate` | Audio bitrate in kbps as text; empty if unknown. |
| `is_live` | `true` for live/continuous streams, `false` for VOD. |
| `https` | `true` if the URL is HTTPS; `false` for cleartext `http://` (relevant to the network-security policy). |
| `language` | Normalised lowercase language name(s); multi-value comma-separated (e.g. `english`, `english,german`). |
| `country` | ISO-3166 alpha-2 where known. |
| `homepage` | Attribution / source page. |
| `source_kind` | `TEST` \| `PUBLIC_RADIO` \| `COMMUNITY` \| `PUBLIC_BROADCASTER` \| `GOV` \| `CREATIVE_COMMONS` \| `PUBLIC_DOMAIN`. |
| `license_note` | Short reason the stream is free to access. |
| `notes` | Free-text remarks. |
| `confidence` | `high` \| `medium` \| `low` - our confidence the URL is correct/stable. |
| `favicon_index` | Zero-based tile ordinal into `favicon-atlas.png` (32 px, 16-col grid); blank = no favicon. |
| `access` | `` (open) \| `geo` = region-restricted (returned HTTP 403/451 from the maintainer's network - **may still play** for a user in-region). Heuristic, not a guarantee: a 403 can also be hotlink / IP-block. Produced only by the deep-signal probe (S1117). |

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

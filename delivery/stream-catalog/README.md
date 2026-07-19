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
> the S0925 guard - it bundles `streams.csv` (entry 0) **and** `favicon-atlas.png` (<= 3 MiB) and refuses
> to publish a `favicon_index` CSV with no atlas. `-SkipLiveness` skips the URL probe without touching the CSV.

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

The catalog ships **every reachable live channel that actually carries signal**. There is no
legal-scope / "grey-area" filter: community radio, official broadcaster feeds, vendor TEST streams,
Creative-Commons / public-domain media, grey-area IPTV restreams and -/- channels are all kept,
as long as the stream responds with real media bytes.

The only entries dropped are ones that cannot actually play:

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
[SomaFM](https://somafm.com/) listener-supported radio, official broadcaster / government live feeds
(NASA TV, DW, France 24, Al Jazeera, Euronews, NHK World-Japan, TRT World, Arirang, RTVE 24h, Current
Time (RFE/RL), Red Bull TV, ..), vendor TEST/sample streams (Apple, Mux, Unified Streaming, Bitmovin,
DASH-IF, Akamai, Wowza demo), Creative-Commons / public-domain media (Blender open movies, Big Buck
Bunny, Tears of Steel, Sintel via Blender / Internet Archive / Google sample bucket), and the iptv-org
public Live TV index (incl. grey-area restreams and - channels).

## Maintenance: unified collector

`scripts/streams/collect-stream-candidates.ps1` is now the single entrypoint for both:

- discovering + validating new legal/public streams and appending them to `streams.csv`
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

## Inventory (snapshot 2026-07-19)

- Total: **2691** streams - VIDEO 2337, AUDIO 348, RTSP 6. The bulk of the VIDEO rows come from the
  iptv-org public Live TV index; run a full-catalog liveness sweep before each publish.
- Rubrics: Live TV 2299, Radio 292, Radio (SomaFM) 56, Test stream 30, Open movies 14.
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

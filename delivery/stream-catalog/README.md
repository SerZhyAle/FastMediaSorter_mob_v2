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
   (zip contains: streams.csv)
```

- `streams.csv` is committed to the repo as the source of truth (full git history of revisions).
- For distribution it is zipped (~129 KB CSV -> ~25 KB zip) and uploaded as the release asset.
- Unlike the immutable `.so`/`.mp4` delivery assets, the catalog is **not SHA-pinned** - it is meant to
  change; the app always fetches the latest asset and merges idempotently by URL.
- The optional `delivery-so-v1/delivery-manifest.json` URL-override (delivery pattern) can repoint the
  asset without an app release.

Packaging the asset for upload:

```
Compress-Archive -Path delivery/stream-catalog/streams.csv -DestinationPath temp/stream-catalog.zip -Force
```

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

## Legal scope

Only streams that are clearly free and legal to access publicly:

- Public/community internet radio - the [radio-browser.info](https://www.radio-browser.info/) community
  database, and [SomaFM](https://somafm.com/) listener-supported free radio.
- Official broadcaster / government live feeds where the **broadcaster itself** publishes an open
  HLS/DASH URL: NASA TV, DW, France 24, Al Jazeera, Euronews, NHK World-Japan, TRT World, Arirang,
  RTVE 24h, Current Time (RFE/RL), Red Bull TV, etc.
- Vendor TEST/sample streams (Apple, Mux, Unified Streaming, Bitmovin, DASH-IF, Akamai, Wowza demo).
- Creative-Commons / public-domain media (Blender open movies, Big Buck Bunny, Tears of Steel, Sintel
  via Blender / Internet Archive / Google sample bucket).

Explicitly **excluded**: paywalled, DRM-protected, geo-locked premium, and grey-area IPTV-aggregator
channels (re-streamed commercial TV). When the free/legal status of a candidate is uncertain, it is
dropped. The legality audit removed e.g. CGTN (state-controlled, revoked Ofcom licence) and a third-party
Amagi FAST mirror of DW (not the broadcaster's own feed).

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
- Default run appends only `alive` new rows to `delivery/stream-catalog/streams.csv` and writes a
  timestamped backup under `temp/` first.
- Preview run writes `temp/stream-candidates.csv` + `temp/stream-candidates-report.csv` and does not
  touch the catalog.
- Catalog maintenance report: `temp/stream-catalog-liveness.csv` (per-URL status + http code + note).

### Pruning dead rows

The unified script can also delete confirmed-dead rows from the CSV. Pruning is **opt-in** and conservative -
only rows classified `dead` (DNS-fail / connection-refused / HTTP 404|410) are eligible; `unknown`
(auth / geo / rate / timeout) is never removed.

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
- After pruning, re-zip and re-upload the release asset (see Hosting above).

## Inventory (snapshot 2026-06-21)

- Total: **426** streams - AUDIO 341, VIDEO 79, RTSP 6 (2026-06-21 new-row liveness pass: 17/17 alive;
  a full-catalog sweep was not rerun in this edit).
- Rubrics: Radio 285, Radio (SomaFM) 56, Live TV 39, Test stream 32, Open movies 14.
- Topics (25): News, Ambient, Test pattern, Electronic, Oldies, Pop, Lo-fi, Jazz, Reggae, World,
  Classical, Rock, General, Movie, Eclectic, Chillout, Lounge, Folk, Vocal, Documentary,
  Science & Space, Metal, Sports, Celtic, Hip-hop.
- Languages: english 218, french 44, german 42, italian 18, ukrainian 18, russian 15, spanish 14,
  dutch 13, polish 4, arabic 4, korean 3, turkish 2, plus others.
- Live TV languages span en / de / es / fr / ar / it / pt / ko / ru.
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

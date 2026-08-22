# Streams ("Трансляции") - Source Technical Specification

Source-of-truth documentation of the FastMediaSorter **Android** app's internet-streams ("Трансляции")
subsystem, written as a handoff spec so the **FastMediaSorter for Windows** app can reimplement the same
feature and **reuse the same stream bank and favicon atlas**.

- **What this describes**: the implemented Android system - its delivery format, data model, catalog format,
  favicon atlas, browse screen, player routing, entry points, and offline build/publish pipeline.
- **What this does NOT do**: prescribe a Windows design. How the feature is built on Windows is out of
  scope. Facts that a reuse **must match** to stay compatible with the shared bank are marked **[CONTRACT]**
  in each file; Android-internal implementation is marked *(impl detail)*.
- **Snapshot**: prose and code excerpts extracted from the working tree on branch `DEBUG-v026`, 2026-07-19;
  line numbers are from that snapshot - verify against live code before acting on a specific line. The
  delivery-contract numbers (bank size, row/column counts, atlas geometry) were **re-measured against the
  live published `stream-catalog.zip` on 2026-08-19** - see the changelog note at the top of
  `01_delivery_contract.md` for the full list of what changed.

---

## 1. The one thing to read first

If you only reuse one thing, reuse the **bank**: read **`01_delivery_contract.md`**. It fully specifies the
single downloadable artifact both apps share:

- **URL**: `https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip`
  (GitHub Release, tag `delivery-so-v1`, asset `stream-catalog.zip`, overwritten in place, not SHA-pinned).
- **ZIP contents**: `streams.csv` (entry 0, always first) + optional `favicon-atlas.png`.
- **`streams.csv`**: UTF-8 no BOM, RFC-4180, 19 columns matched **by header name**; `name` + `url` required.
- **`favicon-atlas.png`**: 32x32-px tiles, 16-column row-major grid, width fixed 512 px; each row points at
  its tile via the `favicon_index` column; indices are per-build (pair CSV+atlas from the same ZIP).
- Live snapshot (2026-08-19): 19,534 channels (AUDIO 16,616 / VIDEO 2,917 / RTSP 1); atlas 512x11488,
  5,624 favicons; ZIP ~7.56 MB (7,557,268 bytes).

The Windows app can consume this ZIP directly, and/or regenerate it with the same pipeline
(`08_build_publish_pipeline.md`).

---

## 2. Files in this set

| File | Contents |
|---|---|
| `00_README.md` | This overview: architecture map, end-to-end flow, consolidated ticket index, reuse checklist. |
| `01_delivery_contract.md` | **[Crown jewel]** The reusable bank: ZIP / CSV / atlas / coords / URL / versioning / backward-compat, and how the app applies it (merge-by-URL). |
| `02_data_model.md` | On-device persistence: the `stream_sources` Room table + migrations, DAO, repository merge/prune, the two preference stores, the domain enums, all 14 use cases. |
| `03_catalog_format.md` | Exhaustive `streams.csv` column spec, the RFC-4180 parser, the URL->media-kind classifier, and the `.m3u` playlist import. |
| `04_favicon_atlas.md` | Atlas geometry + bounds, the `favicon-coords.json` sidecar, decode/cache, the 7 render sites, and the grid-mode live-frame thumbnails (S0675). |
| `05_ui_streams_screen.md` | The Streams screen: structure, filtering, sort, session memory, pin/reorder, list/grid, overflow menu, dialogs, empty state, inline-audio UI, health probe, input parity. |
| `06_player_routing.md` | Playback: AUDIO->inline vs VIDEO/RTSP->fullscreen, ICY metadata, bandwidth-adaptive buffer, live-edge recovery, stall watchdog, control profile, casting, protocol matrix, play-outcome. |
| `07_entrypoints_and_gating.md` | Flavor gate (`SUPPORT_STREAMS`) + user master toggle (`enableStreams`), main-window menu + pinned panel, settings, onboarding, launcher gadget, extensions download, app-launch panel, device presets. |
| `08_build_publish_pipeline.md` | The offline producer: `collect-stream-candidates.ps1` (harvest, liveness probe, favicon fetch, atlas pack, CSV write, ZIP assemble, `gh` publish) and its guards. |
| `09_logo_and_preview_atlases.md` | The two on-demand atlases delivered outside `stream-catalog.zip`: channel-preview and stream-logo geometry, sidecars, the Deliverable payloads, tile-pack vs sheet, and the render fallback order. |
| `10_contract_amendment_2026-08-20.md` | **[Read first if you already implemented against this set]** What the 2026-08-19 catalog incident and the exchange with StreamsPlayer settled: the build is atomic, a row's absence never deletes user data, artwork is read by stable name plus manifest, and the preview sheet's height now follows its tile count - a breaking change for anyone who hardcoded 60 rows. |

Read order for a full understanding: `01` -> `03` -> `04` -> `02` -> `05` -> `06` -> `07` -> `08`.
Already implemented against an earlier copy of this set? Read `10` first - it is the only file that
changes rules rather than describing them.

---

## 3. End-to-end data flow

```
PRODUCER (offline, Windows, PowerShell 7 + GDI+)                  file 08
  collect-stream-candidates.ps1
    harvest candidates (iptv-org, radio-browser, webcam seeds)
    liveness probe (header + deep-signal) -> keep the live ones
    fetch favicons from each row's homepage -> pack favicon-atlas.png (32px/16col)
    stamp favicon_index into streams.csv
    zip [streams.csv (entry 0)] + [favicon-atlas.png]  (S0925 guard)
    gh release upload delivery-so-v1 stream-catalog.zip --clobber
                              |
                              v
DELIVERY (GitHub Release, tag delivery-so-v1)                     file 01
    stream-catalog.zip  (mutable, not SHA-pinned)
                              |
        explicit user action (Streams refresh / Import list / Welcome / Extensions)
                              v
CONSUMER (the app)                                               files 01-02
  ImportStreamCatalogUseCase
    download (30s deadline) -> unzip -> parse CSV -> build url->favicon_index map
    write favicon-atlas.png + favicon-coords.json to filesDir/streams/     file 04
    mergeCatalog(): add / update-in-place / prune, keyed by url, one txn    file 02
                              |
                              v
  stream_sources (Room table)                                    file 02
    observed by ObserveStreamSourcesUseCase / ObservePinnedStreamSourcesUseCase
                              |
             +----------------+-----------------+
             v                                  v
  BROWSE (StreamsActivity)  file 05     ENTRY POINTS  file 07
    list/grid, filter, sort, pin          main menu, pinned panel, settings,
    favicon tiles + grid frames  file 04  onboarding, launcher gadget, app-panel
             |
     tap a channel -> routing predicate (mediaKind)              file 06
             |
     +-------+--------------------------+
     v                                  v
  AUDIO -> inline mini-player      VIDEO/RTSP -> fullscreen PlayerActivity
    ICY now-playing metadata          BandwidthAdaptiveLoadControl, live-edge
    background service option          recovery, stall watchdog, trimmed controls,
    (files 05/06)                      cast (files 06)
```

---

## 4. Architecture / layers (consumer side)

```
UI            StreamsActivity / StreamsViewModel        (file 05)
              MainActivity panels, launcher gadget       (file 07)
              PlayerActivity + stream player helpers      (file 06)
   |
UseCase       domain/usecase/streams/*  (14 use cases)   (file 02)
              ImportStreamCatalogUseCase, StreamMediaKindClassifier, ...
   |
Repository    StreamSourceRepository (single entry point) (file 02)
              FaviconAtlasStore (favicon sidecar)          (file 04)
              StreamsSettingsStore / StreamsSessionStore   (file 02)
   |
DataSource    StreamSourceDao -> stream_sources (Room)     (file 02)
              StreamCatalogCsvParser (pure)                (file 03)
              OkHttp (catalog/playlist download)           (file 01)
              ExoPlayer/Media3 (playback)                  (file 06)
```

Data-flow rule (whole project): `UI -> ViewModel -> UseCase -> Repository -> DataSource`. UI holds no
business logic. All Streams data-layer classes compile into every flavor; only the UI entry points are gated
(file 07).

---

## 5. Flavor gate summary (file 07)

| Flavor | Streams UI (`SUPPORT_STREAMS`) | HLS/DASH/RTSP | Launcher gadget | Cast a live stream |
|---|---|---|---|---|
| standard | yes | yes | yes | yes |
| noLegal | yes | yes | yes | yes |
| legacy | yes | yes | no (`launcherDisabled`) | yes |
| vr | yes | yes | no | no (`SUPPORT_CAST=false`) |
| lite | no | no | no | (n/a) |
| photos | no | no | no | (n/a) |

Second gate: the user master toggle `AppSettings.enableStreams` (default OFF; a device-profile preset raises
it on most profiles). The app-launch-panel tile and launcher gadget are the only entry points that bypass
this toggle.

---

## 6. Reuse contract checklist (condensed from the [CONTRACT] items)

To stay compatible with the shared bank, a reimplementation must:

1. Fetch `stream-catalog.zip` from the fixed URL; expect it to change; re-fetch only on explicit user action.
2. Read `streams.csv` as UTF-8 (no BOM), RFC-4180, matching columns **by header name** (tolerate reordering /
   extra columns / a missing column). Drop rows with a blank `url` or `name`.
3. Route by `media_kind` (`AUDIO`/`VIDEO`/`RTSP`); when blank, classify from the URL (rtsp scheme -> RTSP;
   `{m3u8,mpd,mp4,mkv,webm,ts,mov}` -> VIDEO; else AUDIO).
4. If a `favicon-atlas.png` entry exists, slice tile `favicon_index` at `(index%16*32, index/16*32, 32, 32)`
   from the 512-wide grid; bounds-check; degrade to no-favicon otherwise. Never pair `favicon_index` with an
   atlas from a different ZIP (indices are per-build).
5. De-duplicate and merge channels **by url**; never overwrite or remove user-created channels from a catalog
   import (a url collision favors the user row; prune only ever removes catalog-origin rows).
6. Tolerate a missing atlas entry (older/degraded bank) without error - render no favicons.
7. (If regenerating the bank) keep `streams.csv` as ZIP entry 0, cap the atlas at 30 MB (current
   `-MaxAtlasBytes` default), and never publish a CSV with `favicon_index` values without a matching atlas
   (the S0925 hazard - see `08`).

---

## 7. Consolidated ticket index (Sxxxx)

The subsystem spans ~40 tickets (all Archived unless noted). Grouped by area:

- **Foundations**: S0565 (feature + `stream_sources` + `SUPPORT_STREAMS`), S0570 (curated catalog: CSV parser,
  import, merge-with-prune), S0575 (master toggle + onboarding + downloadable catalog + gating).
- **Catalog data**: S0583 (import timeout + atlas cap budget), S0761 (country column/facet), S0821 (chunked
  prune), S0805 (deep-signal liveness), S0843 (webcam seeds), S0588 (catalog replenish).
- **Favicon atlas**: S0668 (sprite atlas end-to-end), S0785 (list country-flag fallback), S0925 (publish
  guard), S1067 (favicon shortcut icon).
- **Logo / preview atlases** (file `09`, on-demand, NOT in the bank ZIP): S1154 (channel-preview atlas),
  S1201 (stream-logo atlas), S1445 (tile pack replaces the sheet as the primary read path), S1220
  (decoder recycle guard), S1483 (artwork payloads unpinned).
- **Grid frames**: S0675 (grid mode + capture), S0700 (probe outcome), S0712 (persistent frames), S0784
  (always-show-last-frame), S0933 (TextureView capture).
- **Browse screen**: S0580 (filter + searchable picker), S0587 (scroll buttons), S0593 (play-status bullet),
  S0659 (settings defaults + session memory), S0660 (overflow menu + edit), S0664 (input parity), S0673
  (empty state), S0690 (re-tap stop), S0691 (title dedup), S0692 (rotation), S0695/S0696 (pin / pinned-only),
  S0697/S0699 (facet + scroll session), S0711 (offline gate), S0940 (controls placement), S0947 (picker),
  S1054 (search not persisted), S0938 (pinned reorder), S0701/S1062 (grid overflow/badge).
- **Player**: S0581 (unavailable dialog), S0590/S0592 (channel title/kind), S0631/S0640/S0641/S0642 (control
  profile + channel nav), S0632 (cast; via S0403 seam), S0634 (live recovery), S0685 (wait label + buffer),
  S0688 (adaptive buffer), S0694 (fullscreen/gesture), S0936/S0937 (stall watchdog), S1015 (basic-auth +
  shared factory), S0874/S0893/S0895/S0896 (lifecycle guards), S0577 (background audio + exit).
- **Entry points**: S0637 (home-screen shortcut), S0663 (app-launch panel), S0756/S0770/S0777/S0779/S0780/
  S0782/S0783/S0807-S0810 (main-window panel), S0404 (launcher gadget), S0386/S0401/S0547 (extensions),
  S0327 (device presets), S0876 (serialized onboarding writes), S0911 (panel toggle relocation), S1106
  (onboarding deadline).

---

## 8. Parked follow-ups (found while writing this set)

Four out-of-scope issues surfaced during research and were parked as Draft tickets (§3.1 auto-capture) - they
do not block reuse but are worth tracking:

- **S1108** - `delivery/stream-catalog/README.md` still shows an unsafe CSV-only publish snippet that bypasses
  the S0925 atlas guard (has caused two production favicon-wipe incidents). See `08` §11.
- **S1109** - `docs/ARCHITECTURE.md` "Internet Streams Subsystem" section is stale (names 5 non-existent
  classes and misattributes the player protocol path). See `06` §12.
- **S1110** - the Extensions screen shows the stream catalog as "0 MB" (hardcoded 200 KB under a `%.0f MB`
  formatter). See `07` §7.
- **S1111** - grid-mode tiles lack mouse right-click parity (the list rows have it). See `05` §11/§18.

---

## 9. How this set was produced

Read-only extraction across the whole subsystem (79 catalog classes + the offline pipeline + specs), then
synthesized here. Detailed working dumps live under `temp/scratch/streams-src-doc/` (gitignored scratch;
they may be wiped). This set is the version-controlled deliverable. The Streams subsystem has no dedicated
record in the project's `docs/DOCUMENT_REGISTRY.jsonl`; this handoff set is deliberately placed under
`dev/handoff/` so it stays version-controlled without registering as a maintained public document.

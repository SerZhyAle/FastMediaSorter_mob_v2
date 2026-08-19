# Developer Prompt - Build the "Streams / Трансляции" feature in FastMediaSorter for Windows

> Hand this whole file to the developer (or AI coding agent) implementing the feature on Windows. It is
> self-contained: the fixed data/format contracts are embedded inline, and the deeper reference is the
> source-spec set in `dev/handoff/streams-source-spec/` (files 00-08).

---

## 0. Role and goal

You are implementing an **internet-streams viewing feature** (live TV, internet radio, and RTSP channels)
in **FastMediaSorter for Windows**, as a close analog of the same "Трансляции" feature already shipped in
the FastMediaSorter **Android** app.

You will **reuse the exact same published "stream bank" and favicon atlas** the Android app uses - the same
downloadable artifact, byte-for-byte. Your app is a **new consumer of the same bank**.

**Your freedom**: choose your own implementation, media backend, UI framework, and storage inside the
existing Windows codebase and stack. Do **not** mirror the Android class layout. **The data and format
contracts in Part A are fixed** and must match exactly, or you break compatibility with the shared bank.

**Authoritative reference** (read before coding): `dev/handoff/streams-source-spec/`
- `01_delivery_contract.md` - the bank (read this first)
- `03_catalog_format.md` - CSV columns + classifier + m3u
- `04_favicon_atlas.md` - atlas geometry + coords + grid frames
- `02_data_model.md` - persistence + merge semantics + use cases
- `05_ui_streams_screen.md` - browse UI behavior
- `06_player_routing.md` - playback, buffering, recovery, cast
- `07_entrypoints_and_gating.md` - entry points + gating
- `08_build_publish_pipeline.md` - the offline producer (only if you regenerate the bank)
- `09_logo_and_preview_atlases.md` - the two on-demand atlases (only if you render richer art than a favicon)

Everything below summarizes those files; on any conflict, the files win.

---

## PART A - Non-negotiable contracts (must match to share the bank)

### A.1 The bank (one downloadable artifact)

- **URL** (fixed, hosted on the Android repo's GitHub Release, overwritten in place, **not** SHA-pinned):
  ```
  https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip
  ```
- **ZIP contents**: exactly `streams.csv` (**entry 0, always first**) plus an optional `favicon-atlas.png`.
  There is **no** third file. `favicon-coords.json` is **not** shipped - you derive it yourself from the CSV.
- Expect the asset to **change over time**; re-download to refresh. Match entries by suffix name
  (`...streams.csv`, `...favicon-atlas.png`); ignore any other entry.

### A.2 `streams.csv`

- **UTF-8, no BOM.** RFC-4180 (quoted fields may contain commas, `""` escapes a quote, quoted fields may
  contain newlines). The producer quotes every field; quoting is not required of a consumer.
- **Columns are matched by header NAME (case-insensitive), never by position.** Tolerate reordered columns,
  unknown extra columns, and a missing column (read it as empty). The 19 producer columns, in order:
  ```
  category, topic, name, url, media_kind, protocol, format, bitrate,
  is_live, https, language, country, homepage, source_kind,
  license_note, notes, confidence, favicon_index, access
  ```
- **A row is valid only if both `name` and `url` are non-blank** (after trim); drop any other row silently.
- Columns you must use: `name` (title), `url` (the direct playable stream URL, playlists already resolved),
  `media_kind` (`AUDIO`/`VIDEO`/`RTSP`), `favicon_index` (int), plus metadata `category`, `topic`,
  `language` (lowercase, comma-separated inside one cell), `country` (ISO 3166-1 alpha-2), `homepage`,
  `access` (added S1117: `""` = open, `"geo"` = region-locked from the build machine's network, may still
  play in-region - tolerate every row shipping a blank `access` cell).
- Columns you may ignore for playback (maintainer/tooling metadata): `protocol`, `format`, `bitrate`,
  `is_live`, `https`, `source_kind`, `license_note`, `notes`, `confidence`.
- **`favicon_index`** decode: a non-negative integer -> that index; blank / non-numeric / negative /
  column-absent -> no favicon.

### A.3 URL -> media kind (when `media_kind` is blank, or for manual/playlist adds)

```
rtsp://...                                          -> RTSP     (scheme wins)
last-path-segment extension in {m3u8, mpd, mp4,
   mkv, webm, ts, mov} (strip ?query and #frag)     -> VIDEO
otherwise (audio ext, no extension, unknown)        -> AUDIO    (radio default)
```
Launchable schemes are only `http://`, `https://`, `rtsp://`.
**The catalog's declared `media_kind` wins over URL classification** for catalog rows (e.g. an HLS `.m3u8`
radio stream is legitimately `AUDIO`).

### A.4 `favicon-atlas.png`

- A single PNG grid of **32x32-px** tiles, **16 columns**, **row-major**, RGBA (transparent background).
- Width is fixed at `16*32 = 512 px`; height grows with content (`ceil(tileCount/16) * 32`).
- **index -> pixel rect**: `col = index % 16`, `row = index / 16` (integer),
  `rect = (col*32, row*32, col*32+32, row*32+32)` (right/bottom exclusive).
- **`favicon_index` is not stable across bank rebuilds** - it is assigned in favicon-decode order per build.
  Always slice against the atlas from the **same** ZIP you read the CSV from. Never cache-mismatch.
- **Bounds-check** every index: `index < 0`, or a rect exceeding the actual decoded atlas dimensions ->
  render **no favicon** (never crash). An index that fits the canvas but was never assigned to any row also
  yields a transparent tile - don't infer "packed" from the row count alone (the live bank's populated-row
  count can sit below its highest referenced index; see `04` §1.4). Bounds-check per tile and accept a
  transparent result gracefully.
- The coords mapping you build is `url -> favicon_index` (keyed by the stream URL, since ids are not shared).

### A.5 Merge semantics (how to apply the bank without losing user data)

Each stored channel has a **provenance**: `CATALOG` (from the bank), `MANUAL` (user typed a URL), or
`IMPORTED` (user imported an `.m3u`/`.m3u8`). De-duplicate and merge **by `url`**:
- Existing `CATALOG` row with the same url -> **update its metadata in place** (title, kind, category, topic,
  language, country); **preserve** the user's local order and pin state.
- New url -> insert as `CATALOG`. **But** if the url is already owned by a `MANUAL`/`IMPORTED` row, the
  **user row wins** - skip the catalog insert silently. A catalog import must **never** overwrite or hide a
  user-created channel.
- `CATALOG` rows whose url vanished from the new bank -> **prune** them. **Prune only `CATALOG`-origin rows,
  never user rows.**
- The whole merge must be **atomic** (a crash mid-merge leaves the pre- or post-merge state, never a partial
  one) and **idempotent** (re-importing the same bank is a no-op).

Only `CATALOG` rows carry `category/topic/language/country`.

### A.6 Backward / forward compatibility

Tolerate an older/degraded bank: a missing `favicon_index` column and/or a missing `favicon-atlas.png`
entry must render as "no favicons", no error. Never assume a fixed atlas height.

### A.7 Refresh discipline

Re-fetch the bank only on an **explicit user action** (a "Refresh / Update catalog" command, an "Import
list" flow, or first-run onboarding). Never auto-download on app launch/update.

---

## PART B - Functional scope (Android parity), in tiers

### B.1 MVP (prove the contract, then a usable feature)
1. **Bank consumer**: download the ZIP -> unzip -> parse `streams.csv` -> build the `url->favicon_index`
   map -> keep the atlas bytes. Merge into local storage per A.5.
2. **Browse list**: show channels (title, url, media-kind icon, favicon tile).
3. **Play**: tap a channel -> **AUDIO plays inline** (a sticky mini-player, list stays visible);
   **VIDEO/RTSP opens a fullscreen/detached player**.
4. **Favicons**: slice tiles from the atlas per A.4; graceful empty slot when absent.

### B.2 Browse feature parity (see `05`)
- **Filter** by three ANDed facets - **category**, **language**, **country** - plus a **media-kind** filter
  (`All` / `Audio` / `Video`, with **RTSP folded into Video**). "All" on a facet **disables** that facet.
  Rows with a null language stay visible only under "All". Mark the filter control when any facet is active.
- **Sort** (5 modes): by name / topic / language / country / recently-added. **Pinned rows always sort
  first**, keeping their manual order regardless of the chosen sort.
- **Search**: live case-insensitive substring over title/topic/language. **Do not persist** the search text
  across screen re-opens (everything else - sort, facets, list/grid mode, scroll - is remembered).
- **Pin-to-top** + reorder (up / down / to-top) within the pinned set.
- **List vs grid** mode toggle (persisted). In grid mode, an http(s) **VIDEO** tile shows a **captured
  current frame** (see `04` §9); AUDIO/RTSP tiles show the favicon.
- **Per-row play-status bullet**: three states by **icon shape + label** (not colour only) - never tried
  (amber hollow ring), verified OK (green filled + check), last play failed (red filled + exclamation).
- **Card overflow menu**: pin/unpin (grid), move up/down/to-top (when pinned), add/remove favorite, add to
  home screen/shortcut, edit (manual channels only), send link, remove.
- **Empty state**: icon + inline "Add stream" / "Import list" actions.

### B.3 Manage
- **Add a manual channel** by URL (validate http/https/rtsp; default the title to the URL host; classify
  media kind from the URL). Stored as `MANUAL`.
- **Import an `.m3u`/`.m3u8` playlist** by URL. Parser rule: if the body contains `#EXT-X-` anywhere, treat
  it as an HLS manifest and import **zero** channels; otherwise scan lines (`#EXTINF:<dur>,<title>` sets the
  next URL's title; other `#` lines ignored; a bare URL line is a channel; title falls back to the host).
  Stored as `IMPORTED`, de-duplicated by url.
- **Edit** (manual channels only), **remove** (with confirm), **favorites**, **home-screen shortcut**.

### B.4 Settings + defaults (see `07`)
- Default sort, default media-kind filter, catalog-refresh policy (manual / suggest-on-open /
  auto-on-Wi-Fi), and a "clear play marks" action (nulls every OK/FAIL bullet, keeps the channels).
- Optional main-window pinned-channels panel.

### B.5 Full player parity (see `06`)
- **ICY / Shoutcast now-playing metadata** for radio (request header `Icy-MetaData: 1`; show
  `"<title> - <track>"`).
- **Bandwidth-adaptive buffering**, **live-edge tracking**, **auto-recovery**, **silent-stall watchdog**,
  **buffering-vs-reconnecting** wait label (constants in Part D).
- **Trimmed control set** for a live video stream (hide seek bar / rewind-forward / order-toggle /
  delete / rename / copy / move / slideshow / favorite / sleep-timer; keep fullscreen, frame snapshot,
  rotation, info, PiP, control dialog, send-link).
- **Send link** shares the stream URL as text (never downloads the live stream).
- Player **prev/next** pages between **video channels** in catalog order (a separate affordance from any
  in-collection navigation).
- **Cast** a live http(s) stream directly as a live stream if your platform supports casting; **RTSP is
  never castable**. (Cast is likely N/A on desktop Windows - if so, omit it.)

---

## PART C - Behavioral invariants (preserve exactly)

1. Merge is idempotent; **user-created channels are never overwritten or pruned** by a catalog import
   (url collision favors the user row; prune touches only `CATALOG` rows).
2. **Re-tapping** an already-playing AUDIO channel **stops** it (one gesture starts and stops).
3. **Red status = a real failed play** (from either the inline audio path or the fullscreen player). A
   reachability probe or grid-frame capture may promote a row to **green** or leave it **amber**, but must
   **never** write red.
4. `favicon_index` is valid **only** against the atlas from the same ZIP; always bounds-check.
5. **Title dedup**: render `Name (Name)` as `Name`; keep a differing parenthetical (`Euronews (FR)`). Only
   the trailing parenthetical is considered; never mutate the stored title.
6. **Offline**: fail fast with a message **before** any play attempt (do not spin on a connection timeout).
7. The catalog's declared `media_kind` **wins** over URL classification for catalog rows.
8. A since-deleted pinned/shortcut channel fails **soft** ("no longer in your list"), never crashes.

---

## PART D - Media / protocol parameters (mirror or adapt to your backend)

Your media backend must handle **progressive HTTP(S)** audio and video, **HLS** (`.m3u8`), **DASH**
(`.mpd`), and **RTSP** (prefer RTP-over-RTSP/TCP). The Android numbers to mirror where they apply:

- **HTTP data source**: connect/read timeout **15,000 ms**; **allow cross-protocol redirects** (Icecast/
  Shoutcast relays hop http<->https); send `Icy-MetaData: 1`; support **Basic Auth from a
  `http://user:pass@host` userinfo** (lift into an `Authorization: Basic` header, strip from the URL).
- **Live window config**: target ~**10 s** behind live, allowed window **4-20 s**, catch-up speed cap
  **1.02x**.
- **Adaptive buffer**: floor / live ceiling **30 s**; VOD (non-live) weak-signal ceiling **60 s**; start
  threshold **2.5 s**; post-rebuffer fill **8 s**; weak/healthy bitrate thresholds **1.5 Mbps / 5 Mbps**
  (linear interpolation between). **Live content is hard-clamped to 30 s** regardless of bandwidth; only
  non-live deepens toward 60 s.
- **Auto-recovery**: on a "behind live window" error, re-anchor to the live edge and re-prepare - budget
  **3**, linear backoff **1 s / 2 s / 3 s**. On a transient/retryable error (timeouts, connection failures,
  **HTTP 429 or 5xx**), budget **4**, exponential backoff **2 / 4 / 8 / 16 s** (re-anchor live before
  retry). **Hard-fail immediately** (show a Retry / Remove / Cancel "stream unavailable" dialog) on an
  explicit **4xx that is not 429**, a malformed manifest, or an unsupported container.
- **Stall watchdog** (for freezes that throw no error): if position advances `< 500 ms` for 3 consecutive
  3 s polls (~9 s frozen while nominally playing), or buffering exceeds 15 s without genuine progress ->
  recover (`stop()` then re-prepare; re-anchor live or restore position); budget **3**, then hard-fail.
- **Wait label**: show "Buffering.." for a plain fill, "Reconnecting.." while a recovery is in progress.
- **Cast** (if supported): hand the http(s) URL to the receiver as a **live** stream (no download/proxy);
  content-type by extension (`m3u8`->`application/x-mpegurl`, `mpd`->`application/dash+xml`, `mp4/mov`->
  `video/mp4`, `webm`->`video/webm`, `ts`->`video/mp2t`, else HLS). **Reject RTSP for cast.**

---

## PART E - Entry points and gating (adapt to Windows)

The Android flavor gate (`SUPPORT_STREAMS`) is Android-build-specific - **ignore it**. Keep a user-level
**master enable toggle** (default your choice; Android defaults it OFF and turns it ON via onboarding or a
device profile). Surface the feature as: a dedicated **Streams screen/window**, an optional **pinned-channel
panel/strip**, a settings section, and (optionally) shortcuts. The catalog download belongs behind an
explicit "Update catalog" / "Import list" action and/or first-run onboarding.

---

## PART F - Out of scope / your freedom

- **Tech stack, UI framework, media library, storage engine** - your choice within the Windows app. Do not
  port Android classes; match the **contracts (Part A)** and **behaviors (Parts B-D)** instead.
- You **may regenerate or extend** the bank yourself with the offline pipeline (`08`) - same constraints
  (CSV entry 0, atlas <= 30 MB, never ship `favicon_index` values without a matching atlas). The atlas
  packer uses .NET `System.Drawing`/GDI+, which is native on Windows.
- Chromecast casting and Android-specific surfaces (home-screen pinned shortcuts, launcher gadget) are
  optional / may not apply on desktop.

---

## PART G - Acceptance criteria

1. Downloads and applies the **live** bank; the list shows ~19,500+ channels (AUDIO ~16,600, VIDEO ~2,900,
   RTSP ~1 at time of writing - expect these to keep growing).
2. Filter (category/language/country/media-kind), sort (5), search, and pin/reorder all work as in Part B.2.
3. An internet **radio** channel plays **inline** with ICY now-playing text; a **VIDEO/HLS** channel plays;
   an **RTSP** channel plays or degrades gracefully with a clear message.
4. **Favicons** render from the atlas; a channel without a favicon shows a graceful empty slot; a
   stale/out-of-range index never crashes.
5. **Re-importing** the same bank is a no-op; **manual/imported** channels survive a catalog re-import; a
   catalog url colliding with a manual channel does not overwrite the manual one.
6. The per-row status bullet reflects real play outcomes (green on success, red on a real failure, amber
   otherwise), distinguishable by **shape**, not colour alone.
7. Fully offline: a clear message, no hang.

---

## PART H - Suggested first milestone (do this before any UI)

Implement the **bank consumer** as a standalone module with a tiny test/harness that, given the fixed URL:
downloads the ZIP, verifies `streams.csv` is entry 0, parses the CSV (by header name), prints the channel
count and a few rows, builds the `url->favicon_index` map, and extracts one 32x32 favicon tile at a known
index to an image file. This proves Part A end-to-end before you build the browse UI, player, and merge
logic on top of it.

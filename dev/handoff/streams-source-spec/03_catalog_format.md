# Streams Source Spec - 03 - Catalog Format (streams.csv, media-kind classifier, m3u import)

Part of the FastMediaSorter "Трансляции" (Streams) source-documentation set. This file is the exhaustive
column-level specification of the curated `streams.csv` catalog file, the app-side RFC-4180 parser, the
URL -> media-kind classifier, and the `.m3u` playlist import path.

Marks: **[CONTRACT]** = a producer/consumer contract a reuse must match; *(impl detail)* = Android
internals. Facts cite `path:line` (root `p:\ANDROID\FastMediaSorter_mob_v2`).

See also: `01_delivery_contract.md` (zip/atlas/URL), `02_data_model.md` (how rows land in
`stream_sources`), `08_build_publish_pipeline.md` (how `streams.csv` is produced).

---

## 1. `streams.csv` - the file

**[CONTRACT]** `streams.csv` is the curated catalog: one header row plus one data row per channel.

Live production file measured directly against the published `stream-catalog.zip` (2026-08-19):
- 5,834,634 bytes, 19,535 lines = 1 header + 19,534 data rows.
- **No UTF-8 BOM** (first byte is `0x22` = `"`).
- Every field double-quoted (the PowerShell producer `Export-Csv -Encoding utf8` quotes unconditionally),
  but quoting is **not required** by the consumer parser - unquoted bare fields parse identically.
- Row ordering convention (maintainer-only, not enforced): grouped by `media_kind`, then `category`, then
  `topic`, then `name`.

Verbatim header (byte offset 0):

```csv
"category","topic","name","url","media_kind","protocol","format","bitrate","is_live","https","language","country","homepage","source_kind","license_note","notes","confidence","favicon_index","access"
```

### 1.1 Column order (producer) **[CONTRACT]**

The producer always writes these 19 columns in this order (`collect-stream-candidates.ps1:256-259`, the
`$Schema` array). Existing columns are **never reordered**; new columns are appended at the end. This is
safe because the consumer matches columns **by header name**, not by position.

```
category, topic, name, url, media_kind, protocol, format, bitrate,
is_live, https, language, country, homepage, source_kind,
license_note, notes, confidence, favicon_index, access
```

---

## 2. Column reference (all 19) **[CONTRACT]**

| # | Column | Meaning | Example values | Blank default | Required | Persisted to `stream_sources`? |
|---|--------|---------|----------------|---------------|----------|--------------------------------|
| 1 | `category` | high-level rubric | `Radio`, `Radio (SomaFM)`, `Live TV`, `Open movies`, `Test stream` | `""` | no | yes -> `category` (CATALOG rows) |
| 2 | `topic` | genre/theme for filtering | `Jazz`, `Classical`, `Ambient`, `News`, `Movie` | `""` | no | yes -> `topic` |
| 3 | `name` | display title | `TRT Radyo 3` | - | **yes** | yes -> `title` |
| 4 | `url` | direct playable stream URL (playlists already resolved) | `https://host/stream.aac` | - | **yes** | yes -> `url` (unique) |
| 5 | `media_kind` | launch routing | `AUDIO` \| `VIDEO` \| `RTSP` | `""` -> classified from URL | no | yes -> `mediaKind` |
| 6 | `protocol` | transport hint | `PROGRESSIVE`, `HLS`, `DASH`, `ICECAST`, `SHOUTCAST`, `RTSP`, `UNKNOWN` | `""` | no | **no** (parsed, discarded) |
| 7 | `format` | container/codec hint | `mp3`, `aac`, `ogg`, `opus`, `flac`, `m3u8`, `mpd`, `mp4` | `""` | no | **no** |
| 8 | `bitrate` | audio kbps as text | `64`, `140` | `""` | no | **no** |
| 9 | `is_live` | live vs VOD | `true` / `false` | `false` | no | **no** |
| 10 | `https` | URL is HTTPS | `true` / `false` | `false` | no | **no** |
| 11 | `language` | lowercase language name(s), comma-separated inside the quoted cell | `english`, `english,german` | `""` -> `null` | no | yes -> `language` |
| 12 | `country` | ISO 3166-1 alpha-2 | `DE`, `TR` | `""` -> `null` | no | yes -> `country` |
| 13 | `homepage` | attribution page; ALSO the favicon-fetch source for the offline packer | `https://site/` | `""` | no | **no** (consumed only by the offline packer, see `08`) |
| 14 | `source_kind` | provenance class | `TEST`, `PUBLIC_RADIO`, `COMMUNITY`, `PUBLIC_BROADCASTER`, `GOV`, `CREATIVE_COMMONS`, `PUBLIC_DOMAIN` | `""` | no | **no** |
| 15 | `license_note` | why the stream is free to access | free text | `""` | no | **no** |
| 16 | `notes` | maintainer remarks | free text | `""` | no | **no** |
| 17 | `confidence` | maintainer confidence the URL is correct/stable | `high` \| `medium` \| `low` | `""` | no | **no** |
| 18 | `favicon_index` | zero-based tile ordinal into `favicon-atlas.png` (32px tile / 16-col grid, row-major) | `0`, `17`, blank | `null` | no | **no** (routed to the favicon sidecar, see `04`) |
| 19 | `access` | region-lock flag, added S1117 | `""` (open), `"geo"` (region-locked: HTTP 403/451 seen from the build machine, may still play in-region) | `""` -> `null` | no | yes -> `access` |

Ten of the 19 columns (`protocol`, `format`, `bitrate`, `is_live`, `https`, `homepage`, `source_kind`,
`license_note`, `notes`, `confidence`) are **parsed but never stored** in `stream_sources`; they exist in
the shipped catalog for maintainer/tooling use only. The running app sees them for exactly one import
pass, then discards them. `homepage` is the sole exception that has a second consumer: the offline favicon
packer (see `08`). `favicon_index` is the one column routed elsewhere (the favicon sidecar, not the
`stream_sources` row itself); `access` **is** persisted, straight to the `access` column added by
Migration41To42 (S1117) - a consumer must tolerate every row shipping a blank `access` cell, including
every row from a catalog produced before this ticket.

### 2.1 Required-row rule **[CONTRACT]**

A data row is meaningful only if **both** `url` and `name` are non-blank (after trim). Any row missing
either is **silently dropped** by the parser (not emitted as an error). Everything else is optional.

### 2.2 `favicon_index` decode rule **[CONTRACT]**

Parser (`StreamCatalogCsvParser.kt:53-56`):
```kotlin
faviconIndex = cell(fields, "favicon_index").toIntOrNull()?.takeIf { it >= 0 }
```
- non-negative integer text -> that `Int`;
- blank / non-numeric / negative -> `null`;
- column entirely absent (older catalog) -> `null` for every row, no exception.

The index is **not stable** across catalog regenerations - the offline packer assigns indices only to
rows whose favicon actually decoded, in CSV order (see `04`/`08`). A consumer must always resolve
`favicon_index` against the atlas PNG shipped in the **same** zip, never a cached/older atlas.

### 2.3 Boolean cells

`is_live`/`https` decode via a case-insensitive-`"true"`-only rule: `"TRUE"`, `"  true  "` (trimmed) ->
`true`; `"1"`, `"yes"`, `"false"`, blank -> `false`.

---

## 3. The parser - `StreamCatalogCsvParser` *(impl detail; but the tokenizer contract is [CONTRACT])*

Source: `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParser.kt`. Pure
JVM/Kotlin, **no Android dependency, no IO** - a `String -> List<ParsedCatalogEntry>` function. No
external CSV library; a hand-written character state machine.

### 3.1 Column matching **[CONTRACT]**

The header row is mapped to a `name(lowercased,trimmed) -> index` table; every data cell is fetched by
column **name**. Consequences (each covered by a unit test):
- The catalog file may **reorder** columns or **append** new ones without breaking older apps.
- An app newer than the catalog reads `""`/`null` for a column the catalog lacks.
- Unknown/extra columns are silently tolerated.

### 3.2 RFC-4180 tokenizer contract **[CONTRACT]**

The tokenizer (`:66-128`) is a strict RFC-4180 superset:
- `"`-quoted fields may contain `,` (literal comma preserved).
- Doubled `""` inside a quoted field -> a literal `"`.
- Embedded newlines (`\n`, `\r\n`) inside a quoted field are preserved (one logical record spans multiple
  physical lines).
- Bare `\n` and `\r\n` record terminators are both accepted and may be mixed.
- A trailing newline after the last record does not produce a spurious empty row.
- A row missing `url` or `name` (blank after trim) is silently dropped.

### 3.3 `ParsedCatalogEntry` (the parse output)

```kotlin
data class ParsedCatalogEntry(
    val category: String, val topic: String, val name: String, val url: String,
    val mediaKind: String, val protocol: String, val format: String, val bitrate: String,
    val isLive: Boolean, val https: Boolean, val language: String, val country: String,
    val homepage: String, val sourceKind: String, val licenseNote: String, val notes: String,
    val confidence: String, val faviconIndex: Int?, val access: String = ""
)
```

All fields are `String` except `isLive`/`https` (`Boolean`) and `faviconIndex` (`Int?`). Missing optional
columns decode to `""` (or `false`/`null`).

### 3.4 From `ParsedCatalogEntry` to `stream_sources`

Mapping happens only in `ImportStreamCatalogUseCase` (`:66-81`). See `02_data_model.md` §2 for the full
field-by-field table. Highlights:
- `id` = fresh `UUID.randomUUID()`; `sourceOrigin` = `"CATALOG"`; `sortIndex` = 0; `addedAt` = batch time.
- `mediaKind` = `entry.mediaKind.uppercase().ifBlank { classifier.classify(entry.url) }` - the catalog's
  declared kind wins after uppercasing; a blank cell falls back to the URL classifier (section 4). This is
  the only path where a declared kind is trusted (manual add and playlist import always classify from the
  URL).
- `category`/`topic`/`language`/`country`/`access` = `.ifBlank { null }`.
- The 10 catalog-only fields are dropped; `faviconIndex` is routed to the favicon sidecar keyed by URL.

---

## 4. URL -> media-kind classifier - `StreamMediaKindClassifier` **[CONTRACT for classification semantics]**

Source: `domain/usecase/streams/StreamMediaKindClassifier.kt`. Decides `AUDIO` / `VIDEO` / `RTSP` from a
URL, and validates launchable schemes.

```kotlin
fun isSupportedScheme(url: String): Boolean {
    val lower = url.trim().lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("rtsp://")
}

fun classify(url: String): String {
    val trimmed = url.trim()
    if (trimmed.lowercase().startsWith("rtsp://")) return "RTSP"
    return if (extensionOf(trimmed) in VIDEO_EXTENSIONS) "VIDEO" else "AUDIO"
}

// VIDEO_EXTENSIONS = { m3u8, mpd, mp4, mkv, webm, ts, mov }
```

Decision tree:
1. **Supported schemes**: only `http://`, `https://`, `rtsp://` (case-insensitive, trimmed). Everything
   else is rejected **before persistence** by `AddStreamSourceUseCase`/`UpdateStreamSourceUseCase`.
   *(Note: `ImportStreamCatalogUseCase`/`ImportStreamPlaylistUseCase` never call `isSupportedScheme` - a
   catalog/playlist row with an unsupported scheme is still stored, possibly classified `AUDIO` by the
   fallback.)*
2. **Classify**:
   - `rtsp://...` -> `RTSP` (scheme wins outright, no extension check).
   - else take the extension of the last path segment (strip `?query` and `#fragment` first; extension =
     text after the last `.` in the last `/`-segment, lowercased; none -> empty).
   - extension in `{ m3u8, mpd, mp4, mkv, webm, ts, mov }` -> `VIDEO`.
   - otherwise (pathless URL, audio extension like `mp3`/`aac`, or any unrecognized extension) -> `AUDIO`
     (the radio default).

The resulting `mediaKind` drives launch routing (`06_player_routing.md`): `AUDIO` -> inline mini-player,
`VIDEO`/`RTSP` -> fullscreen player.

---

## 5. `.m3u` / `.m3u8` playlist import

An alternative to the curated catalog: the user pastes an arbitrary playlist URL. Source:
`domain/usecase/streams/ImportStreamPlaylistUseCase.kt` + `data/repository/M3uPlaylistParser.kt`.

### 5.1 `M3uPlaylistParser` contract *(impl detail)*

```kotlin
fun parse(text: String): List<ParsedStreamEntry> {
    if (text.contains("#EXT-X-")) return emptyList()   // treat as an HLS manifest, not a playlist-of-streams
    // else: line scan
    //   "#EXTINF:<dur>,<title>" -> pending title (text after first comma, trimmed)
    //   other "#..." line       -> ignored
    //   non-#, non-blank line    -> a stream URL; title = pending #EXTINF title else the URL host
    //   pending title resets after each URL line
}
```
- **HLS short-circuit**: if the body contains the literal substring `#EXT-X-` **anywhere** (a bare
  substring match, not a proper tag parse), the whole body is treated as an HLS manifest the player will
  resolve directly, so the parser returns an **empty list** (zero channels imported), by design. An HLS
  master/media playlist URL handed to Import-list therefore imports nothing.
- Otherwise a plain line format: `#EXTINF:<duration>,<title>` sets the title for the next URL line; other
  `#`-lines are ignored; a non-`#`, non-blank line is a stream URL. Title falls back to the URL host, then
  to the whole URL.
- This is **not** CSV; unrelated to the `streams.csv` contract in sections 1-3.

### 5.2 `ImportStreamPlaylistUseCase`

- Downloads the playlist body over the shared `OkHttpClient` (**no `callTimeout` override** - only the
  10s per-phase connect/read/write timeouts apply; contrast the catalog import's 30s deadline in `01`).
- Parses; empty -> `ImportResult.Empty`.
- Builds `sourceOrigin = "IMPORTED"` entities: fresh UUID, `title` from the parsed entry,
  `mediaKind = classifier.classify(url)`, `sortIndex = 0`; `category/topic/language/country` left null.
- Inserts via `repository.addAllIgnoringDuplicates(...)` (one transaction, `insertIgnore` per row,
  duplicates by URL silently skipped - no merge/prune semantics, unlike the catalog path).
- Records `StatsEvent.PlaylistImported(count = actuallyInsertedRows)`; returns `ImportResult.Success(inserted)`.

No dedicated unit test exists for `M3uPlaylistParser` or `ImportStreamPlaylistUseCase`.

---

## 6. Real production rows (verbatim excerpt)

```csv
"category","topic","name","url","media_kind","protocol","format","bitrate","is_live","https","language","country","homepage","source_kind","license_note","notes","confidence","favicon_index"
"Radio","Ambient","0 N - Chillout on Radio (AAC+)","https://0n-chillout.radionetz.de/0n-chillout.aac","AUDIO","ICECAST","aac+","64","true","true","german","DE", ...
"Radio","Classical","TRT Radyo 3","http://rd-trtradyo3.medya.trt.com.tr/master.m3u8","AUDIO","HLS","m3u8","140","true","false","turkish","TR", ...
```
(Trailing columns `homepage` .. `favicon_index` omitted for width.) Note the second row: `media_kind` is
`AUDIO` even though the URL ends in `.m3u8` (which the classifier would call `VIDEO`) - because the
catalog's **declared** `media_kind` wins over URL classification for CATALOG rows. This is an HLS **radio**
stream.

---

## 7. Media-kind distribution (live snapshot, 2026-08-19)

From the current `streams.csv` (19,534 data rows): **AUDIO 16,616, VIDEO 2,917, RTSP 1** (is_live true on
18,624 rows, false on 910; measured against the published `stream-catalog.zip`).

---

## 8. Ticket index for this file

S0570 (catalog CSV + parser + import), S0668 (`favicon_index` column), S0583 (import size/timeout budgets),
S0761 (`country` column), S0805 (deep-signal append gate in the collector), plus the classifier and m3u
paths under S0565.

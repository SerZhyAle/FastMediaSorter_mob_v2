# Phase 01 - Catalog CSV parser

**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done (backend; build pending central build)
**Depends on:** -
**Blocks:** Phase 03

## Objective

A pure, RFC-4180-aware parser turning the curated `streams.csv` text into typed entries. No Android,
no IO - just `String -> List<ParsedCatalogEntry>`.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParser.kt` | New | <= 150 |

## Steps

### Step 01.1 - `StreamCatalogCsvParser`

**Prompt for developer:**

> Create `class StreamCatalogCsvParser @Inject constructor()` with `fun parse(text: String): List<ParsedCatalogEntry>`. Parse RFC-4180: a header row mapping column name -> index (tolerate column reordering and unknown extra columns), fields quoted with `"` may contain commas / escaped `""` / newlines. Required columns: `category,topic,name,url,media_kind,protocol,format,bitrate,is_live,https,language,country,homepage,source_kind,license_note,notes,confidence` - map by header name, not position. Skip rows with a blank `url` or `name`. Booleans parse `true`/`false` case-insensitively. Define `data class ParsedCatalogEntry(category, topic, name, url, mediaKind, protocol, format, bitrate, isLive, https, language, country, homepage, sourceKind, licenseNote, notes, confidence)` (all `String` except `isLive`/`https` `Boolean`). No `Sxxxx` in any log. Robust to a trailing newline and CRLF.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class StreamCatalogCsvParser`, `data class ParsedCatalogEntry`, `fun parse(` present.
- `Grep` - header-name mapping present (no hard-coded positional `split(",")[3]` indexing of the body).
- Build: `.\a.ps1 fk` PASS.

**Status:** `[x]` done

**Step Log:** Created `StreamCatalogCsvParser` with header-name column mapping (no positional body indexing), full RFC-4180 tokenizer (quoted fields, escaped `""`, embedded newlines, CRLF, trailing newline), blank-url/name skip, case-insensitive boolean parse; `ParsedCatalogEntry` defined. Self-verified via Grep (`class StreamCatalogCsvParser`, `data class ParsedCatalogEntry`, `fun parse(`, `columnIndex[name`, no `split(",")[n]`).

## Phase Done Criteria

- [x] Step 01.1 done.
- [ ] `.\a.ps1 fk` PASS (central build, orchestrator).
- [x] No `Sxxxx` in persistent logs; no empty catch.

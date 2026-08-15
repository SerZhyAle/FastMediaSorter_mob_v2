# Phase 02 - Offline favicon fetch + sprite-atlas packer

**Strategic spec:** [`../S0668_streams-favicon-sprite-map.md`](../S0668_streams-favicon-sprite-map.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (column contract fixed)
**Blocks:** the EXTERNAL atlas-build closure step
**Steps done:** 0 / 4

> This phase is PowerShell-only (offline tooling). It produces the real atlas; the app phases verify against the Phase 01 fixture, so Phase 02 may run in parallel with Phases 03-05.

---

## Objective

Extend the offline collector to fetch each catalog channel's favicon from its `homepage`, normalise it to a 32 px tile, pack tiles into one grid PNG atlas (16 columns), write the tile ordinal back into the `favicon_index` CSV column, and bundle the atlas PNG alongside `streams.csv` in the published zip.

---

## Prerequisites

- [ ] Phase 01 merged: `favicon_index` exists in `$Schema`.
- [ ] PowerShell 7 available (the collector already requires it).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | +≤ 180 (new functions + publish change) |

> File is large; back up to `temp/` before editing (it exceeds the 500-LOC backup threshold). Add new functions; modify only `Invoke-PublishCatalog` and the catalog-maintenance write path.

---

## Steps

### Step 02.1 - Favicon fetch helper (per homepage, with fallback chain)

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `Get-FaviconBytes([string]$homepage)` returning raw image bytes or `$null`. Fallback chain: (1) `https://<host>/favicon.ico`; (2) parse the homepage HTML for `<link rel="icon" href=..>` / `rel="shortcut icon"` and fetch that; (3) Google s2 fallback `https://www.google.com/s2/favicons?domain=<host>&sz=64`. Use `Invoke-WebRequest -UseBasicParsing` with a short per-request timeout (e.g. 8 s) and swallow per-host failures (a missing favicon is normal). Empty/blank `homepage` -> `$null` immediately (no fetch). Cache by host so duplicate homepages fetch once. NOTE: the s2 fallback is a third-party call - it is gated behind a `-FaviconS2Fallback` switch (default ON) so the owner can disable it.

**Verification:**

- `Grep` - `function Get-FaviconBytes` matches once in the script.
- `Grep` - all three fallbacks present: `favicon.ico`, `rel=` (link-icon parse), `s2/favicons`.
- `Grep` - a `-FaviconS2Fallback` parameter/switch exists in the `param(..)` block.

**Status:** `[ ]`

---

### Step 02.2 - Normalise + pack tiles into one grid PNG

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `Build-FaviconAtlas([object[]]$rows, [string]$AtlasPath)`. For each row with non-null favicon bytes: decode via `System.Drawing` (`System.Drawing.Image.FromStream`), draw scaled into a 32x32 cell with high-quality interpolation onto one big `Bitmap` whose width = `16 * 32` and height = `ceil(count/16) * 32`. Assign each packed favicon the next sequential ordinal and record `url -> ordinal`. Rows whose favicon could not be fetched get NO ordinal (their `favicon_index` stays blank). Save the bitmap as PNG to `$AtlasPath` (`delivery/stream-catalog/favicon-atlas.png`). Return the `url -> ordinal` map. Constants must match Phase 01: TILE=32, COLS=16 (state this in a comment referencing PHASE_01).

**Verification:**

- `Grep` - `function Build-FaviconAtlas` matches once.
- `Grep` - the grid constants `32` (tile) and `16` (columns) appear together in the packer with a comment referencing the Phase 01 contract.
- `Grep` - `System.Drawing` (or `Add-Type -AssemblyName System.Drawing`) is used for decode/scale/save.

**Status:** `[ ]`

---

### Step 02.3 - Write `favicon_index` back into the CSV rows

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> In the catalog-maintenance write path (where `Write-CsvUtf8 -Columns $Schema` writes `$ExistingCsv`, ~line 785/925), after building the atlas, set each row's `favicon_index` property from the `url -> ordinal` map (blank when absent) BEFORE the CSV write, so the persisted `streams.csv` carries the indices that match the atlas just built. Guard the whole favicon build behind a `-WithFavicons` switch (default OFF) so routine catalog maintenance runs are unchanged unless favicons are explicitly requested.

**Verification:**

- `Grep` - a `-WithFavicons` switch exists in `param(..)`.
- `Grep` - `favicon_index` is assigned on rows before a `Write-CsvUtf8 ... -Columns $Schema` call.
- `pwsh -NoProfile -Command "& { . scripts/streams/collect-stream-candidates.ps1 ... }"` is NOT required; instead run a syntax parse: `pwsh -NoProfile -Command "[void][System.Management.Automation.Language.Parser]::ParseFile('scripts/streams/collect-stream-candidates.ps1',[ref]$null,[ref]$null)"` exits 0.

**Status:** `[ ]`

---

### Step 02.4 - Bundle the atlas PNG into the published zip (CSV first, size-capped)

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `Invoke-PublishCatalog` (lines 791-807) bundle BOTH the CSV and the atlas, but **the CSV entry must be written FIRST and an entry whose name ends `streams.csv` must always be present** (compat invariant - an already-shipped app reaches the CSV without first streaming the whole atlas, since `ZipInputStream.closeEntry()` skips by reading bytes). `Compress-Archive -Path @(csv, png)` does NOT guarantee entry order, so write the CSV first and add the atlas as a second step: `Compress-Archive -Path $CsvPath -DestinationPath $zip -Force` (creates the zip with the CSV as the first entry), then `if (Test-Path $AtlasPath) { Compress-Archive -Path $AtlasPath -DestinationPath $zip -Update }` (appends the PNG after the CSV). Add an `$AtlasPath` param defaulting to `delivery/stream-catalog/favicon-atlas.png`. Before publishing, enforce a size ceiling: if the atlas PNG exceeds a documented cap (`$MaxAtlasBytes`, default 3 MB so the whole zip stays comfortably inside the app's 30 s import `callTimeout` budget - S0583), throw with a clear message rather than ship an atlas an old/new app may fail to download in time. The zip name and `gh release upload $Tag $zip --clobber` stay unchanged - the app fetches the same asset. Log both bundled file names + sizes and assert the CSV is entry 0.

**Verification:**

- `Grep` - `Invoke-PublishCatalog` writes the CSV with `Compress-Archive ... -Force` and appends the atlas with a separate `-Update` call (CSV-first ordering; not a single `-Path @(..)`).
- `Grep` - `favicon-atlas.png` appears as the default `$AtlasPath` in `Invoke-PublishCatalog`.
- **Compat invariant:** `Grep` - a `streams.csv`-bearing entry is always added (the CSV `Compress-Archive` is unconditional; only the atlas append is `if (Test-Path $AtlasPath)`), and a `$MaxAtlasBytes` ceiling (documented as the 30 s `callTimeout` budget, S0583) guards the atlas size before upload.
- `pwsh -NoProfile -Command "[void][System.Management.Automation.Language.Parser]::ParseFile('scripts/streams/collect-stream-candidates.ps1',[ref]$null,[ref]$null)"` exits 0.

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] The script parses with no syntax errors (`Parser::ParseFile` exit 0).
- [ ] `Get-FaviconBytes` (fetch+fallback), `Build-FaviconAtlas` (pack), the CSV write-back, and the publish bundling all exist and are guarded by `-WithFavicons` / `-FaviconS2Fallback` switches so default runs are unchanged.
- [ ] Grid constants (32 px / 16 cols) match the Phase 01 contract and reference it in a comment.
- [ ] **Compat invariant (strategic §3.2 / §11 #6):** the published zip always contains a `streams.csv` entry and packs it BEFORE the atlas PNG (CSV-first, so an already-shipped app reaches the CSV without streaming the whole atlas); the atlas size is capped (`$MaxAtlasBytes`, documented against the 30 s import `callTimeout` budget, S0583) so the larger zip still downloads on old and new apps.
- [ ] Dev log entry added for the collector change.

---

## Handoff Notes to Next Phase

The published `stream-catalog.zip` now contains `streams.csv` (with `favicon_index`) AND `favicon-atlas.png`. Phase 03 extracts the PNG entry by name from that zip. The actual `-WithFavicons` run over the live catalog is the EXTERNAL closure step (owner-run, network-heavy) - this phase only builds the capability.

---

## Rollback Plan

Revert the phase commit: restore the single-path `Compress-Archive`, drop the new functions and switches, drop `favicon_index` write-back. The schema column from Phase 01 stays (harmless empty column).

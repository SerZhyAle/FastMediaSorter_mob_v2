# Phase 01 - Logo atlas packer

**Strategic spec:** [`../S1201_radio-logo-atlas.md`](../S1201_radio-logo-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Teach the offline packer to build a 136x136 logo sheet plus a `url -> index` sidecar from the artwork cache already on disk, and print the SHA-256 / byte pins the app descriptor needs.

---

## Prerequisites

- [ ] `temp/stream-logo-src/` holds cached originals (`*.img`) - populated 2026-07-26.
- [ ] `ffmpeg` resolvable via the packer's `Get-FfmpegExe`.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | ≤ 2050 |

> Script-only phase - no Kotlin, no resources, no layout, so no landscape-parity or flavor-source-set obligation.

---

## Steps

### Step 01.1 - Declare the logo-atlas geometry and CLI surface

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `# --- S1201 stream logo atlas ---` section after the S1154 preview-atlas section. Declare `$script:LogoTileW = 136`, `$script:LogoTileH = 136`, `$script:LogoCols = 59`, `$script:LogoMaxRows = 60`, each carrying a comment naming `StreamLogoAtlasSlicer` as the app-side mirror of the same contract. Add script parameters `-WithStreamLogos` (switch), `-PublishStreamLogoAtlas` (switch), `-LogoAtlasPath` (default `delivery/stream-catalog/stream-logo-atlas.webp`), `-LogoCoordsPath` (default `delivery/stream-catalog/stream-logo-coords.json`) and `-LogoLimit` (int, 0 = no limit) next to the existing `-WithChannelPreviews` group.

**Verification:**

- `Grep` - `$script:LogoTileW = 136` present exactly once.
- `Grep` - `StreamLogoAtlasSlicer` referenced in a comment beside the geometry constants.
- `Grep` - `WithStreamLogos`, `PublishStreamLogoAtlas`, `LogoAtlasPath`, `LogoCoordsPath`, `LogoLimit` each present in the `param(..)` block.

**Status:** `[x]` done

---

### Step 01.2 - Build the sheet from the artwork cache

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `Build-StreamLogoAtlas -Rows <object[]> -SheetPath -CoordsFile`, modelled on `Build-ChannelPreviewAtlas` but sourcing pixels from the cache instead of ffmpeg captures: for each row resolve `Get-LogoCacheFile $row.homepage $LogoCacheDir` and skip rows whose cache entry is missing or is a `.miss` marker. Draw each logo *contained* inside the square tile (scale by the smaller of `tileW/imgW` and `tileH/imgH`, never upscale past 1.0, centre the result) onto a fully transparent sheet - create the `Bitmap` with `[System.Drawing.Imaging.PixelFormat]::Format32bppArgb` and clear to `[System.Drawing.Color]::Transparent`, do not `Clear(Black)` as the preview packer does. Encode with `libwebp` preserving alpha (`-c:v libwebp -preset picture -quality 90 -compression_level 6`). Write the `url -> index` sidecar with `ConvertTo-Json -Compress`. Print tile count, sheet dimensions, remaining slot capacity, and the SHA-256 + byte size of both artifacts, stating they belong in `DeliverableDescriptorCatalog.streamLogoAtlas()`. An unreadable cache entry skips that url and leaves its slot untouched rather than aborting the sheet.

**Verification:**

- `Grep` - `function Build-StreamLogoAtlas` present exactly once.
- `Grep` - `Format32bppArgb` and `Color]::Transparent` both present inside the new function.
- `Grep -n "Clear(\[System.Drawing.Color\]::Black)"` - still exactly one hit (the preview packer), i.e. the logo packer did not copy the opaque clear.
- `Grep` - the report line mentions `streamLogoAtlas()`.

**Status:** `[x]` done

---

### Step 01.3 - Add the run and publish entry points

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `Invoke-BuildStreamLogoAtlasRun`, which reads `$ExistingCsv`, keeps every row with a non-empty `homepage` in catalog order (both AUDIO and VIDEO - strategic ADR-3 makes the tier kind-agnostic), applies `-LogoLimit` when set, and calls `Build-StreamLogoAtlas`. Add `Invoke-PublishStreamLogoAtlas`, modelled on `Invoke-PublishChannelPreviewAtlas`: stage the two artifacts under `temp/stream-logo-publish` as `stream-logo-atlas-v1.webp` and `stream-logo-coords-v1.json`, then `gh release upload $Tag .. --clobber`. Wire both into the script's dispatch so `-WithStreamLogos` builds and `-PublishStreamLogoAtlas` uploads, matching how `-WithChannelPreviews` / `-PublishPreviewAtlas` are dispatched today.

**Verification:**

- `Grep` - `function Invoke-BuildStreamLogoAtlasRun` and `function Invoke-PublishStreamLogoAtlas` each present exactly once.
- `Grep` - `stream-logo-atlas-v1.webp` and `stream-logo-coords-v1.json` present (asset names carry the `-v1` element revision, matching `withRev()`).
- `Grep` - both functions are invoked from the dispatch block, not only defined.

**Status:** `[x]` done

---

### Step 01.4 - Run the packer and capture the pins

**Files:** `scripts/streams/collect-stream-candidates.ps1` (execution only)
**Depends on:** Step 01.3

**Prompt for developer:**

> Run the packer with `-WithStreamLogos` against the shipped catalog and record its report to `temp/S1201/logo-atlas-build.log`. Confirm the sheet fits one page: rows needed must be ≤ `$script:LogoMaxRows` and the sheet height ≤ 8192. Copy the two SHA-256 values and byte sizes out of the report - Phase 03 pins them. If the sheet exceeds 12 MB, lower `-quality` in the encode call rather than dropping alpha (strategic §5).

**Verification:**

- `Glob` - `delivery/stream-catalog/stream-logo-atlas.webp` and `delivery/stream-catalog/stream-logo-coords.json` both exist.
- Report states `tile(s)` count ≥ 1500 and a sheet height ≤ 8192; record `expected: <=8192 | actual: <n>`.
- `Glob` - `temp/S1201/logo-atlas-build.log` exists and contains both `sha256 =` lines.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Script runs to completion with exit code 0 - record the code.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `scripts/streams/collect-stream-candidates.ps1`.
- [ ] Phase-boundary audit run - focus: no unbounded memory growth while packing ~2500 tiles (dispose every `Image`/`Graphics`), and no silent truncation without a warning line.

---

## Handoff Notes to Next Phase

- The sheet geometry is now fixed in the script; Phase 02's slicer must declare the identical 136x136 / 59-column constants.
- The SHA-256 + size pins from Step 01.4 are Phase 03's input.

---

## Rollback Plan

Revert the script edits and delete the two generated artifacts - nothing is published or consumed by the app until Phase 03.

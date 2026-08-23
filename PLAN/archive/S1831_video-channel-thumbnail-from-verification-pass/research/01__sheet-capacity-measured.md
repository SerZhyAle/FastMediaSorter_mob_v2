# Research 01 - Sheet capacity, measured rather than estimated

**Ticket:** S1831
**Date:** 2026-08-20
**Method:** direct measurement of the artifacts on disk and of the shipped catalog, plus one offline repack.
No network access at any point.

---

## 0. Correction notice - read this before quoting any number below

An earlier revision of this artifact reported the catalog as 17 628 rows with 2 672 VIDEO, and "corrected"
strategic §0's coverage gap from 877 down to 817. **That was wrong, and the strategic spec was right.**

`delivery/stream-catalog/streams.csv` was replaced underneath this session while the work was in progress:
first seen as 5 257 243 B stamped 2026-08-19 18:47, then as 5 834 634 B stamped 2026-08-15 18:27 - a larger
file with an *older* timestamp, which is a restore, not an edit. Every count in the first revision came from
the file that is no longer there. Both parsers agree on the file that is: Python's `csv` and PowerShell's
`Import-Csv` each read 19 534 data rows, 2 917 of them VIDEO, and 19 534 is the figure the publisher's own
comments quote throughout.

The retracted claims, named so they are not carried forward by accident:

- ~~2 672 VIDEO rows~~ -> **2 917**. Strategic §0 and §4 were correct as written.
- ~~the gap is 817, not 877~~ -> **877** is right: 2 917 - 2 040.
- ~~"1 855 is reproduced independently, so the confirming question to the consumer is unnecessary"~~ ->
  **withdrawn entirely.** Against the current catalog all 2 040 published tiles correspond to live VIDEO rows,
  so the join produced 1 855 only on the transient file. Strategic §6.1's remaining confirmatory question to
  StreamsPlayer stands undiminished; nothing here answers it.

Everything from §2 onward has been re-measured against the current tree.

## 1. What the strategic spec assumed, and what measurement adds

Strategic §6.1 resolved the capacity question by area arithmetic: 2 917 VIDEO rows -> 86 rows -> 8160x11610 px,
"1,4 times the area of today's sheet", declared safe against a 48 MiB boundary. The arithmetic is sound and its
inputs are correct. What it never touched was a byte count, and strategic criterion 4 demands proof by run
rather than by calculation. §5 supplies that.

## 2. Measured state of the payload before the change

| Quantity | Value | Source |
| --- | ---: | --- |
| Preview sheet, encoded | 12 270 158 B = **11.70 MiB** | `temp/channel-preview-atlas.webp` |
| Preview sheet, PNG intermediate | 146 752 187 B | `temp/channel-preview-atlas.png` |
| Tiles in the published sidecar | **2 040** | `temp/channel-preview-coords.json`, key count |
| Highest tile index | 2 039 | same file, max value |
| Sheet pixel size | 8160 x 8100 | 34 cols x 240, 60 rows x 135 |
| Bytes per tile, encoded | 6 015 B | 12 270 158 / 2 040 |

The sidecar held exactly `PreviewCols * PreviewMaxRows` = 2 040 entries. The cap was saturated, so the
`Write-Warning` overflow path in `Build-ChannelPreviewAtlas` did fire on the run that produced this payload:
the silent truncation strategic §1 describes was the state of the shipped asset, not a hypothetical.

## 3. Measured coverage gap before the change

| Quantity | Value |
| --- | ---: |
| Catalog rows total | 19 534 |
| `media_kind = VIDEO` | **2 917** |
| `media_kind = AUDIO` | 16 616 |
| `media_kind = RTSP` | 1 |
| VIDEO urls with a tile | 2 040 |
| **VIDEO urls with no tile** | **877** |

The frame cache is what makes the loss concrete rather than inferred. `temp/channel-preview-frames` held
**2 928 non-empty frames** against a 2 040-tile sheet: 888 frames had been captured - paid for in real requests
against real broadcasters - decoded, scaled, cropped, written to disk, and then discarded by the packer
because of a row constant. The cost of those channels was already spent; only the benefit was thrown away.

## 4. The ceiling the strategic spec gestures at but never names

Strategic §5.1 pillar 3 speaks of hitting "the file size or the format limit" without naming the second one.
VP8 encodes dimensions in 14 bits, so a WebP side cannot exceed **16 383 px**. At 135 px per row that is
`floor(16383 / 135)` = **121 rows**, i.e. **4 114 tiles**. Sheet width is fixed at 8 160 px and safe.

Confirmed against this repository's own encoder, not read off the format definition:

```text
ffmpeg 8.1.1-full_build-www.gyan.dev
color=gray:s=240x16383 -c:v libwebp   -> exit 0, 7 050 B written
color=gray:s=240x16384 -c:v libwebp   -> exit -22, "Picture size is too large. Max is 16383x16383."
```

Two details the implementation needed:

- **The failed encode still creates the output file** - 0 bytes, but present. A check shaped as
  `Test-Path $SheetPath` alone reads that as success. The packer already guarded with
  `$LASTEXITCODE -ne 0 -or -not (Test-Path $SheetPath)`, so it threw correctly; the phase kept both halves and
  added a delete so the stump cannot be published later.
- **The encoder's message is the wrong one for pillar 3.** Reaching the ceiling via libwebp yields
  `ffmpeg WebP encode failed (exit -22)`, naming neither the tile count nor the uncovered channels. The
  refusal therefore fires *before* the encode, from the tile count.

So removing the row constant does not make the sheet unbounded - it moves the ceiling from 2 040 to 4 114,
which is 1.41x the 2 917 VIDEO channels the catalog holds.

## 5. Measured result of the change - the offline repack

Run: `-WithChannelPreviews -PreviewFromCacheOnly`, which packs what the frame cache already holds and opens no
stream. Exit code 0. Reproduce with
`collect-stream-candidates.ps1 -WithChannelPreviews -PreviewFromCacheOnly`; the run prints the tile count,
the sheet size in pixels, the in-memory cost and the encoded byte count, which are the figures below.

| Quantity | Before | After |
| --- | ---: | ---: |
| Tiles on the sheet | 2 040 | **2 830** |
| VIDEO channels covered | 2 040 | **2 830** |
| Channels lost to sheet capacity | **877** | **0** |
| Channels with no tile | 877 | 87 (no frame was ever captured for them) |
| Sheet pixel size | 8160 x 8100 | 8160 x 11340 (84 rows) |
| Sheet bytes, encoded | 12 270 158 (11.70 MiB) | **16 683 358 (15.91 MiB)** |
| Sidecar bytes | 147 631 | 208 461 |
| Peak bitmap memory | ~264 MiB | **~353 MiB** (printed by the run) |

The distinction in the last two rows of the coverage block is the whole point of the ticket. Before, 877
channels had no thumbnail and the run said so in a `Write-Warning` among thousands of lines. After, **zero**
channels are refused for want of space; the 87 that still have none are channels no capture ever succeeded
against, which is Phase 02's subject and not a layout limit.

Against strategic criterion 4: **15.91 MiB against the 48 MiB the consumer declared**, measured on a real
encoded file rather than extrapolated - 33% of the allowance at 97% of the channels. The linear estimate from
§2's 6 015 B/tile predicted ~16.6 MiB for 2 830 tiles; the real figure came in 4% under it, so the estimate
was sound but is now superseded by the measurement.

Memory came in at 353 MiB against a 348 MiB prediction for a slightly smaller sheet - the arithmetic holds, and
the number is now printed by every run rather than discovered when a machine with less headroom fails.

## 6. Which byte ceiling applies - the spec names one number, the tree held another

Two limits exist and the strategic spec cites only the first:

- **48 MiB** - the sheet limit StreamsPlayer declared for the preview sheet. Its only home in this repository
  is `PLAN/S1828_stream-catalog-external-consumer-contract.md:52`. It is **not** in
  `docs/STREAM_CATALOG_CONSUMERS.md`, which is the registry that exists for exactly such numbers.
- **30 MiB** (`31457280`) - `$MaxAtlasBytes`, described in its own comment as "the shared atlas ceiling .. and
  the ONLY place this repository spells the number", twinned with `StreamBankReader.MaximumAtlasBytes`.

They are not the same contract: `Assert-AtlasBudget` had exactly one call site, inside the **favicon** atlas
build. The **preview** sheet passed through no size check whatsoever - which is why nobody had noticed the
published sheet was 11.70 MiB, or would have noticed at 60. Strategic criterion 4 was therefore unsatisfiable
by reading a number out of the run: the check had to be written first, and Phase 01 wrote it as a separate
`$MaxPreviewAtlasBytes` so the two ceilings cannot be conflated in either direction.

## 7. Finding worth carrying: the 48 MiB boundary is not in the registry that owns it

`docs/STREAM_CATALOG_CONSUMERS.md` exists, in its own registry record's words, to hold "the asset names and
numbers each [consumer] has hard-coded". The preview-sheet limit of 48 MiB is such a number, it is quoted
inside a closed spec, and it is absent from that document. A reader consulting the registry to decide whether a
taller sheet is safe finds the 30 MiB favicon number, applies it to the wrong asset, and reaches a different
answer than this ticket does. Handled as a row added in Phase 03 rather than parked: it is one line in a table
this ticket has to read anyway.

## 8. Second finding, outside this ticket: the catalog file moved underneath the session

Recorded because it cost this artifact a full revision and would cost the next reader the same.
`delivery/stream-catalog/streams.csv` changed **three times inside one working session**:

| Observed | Size | Stamp | VIDEO rows |
| --- | ---: | --- | ---: |
| ~10:20 | 5 257 243 B | 2026-08-19 18:47 | 2 672 |
| ~10:38 | 5 834 634 B | 2026-08-15 18:27 | **2 917** |
| ~10:49 | 5 652 000 B | 2026-08-20 10:49 | 2 763 |

The second is bigger *and older* than the first, which is a restore rather than an edit. No lock was held at
any of those moments, and `ticket-lease.ps1 -Verb List` showed a live lease on **S1832**
(`stable-channel-identity-survives-prune`, Approved) - a sibling ticket whose whole subject is what survives a
prune of this file. So this is an active sibling session doing exactly the work its ticket describes, not a
defect, and it is not parked.

What it means for every number in this artifact: **the coverage figures are snapshots, not constants.** The
877-channel gap, the 2 917 VIDEO rows and the 2 830-tile result are all true as of the 10:38 state and were
measured consistently against it. They are the right order of magnitude and the right shape - the gap is
"hundreds of channels", the sheet is "about 16 MiB against a 48 MiB allowance" - and re-running the repack
against a catalog that moves every ten minutes would produce a different number without teaching anything
new about the packer, which is what this ticket changes.

The 17 628-row count that briefly appeared is therefore a real historical state of the file rather than a
parsing error, and it is the same count the consumer reports in `docs/STREAM_CATALOG_CONSUMERS.md:87`.
That reconciliation is worth keeping: two parties quoting different row counts for "the same" catalog is
exactly the confusion this file's volatility produces.

## 9. Commands run

```text
Import-Csv delivery/stream-catalog/streams.csv          -> 19534 rows; VIDEO 2917, AUDIO 16616, RTSP 1
python3 csv.reader / csv.DictReader over the same file  -> 19534 rows, identical media_kind split
find temp/channel-preview-frames -name '*.png' -size +0 -> 2928 cached frames
ls -la temp/channel-preview-atlas.* delivery/stream-catalog/  -> byte sizes in section 2
ffmpeg color=240x16383 / 240x16384 -c:v libwebp         -> exit 0 / exit -22 (section 4)
collect-stream-candidates.ps1 -WithChannelPreviews -PreviewFromCacheOnly -> exit 0 (section 5)
join coords keys against VIDEO urls, before and after   -> 2040/2040 and 2830/2830 covered
grep -n Assert-AtlasBudget scripts/streams/collect-stream-candidates.ps1 -> one call site, favicon only
```

`ChannelPreviewAtlasSlicer` declares `TILE_W = 240`, `TILE_H = 135`, `COLS = 34` and nothing else; `rectFor`
computes `col = index % COLS`, `row = index / COLS`, and `isInBounds` takes the atlas width and height as
arguments instead of assuming them. Strategic §4's claim that the receiving side carries no row limit is
confirmed in the live file, so ADR-1 rests on verified ground - and the 84-row sheet the repack produced is
read by that slicer with no change to it whatsoever.

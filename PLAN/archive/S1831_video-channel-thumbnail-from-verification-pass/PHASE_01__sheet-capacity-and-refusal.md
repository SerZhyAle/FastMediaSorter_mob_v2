# Phase 01 - Sheet capacity and refusal

**Strategic spec:** [`../S1831_video-channel-thumbnail-from-verification-pass.md`](../S1831_video-channel-thumbnail-from-verification-pass.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 6 / 6
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Make the preview sheet's height follow the number of tiles instead of a constant, refuse loudly when tiles
still cannot be placed, and gate the published sheet on its byte size - so no video channel loses its
thumbnail to a limit this repository set on itself, and no oversized sheet reaches a consumer unnoticed.

---

## Prerequisites

- [x] Strategic §6.1 is Resolved - it is.
- [x] `temp/CODE.LOCK` acquired immediately before the first edit of `collect-stream-candidates.ps1`, released right after (CLAUDE.md Rule 23).
- [x] Timestamped backup of the script in the ticket's scratch directory - the file is 3254 LOC, far over the 500-LOC backup threshold (CLAUDE.md Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | +60 net |
| `delivery/stream-catalog/README.md` | Modified | +12 net |

> The script is already 3254 LOC against CLAUDE.md Rule 2's 1500-LOC ceiling. That is pre-existing and parked
> as **S1840**; this phase must not make it materially worse, which is why the budget is tight and the new
> logic goes into one small function rather than inline into an already-long packer.

---

## Steps

### Step 01.1 - Back up the publisher script before editing it

**Files:** `scripts/streams/collect-stream-candidates.ps1` -> ticket scratch directory
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `scripts/streams/collect-stream-candidates.ps1` into the ticket's scratch directory as `collect-stream-candidates.ps1.<yyyyMMdd-HHmmss>.bak` before the first edit.

**Why:**

The file is 3254 lines and the only copy of a publishing pipeline whose output is pinned by SHA-256 in shipped
installs, so an edit that goes wrong is not cheap to reconstruct; CLAUDE.md Rule 5 requires a timestamped
backup under `temp/` for any file over 500 LOC.

**Verification:**

- `Glob` - a file matching `collect-stream-candidates.ps1.*.bak` exists in the scratch directory.
- Its byte length equals the source file's.

**Status:** `[x]` done

---

### Step 01.2 - Derive sheet height from the tile count and retire the row constant

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `Build-ChannelPreviewAtlas`, delete the `$maxSlots` truncation block and let `$rowsNeeded` be the only thing that decides height, as `Build-FaviconAtlas` already does. Remove `$script:PreviewMaxRows` and its `ADR-2 budget` comment; replace them with a declaration of the real ceiling - `$script:PreviewMaxSheetPx = 16383`, the WebP dimension limit - and a comment saying the 8192 budget was ours, not a consumer's, and that the receiving side reads `col = index % COLS`, `row = index / COLS` and never a row count.

**Why:**

The 2 040-tile cap is the direct cause of strategic §1's coverage gap - 877 video channels in today's catalog
can get no thumbnail at all - and strategic ADR-1 establishes it was this repository's own budget rather than
any consumer's requirement, confirmed against the live slicer, which declares only tile width, height and
column count.

**Verification:**

- `Grep` - `PreviewMaxRows` returns zero hits across the repository.
- `Grep` - `PreviewMaxSheetPx` is declared once and read in Step 01.3's guard.
- `Grep` - `exceed the {1}-slot sheet capacity` returns zero hits.
- `Grep` - `rowsNeeded` in `Build-ChannelPreviewAtlas` is still computed as `Ceiling(count / cols)`.

**Status:** `[x]` done

---

### Step 01.3 - Refuse before the encoder does, naming the channels left uncovered

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Before allocating the bitmap in `Build-ChannelPreviewAtlas`, compute the sheet height and throw when it exceeds `$script:PreviewMaxSheetPx`. The message states how many tiles were captured, how many fit, and how many channels are therefore left with no thumbnail, and names the ceiling in pixels and in tiles. Follow the refusal shape `Invoke-PublishCatalog` already uses for blank rows: count the offenders, name them, throw.

**Why:**

Strategic §5.1 pillar 3 requires overflow to stop being a `Write-Warning` among thousands of lines, and
research 01 established the reason it must fire before the encode rather than after: hitting the limit inside
libwebp yields `ffmpeg WebP encode failed (exit -22)`, which names neither the tile count nor the uncovered
channels, so the operator would learn that something broke but not what was lost.

**Verification:**

- `Grep` - `Build-ChannelPreviewAtlas` contains a `throw` whose message mentions both the tile count and the uncovered count.
- `Grep` - no `Write-Warning` remains on the capacity path.
- Unit check: run the packer with the ceiling temporarily lowered and confirm it throws before any file is written; restore the ceiling afterwards.

**Status:** `[x]` done

---

### Step 01.4 - Gate the encoded sheet on its byte size

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a `$MaxPreviewAtlasBytes` parameter defaulting to `50331648` (48 MiB) and check the encoded sheet against it in `Build-ChannelPreviewAtlas` right after the WebP encode, throwing and naming both the actual and the allowed size. Keep it a separate parameter from `$MaxAtlasBytes`; a comment states that 48 MiB is StreamsPlayer's preview-sheet limit and 30 MiB is the favicon atlas's, that the two are different contracts, and that conflating them would apply the wrong ceiling to both.

**Why:**

Strategic criterion 4 requires the published sheet to be proven under 48 MiB by the run rather than by
calculation, and research 01 found the preview sheet passes through no size check whatsoever today -
`Assert-AtlasBudget` has one call site and it guards the favicon atlas - so the criterion cannot be satisfied
until the check exists.

**Verification:**

- `Grep` - `MaxPreviewAtlasBytes` is declared as a parameter with default `50331648` and read exactly once.
- `Grep` - the throw message contains both the measured and the allowed byte count.
- `Grep` - `Assert-AtlasBudget` still has its original single favicon call site, unchanged.

**Status:** `[x]` done

---

### Step 01.5 - Prove the new path against the sheet already on disk

**Files:** none - verification only
**Depends on:** Step 01.4

**Prompt for developer:**

> Re-pack the preview sheet from the cached frames already in `temp/channel-preview-frames` with no network access, and record the tile count, the sheet pixel size, the encoded byte size and the exit code. Compare the tile count against the number of VIDEO rows in `delivery/stream-catalog/streams.csv` that have a cached frame, and confirm every one of them landed on the sheet.

**Why:**

Strategic criterion 2 asks for zero channels missing a thumbnail for want of space, and criterion 4 asks for a
measured byte size rather than a calculated one; the frame cache holds enough captured frames to demonstrate
both without opening a single third-party connection, which is what keeps this phase outside the owner's
go-ahead gate.

**Verification:**

- The run exits 0 and prints a tile count strictly greater than 2 040 if that many cached frames exist, or equal to the cached-frame count if fewer.
- The encoded sheet's byte size is recorded and is under 48 MiB.
- `Grep` on the coords JSON - its key count equals the tile count reported by the run.
- Every url in the coords JSON that is a VIDEO row in `streams.csv` appears exactly once.

**Status:** `[x]` done

---

### Step 01.6 - Correct the documents this phase falsifies

**Files:** `delivery/stream-catalog/README.md`
**Depends on:** Step 01.5

**Prompt for developer:**

> Update the channel-preview section: replace "One sheet, at most `8192 x 8192` px" with the real rule - height follows the tile count, the ceiling is the WebP dimension limit of 16 383 px (121 rows, 4 114 tiles), and the sheet is additionally gated at 48 MiB. Refresh the example build figures from the Step 01.5 run and state that overflow now refuses instead of dropping tiles.

**Why:**

That sentence is the published description of a contract external consumers read, and Step 01.2 makes it
false the moment it lands; CLAUDE.md's documentation rule and the canon's ship-together principle both require
a user-facing change to land in its documentation in the same edit rather than in a later pass.

**Verification:**

- `Grep` - `8192 x 8192` returns zero hits in `delivery/stream-catalog/README.md`.
- `Grep` - `16383` or `16 383` appears in the channel-preview section.
- `Grep` - the section states the 48 MiB gate.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -Command "& { . scripts/streams/collect-stream-candidates.ps1 -WhatIf }"` is **not** the check - the script has no `-WhatIf`; the syntax check is `[System.Management.Automation.PSParser]::Tokenize` over the file with zero errors, plus the Step 01.5 run exiting 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Closure through `scripts/post-change.ps1 -Files "scripts/streams/collect-stream-candidates.ps1,delivery/stream-catalog/README.md" -ScopeToFile -ChangeType Mixed`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Measured Outcome (2026-08-20)

Offline repack, `-WithChannelPreviews -PreviewFromCacheOnly`, exit 0. The run prints every figure below.

| | Before | After |
| --- | ---: | ---: |
| Tiles on the sheet | 2 040 | **2 830** |
| VIDEO channels covered | 2 040 | **2 830** |
| **Channels lost to sheet capacity** | **877** | **0** |
| Channels with no tile at all | 877 | 87 (no frame ever captured - Phase 02's subject) |
| Sheet pixels | 8160 x 8100 | 8160 x 11340 (84 rows) |
| Sheet bytes | 11.70 MiB | **15.91 MiB**, against a 48 MiB gate |
| Peak bitmap memory | ~264 MiB | ~353 MiB, now printed by every run |

Strategic criteria settled by this phase: **2** (zero channels refused for want of space), **3** (overflow
throws and names the count), **4** (48 MiB proven on a real encoded file, by a check that had to be written
first because none existed). Criteria 1 and 3-of-§11 remain with Phase 02.

Both refusal paths were exercised for real rather than asserted:

- **Byte gate.** `-MaxPreviewAtlasBytes 1000` on an 85-tile sheet threw
  `encoded sheet is 373,972 B (0.4 MiB) over the 1,000 B (0.0 MiB) limit .. 85 tile(s) at 8160x405`, and the
  `.webp` was removed, so no undersized-but-publishable artifact survived the refusal.
- **Dimension gate.** With the ceiling temporarily lowered to 200 px and then restored to 16383, a 93-frame
  set threw `93 captured frame(s) need 3 row(s) = 405px, over the 200px WebP dimension limit (1 rows = 34
  tiles fit). 59 channel(s) would get no thumbnail` - before the bitmap was allocated and before any file was
  written. 93 - 34 = 59 confirms the uncovered count is computed, not guessed.

**Caveat on the coverage numbers, not on the behaviour.** `delivery/stream-catalog/streams.csv` changed three
times during this session (2 672 -> 2 917 -> 2 763 VIDEO rows) under a live sibling lease on S1832, whose
subject is that very file. The table above is a consistent snapshot of the 10:38 state. The packer's behaviour
- every frame placed, refusal instead of truncation, byte gate enforced - does not depend on which snapshot
it runs against; the specific counts do. Research 01 section 8 has the detail.

One deviation from the plan as written, and why: Step 01.5 could not run offline as specified, because
`-WithChannelPreviews` captures any channel whose frame is not cached, which would have opened roughly 90
third-party streams. A `-PreviewFromCacheOnly` switch was added - the preview twin of the `-ArtworkCacheOnly`
switch the same script already carries for logos - so the packer can be proven without spending a single
request on a broadcaster. It is a real capability, not test scaffolding: it is the way to rebuild the sheet
between capture runs.

---

## Handoff Notes to Next Phase

After this phase the packer places every frame it is given and refuses rather than truncates. That makes the
capture pass the only remaining limit on coverage, which is exactly what Phase 02 addresses. Phase 02 must not
reintroduce a count cap; if a run produces more frames than the sheet can hold, the correct answer is the
refusal from Step 01.3, not a silent drop.

Note for Phase 02: the frame cache under `temp/channel-preview-frames` is keyed `SHA1(url).png` and the
capture already skips a url whose frame exists. A probe that writes into that same cache turns the preview
build into a no-network pack with no change to the skip logic.

---

## Rollback Plan

Restore the backup taken in Step 01.1. No published asset changes as part of this phase - the sheet is rebuilt
locally in Step 01.5 but not uploaded, so nothing external is pinned to the result and no revision is burned.

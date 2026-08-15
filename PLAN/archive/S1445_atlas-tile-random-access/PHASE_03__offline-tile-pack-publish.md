# Phase 03 - Offline tile pack and publish

**Strategic spec:** [`../S1445_atlas-tile-random-access.md`](../S1445_atlas-tile-random-access.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Cut both sprite sheets into tile packs, publish them as new asset revisions, and point the app's descriptors at them.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (the entry-name contract is fixed).
- [ ] `ffmpeg` resolvable by `Get-FfmpegExe`; `gh` resolvable by `Get-GhExe`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | ≤ 2200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalog.kt` | Modified | ≤ 400 |

---

## Steps

### Step 03.1 - Add the pack builder to the offline packer

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `function Build-TilePackFromSheet` taking a sheet path, a coords sidecar path, the tile width/height and column count, and an output zip path. It runs one `ffmpeg` pass with `-vf untile=<cols>x<rows>` over the sheet into per-tile WebP files in a staging directory, renames each output to the plain decimal slot index with no extension, drops every index absent from the coords sidecar, and packs the survivors into the output zip with `Compress-Archive -CompressionLevel NoCompression`. Print the entry count and the resulting size, and report the SHA-256 of the zip. Wire two thin callers, one per payload, using the geometry each already declares.

**Why:**

Strategic ADR-1 makes the tile container the app payload, and cutting it from the already published sheet - rather than from freshly captured frames - is what guarantees the tile indices still match the published `url -> index` sidecar, which strategic §2 keeps unchanged.

**Verification:**

- `Grep` - `function Build-TilePackFromSheet` matches once.
- Run the builder for the channel-preview sheet; expected: entry count equals the sidecar entry count (1881), zip written.
- Record `expected: 1881 entries | actual: <n>`.

**Status:** `[x]` done

---

### Step 03.2 - Build both packs and check their weight

**Files:** -
**Depends on:** Step 03.1

**Prompt for developer:**

> Build the channel-preview pack and the stream-logo pack from the published sheets downloaded into `temp/S1445/`. Compare each pack's size against its sheet. If a pack exceeds its sheet by more than a quarter, lower the per-tile WebP quality until it does not, and record the setting used.

**Why:**

Strategic §3.2 caps the payload at roughly the current 11,4 MB and 6,6 MB, because the user is asked to download this over mobile data and a fix that doubles the download trades one complaint for another.

**Verification:**

- Record `expected: <= 14 MB | actual: <n> MB` for the preview pack and `expected: <= 8 MB | actual: <n> MB` for the logo pack.
- Both zips open with `Expand-Archive -WhatIf` or an entry listing without error.

**Status:** `[x]` done

---

### Step 03.3 - Publish the packs as new asset revisions

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Extend the two publish functions to upload the packs as `channel-preview-tiles-v2.zip` and `stream-logo-tiles-v2.zip` to the `delivery-so-v1` release with `--clobber`, alongside the existing sheet assets, which stay untouched. Print the SHA-256 and byte size of each uploaded pack.

**Why:**

Strategic ADR-2 keeps the sprite sheet published for third-party consumers of the catalog, so the pack is added to the release rather than replacing anything; strategic §3.2 requires the new revision suffix so a payload is never re-uploaded under a name whose pins are already shipped.

**Verification:**

- `gh release view delivery-so-v1` lists both new assets with the printed sizes.
- The pre-existing `channel-preview-atlas-v1.webp` and `stream-logo-atlas-v1.webp` are still listed with their original sizes.

**Status:** `[x]` done

---

### Step 03.4 - Repoint the descriptors

**Files:** `DeliverableDescriptorCatalog.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Replace the sheet resource with the pack resource in `channelPreviewAtlas()` and `streamLogoAtlas()`, keeping the coords sidecar entry as is, and set the new SHA-256 and size pins from Step 03.3 output. Update the pin comment above each descriptor to state which build the pins came from.

**Why:**

Strategic ADR-5 keeps the existing `DeliverableSet` and changes only its file list and pins, so an already installed payload reports an available update through the path S1200 already built instead of silently mismatching.

**Verification:**

- `Grep` - `channel-preview-tiles.zip` and `stream-logo-tiles.zip` each match once in the catalog file.
- `Grep` - the old sheet file names no longer appear in the descriptor file lists.
- `pwsh -NoProfile -File ./a.ps1 fk` - expected exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` exit 0.
- [ ] Dev log entry added for the files in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

After this phase a fresh install downloads packs only; an existing install keeps its sheet until the user accepts the update offer. Phase 04 documents the container and gates the device verdict.

---

## Rollback Plan

Revert the descriptor pins to the sheet resources - the published sheet assets were never removed, so the previous payload is still fetchable byte for byte.

# Phase 06 - Offline atlas packer

**Strategic spec:** [`../S1154_channel-preview-atlas.md`](../S1154_channel-preview-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-07-26
**Completed:** 2026-07-26

---

## Objective

Extend the offline packer to build the 240x135-tile channel-preview atlas (single 8192x8192 sheet + `url->index` sidecar) sharing the exact tile geometry with the on-device slicer (ADR-5), publish it as a SEPARATE versioned release asset (not inside `stream-catalog.zip`), and finalize the descriptor integrity pins in `DeliverableDescriptorCatalog` from the real binary. This phase is DEVICE/OPS-GATED: producing the sheet requires capturing a frame per ~2025 live channel.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`channelPreviewAtlas()` descriptor with placeholder pins exists).
- [ ] Phase 02 is ✅ Done (`ChannelPreviewAtlasSlicer` geometry consts `TILE_W=240/TILE_H=135/COLS=34` are the invariant to mirror).
- [ ] `gh` CLI available (packer publishes to the `delivery-so-v1` release tag).
- [ ] Network access to the ~2025 live channels for frame capture.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalog.kt` | Modified | ≤ 190 |

> No `res/layout/*.xml` edits - no landscape-parity obligation. This phase is a build/ops script plus a data-constant finalize.

---

## Steps

### Step 06.1 - Atlas geometry constants + builder

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add packer-side geometry constants mirroring the slicer contract: `$script:PreviewTileW = 240`, `$script:PreviewTileH = 135`, `$script:PreviewCols = 34` with a comment naming `ChannelPreviewAtlasSlicer` as the paired invariant (as the existing `$FaviconTile/$FaviconCols` comment names `FaviconAtlasSlicer`). Add `function Build-ChannelPreviewAtlas` that, given the ordered VIDEO rows and per-channel captured frames, composes a single sheet (`col = index % PreviewCols`, `row = index / PreviewCols`, tile at `col*240,row*135`) and emits the `url->index` map. Skip audio/radio rows (VIDEO only).

**Verification:**

- `Grep` - `PreviewTileW = 240`, `PreviewTileH = 135`, `PreviewCols = 34` present in the script.
- `Grep` - `function Build-ChannelPreviewAtlas` present.
- `Grep` - the geometry comment names `ChannelPreviewAtlasSlicer`.

**Result (2026-07-26):** all three greps pass. Frame capture is `Invoke-ChannelPreviewCapture` (ffmpeg,
`-frames:v 1 -update 1`, per-channel hard timeout, frames cached under `temp/channel-preview-frames/`
so an interrupted run resumes). Two traps cost a debug cycle and are now pinned by comments in the
script: `Start-Process -ArgumentList` does not quote, so a user-agent containing spaces broke every
capture; and a `-Parallel` runspace does not inherit the working directory, so the frame path must be
absolute.

**Status:** `[x]` done

---

### Step 06.2 - Separate-asset publish path

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a publish path that uploads the atlas sheet + sidecar as their OWN release asset (e.g. `channel-preview-atlas.zip` on the `delivery-so-v1` tag via `gh release upload`), NOT bundled into `stream-catalog.zip` (ADR-5: independent lifecycle / 20-50 MB weight). Mirror the failure handling of `Invoke-PublishCatalog` (throw with a clear message if `gh` is missing). Guard the publish behind an explicit switch so a routine catalog refresh does not force an atlas rebuild.

**Verification:**

- `Grep` - `channel-preview-atlas` upload asset referenced in the publish path.
- `Grep` - the atlas publish is distinct from the `stream-catalog.zip` path (separate `gh release upload` call).
- Run the script's `-WhatIf`/dry validation (no live upload) - exits 0.

**Deviation from the prompt (2026-07-26):** the payload is published as TWO plain assets,
`channel-preview-atlas-v1.webp` + `channel-preview-coords-v1.json`, not as a `.zip`. The already
shipped `DeliverableDescriptorCatalog.channelPreviewAtlas()` declares one `PayloadFile` per file and
the downloader fetches each by its `withRev()` asset name - a zip would never be unpacked. The rest of
the prompt holds: own `gh release upload` call, separate from `stream-catalog.zip`, behind the explicit
`-PublishPreviewAtlas` switch, `gh` resolved through the shared `Get-GhExe` helper (extracted from
`Invoke-PublishCatalog`, which now uses it too).

**Result (2026-07-26):** end-to-end smoke run `-WithChannelPreviews -PreviewLimit 12` produced 10/12
frames, packed an 8160x135 sheet, encoded the WebP, wrote the sidecar, exit 0.

**Status:** `[x]` done

---

### Step 06.3 - Generate and publish the binary (device/ops)

**Files:** `scripts/streams/collect-stream-candidates.ps1` (invocation only)
**Depends on:** Step 06.1, Step 06.2

**Prompt for developer:**

> Run the packer against the ~2025 live VIDEO channels to produce `channel-preview-atlas.webp` + `channel-preview-coords.json`, then publish the atlas asset. Record the produced file sizes and SHA-256 of each file (needed by Step 06.4). This is the device/ops-gated step - it captures a real frame per live channel and uploads via `gh`.

**Verification:**

- The atlas asset exists on the `delivery-so-v1` release (`gh release view delivery-so-v1` lists it).
- The generated sidecar's max index fits the sheet (`isInBounds` holds for `8192x8192`).
- SHA-256 + byte size of both files recorded for Step 06.4.

**Result (2026-07-26):** capture over the 2077 catalog VIDEO rows produced 1881 frames in 10 min
(throttle 20, 20 s per channel); 196 channels returned nothing and simply get no tile. Sheet
8160x7560 (56 of the 60 available rows, so the 2040-slot ceiling was never hit), max index 1880 ->
bottom 7560, in bounds.

- `channel-preview-atlas-v1.webp` - 11,358,632 B, sha256 `7d3e6422ae1fa7ff251b9cd8db20316b72313ffb713d54bc18403111738424ca`
- `channel-preview-coords-v1.json` - 134,997 B, sha256 `be60d35c838d14e584350c2403f22faaa4077a5f0176ed1ceb75e7df760259d9`

Both live on `delivery-so-v1`; a direct GET of the sidecar returns HTTP 200 (it was the 404 in the
owner's 2026-07-26 device log).

**Status:** `[x]` done

---

### Step 06.4 - Finalize descriptor pins

**Files:** `data/delivery/DeliverableDescriptorCatalog.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> Replace the Phase 01 placeholder `const` pins in `channelPreviewAtlas()` with the real SHA-256 and `minSize` recorded in Step 06.3 for both `channel-preview-atlas.webp` and `channel-preview-coords.json`, and point the `sources` at the published asset URL. Remove the `// FINALIZED in Phase 06` placeholder note. After this, the runtime download verifies and installs.

**Verification:**

- `Grep` - the two pins are no longer zero/empty placeholders (real 64-char SHA-256 hex present).
- `Grep` - `FINALIZED in Phase 06` note removed (zero hits).
- `.\a.ps1 fk` compiles.

**Note:** the `sources` URLs needed no change - `resource()` already resolves
`<mirror>/channel-preview-atlas-v1.webp`, which is exactly the published asset name.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Packer script runs clean (`-WithChannelPreviews -PreviewLimit 12` smoke run, then the full run, both exit 0).
- [x] Descriptor compiles with real pins - `.\a.ps1 fk` BUILD SUCCESSFUL.
- [x] Dev log entry added for the script + descriptor change.
- [x] **Ops:** both assets are live on `delivery-so-v1` and return HTTP 200.
- [ ] **Device-gated:** on-device download verifies against the finalized pins and the grid renders atlas previews (stays open until the owner's device test).

---

## Handoff Notes to Next Phase

- The atlas binary is published and the descriptor is finalized; Phase 03's grid render and Phase 04's prompt are now end-to-end verifiable on device.

---

## Rollback Plan

Revert the descriptor finalize (restores placeholder pins - download simply fails safely) and the script additions. Deleting the published asset is optional (the descriptor no longer points at it after revert).

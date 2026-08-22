# Streams Source Spec - 09 - Logo + Channel-Preview Sprite-Atlases (on-demand delivery)

> **Read `04_favicon_atlas.md` first.** This file describes two *further* atlas systems that arrive by a
> different route and sit in front of the favicon in the render order. They are additive: an install that
> has neither still renders every channel, because the favicon tier below them always exists.

## 0. Why this is a separate file

`04` documents the **favicon** atlas, which ships inside `stream-catalog.zip` alongside `streams.csv` and
is part of the bank contract in `01`. The two atlases here are **not in that ZIP** and are not part of the
bank. They arrive through the app's generic on-demand *Deliverable* mechanism (S0386), each as its own
downloadable payload, and either one may simply be absent on a given install.

| | favicon (file `04`) | channel-preview (here) | stream-logo (here) |
|---|---|---|---|
| Ticket | S0761 era | S1154 | S1201 |
| Delivered in | `stream-catalog.zip` | `DeliverableSet.CHANNEL_PREVIEW_ATLAS` | `DeliverableSet.STREAM_LOGO_ATLAS` |
| Applies to | every channel | VIDEO channels only | every channel |
| May be absent | no (part of the bank) | yes | yes |

`DeliverableSet.kt:14-21` declares both entries; its KDoc states the logo atlas "covers what the preview
atlas structurally cannot - a station with no video track has no frame to capture."

## 1. Geometry contract **[CONTRACT]**

Both are fixed-grid sheets indexed row-major, exactly like the favicon atlas - only the numbers differ.
The app half and the offline packer half each hard-code the grid, and they must agree or every rect drifts.

| | channel-preview | stream-logo |
|---|---|---|
| Tile width | 240 | 136 |
| Tile height | 135 | 136 |
| Columns | 34 | 59 |
| Max rows | none - height follows the tile count | none - height follows the tile count |
| Max tiles | 4114 (format-bound) | 7080 (format-bound) |
| Max sheet | 8160 x 16383 (WebP limit) | 8024 x 16383 (WebP limit) |

**The row maximum is retired on both sheets, and this is a breaking change for any reimplementation that
hard-coded it.** Neither sheet has a fixed row count any more: the height follows the tile count, and the
only ceiling is the format's - VP8 stores a dimension in 14 bits, so no WebP side may exceed 16383 px.
The retired caps (60 rows = 2040 preview tiles, 60 rows = 3540 logo tiles) came from a self-imposed
"one 8k x 8k sheet" budget that no consumer ever declared, and on both sheets that budget silently dropped
real artwork: 877 video channels on the preview sheet (S1831), and 608 logos reaching 1593 channel urls on
the logo sheet (S1841, measured 2026-08-20 - the packer lays out 4148 tiles covering 5468 channels where
the published sheet stopped at 3540 covering 3875).

A packer over the format limit now **refuses** rather than trimming: a run that cannot cover every channel
is reported, never published partially. Read the row from the tile index (`row = index / COLS`) and take
the sheet height from the image, never from a constant.
| Sheet format | WebP, quality 80, **no alpha**, black-cleared | WebP, quality 90, **alpha preserved**, transparent-cleared |

App side: `ChannelPreviewAtlasSlicer.kt:130-132` (`TILE_W=240`, `TILE_H=135`, `COLS=34`) and
`StreamLogoAtlasSlicer.kt:138-140` (`TILE_W=136`, `TILE_H=136`, `COLS=59`).
Packer side: `collect-stream-candidates.ps1:1730-1734` and `:2007-2010`.
`Build-TilePackFromSheet` (`collect-stream-candidates.ps1:2274-2280`) throws at pack-build time when the
measured sheet column count disagrees with the app contract, so a drift fails the producer rather than
shipping skewed art.

**Why the logo tile is 136 and not an odd number** (`StreamLogoAtlasSlicer.kt:134-137`): the sheet is lossy
WebP, which is always 4:2:0, so an odd tile size would put every second tile boundary mid-chroma-block and
bleed one tile's edge colour into its neighbour. Any reimplementation packing its own sheet inherits this
constraint.

**Alpha differs and it matters.** The logo sheet carries real alpha and the app forces
`Bitmap.Config.ARGB_8888` on decode (`StreamLogoAtlasSlicer.kt:77`) so the area around a logo takes the
cell's own colour (`StreamLogoAtlasSlicer.kt:24-26`). The preview sheet has no alpha and is decoded with
default options (`ChannelPreviewAtlasSlicer.kt:79`).

### 1.1 index -> pixel rect

Identical row-major rule to `04` section 1.1, with this file's constants:

```text
row    = index / COLS
col    = index % COLS
left   = col * TILE_W
top    = row * TILE_H
right  = left + TILE_W
bottom = top  + TILE_H
```

## 2. Sidecar and keying **[CONTRACT for names and key scheme]**

Both sidecars are a flat JSON object mapping the stream **`url`** to an integer tile index - the same key
scheme as the favicon atlas, for the same reason given in `04` section 3.2 (the entity `id` is a fresh UUID
on every re-import and is therefore useless as a cross-payload key).

| Deliverable | on-device directory | sheet | tile pack | sidecar |
|---|---|---|---|---|
| `CHANNEL_PREVIEW_ATLAS` | `filesDir/delivery/CHANNEL_PREVIEW_ATLAS/` | `channel-preview-atlas.webp` | `channel-preview-tiles.zip` | `channel-preview-coords.json` |
| `STREAM_LOGO_ATLAS` | `filesDir/delivery/STREAM_LOGO_ATLAS/` | `stream-logo-atlas.webp` | `stream-logo-tiles.zip` | `stream-logo-coords.json` |

Sources: `ChannelPreviewAtlasStore.kt:26-29`, `StreamLogoAtlasStore.kt:28-31`. Both parse the sidecar with
the same `decodeCoords` routine (`ChannelPreviewAtlasStore.kt:59-72`, `StreamLogoAtlasStore.kt:61-74`).

## 3. Delivery **[CONTRACT-adjacent]**

Descriptors live in `DeliverableDescriptorCatalog.kt`: `channelPreviewAtlas()` at `:118-124` and
`streamLogoAtlas()` at `:136-142`. Each declares exactly two resources - the **tile-pack ZIP** and the
**coords JSON**:

- `channel-preview-tiles.zip` (min size 1_000_000) + `channel-preview-coords.json` (min size 32_768)
- `stream-logo-tiles.zip` (min size 1_000_000) + `stream-logo-coords.json` (min size 32_768)

Both payload sets are **unpinned** - no SHA-256 (`UNPINNED = ""`, `DeliverableDescriptorCatalog.kt:175`),
a deliberate policy split from native code payloads recorded under S1483 at
`DeliverableDescriptorCatalog.kt:97-108`: artwork is refreshed on its own cadence, code is not.

Note for a reimplementation: the descriptor catalogue declares only the `.zip` form. The `.webp` sheet
filenames appear in the stores but in no descriptor, so **how the sheet variant reaches `filesDir` is not
stated** in the delivery code - treat the tile pack as the delivered artifact and the sheet as legacy state.

## 4. Two read paths - tile pack first, sheet as fallback

Since S1445 the sprite sheet is no longer the primary path:

1. **Tile pack (primary).** A ZIP whose entries are individual tiles named by decimal slot index, stored
   uncompressed, read through `StreamTilePackReader` with an LRU `Bitmap` cache
   (`StreamTilePackReader.kt:28-30,56-59`).
2. **Sheet (fallback).** One large WebP decoded per tile with a cached `BitmapRegionDecoder.decodeRegion`
   (`StreamLogoAtlasSlicer.kt:110-131`).

`tileFor()` in both slicers checks `pack.hasPack()` first and only falls back to the region decoder when the
pack is absent (`StreamLogoAtlasSlicer.kt:73-76`, `ChannelPreviewAtlasSlicer.kt:67-70`). The reason is cost:
a region decode out of a sprite sheet costs a share of a full-sheet decode, so the sheet path survives only
"for installs that have not taken the payload update" (`StreamLogoAtlasSlicer.kt:32-35`).

S1220 constrains the implementation: every decoder-member access stays inside one `try` block, because
`invalidate()` can recycle the decoder mid-read (`StreamLogoAtlasSlicer.kt:78`,
`ChannelPreviewAtlasSlicer.kt:71`, `StreamTilePackReader.kt:64`).

## 5. Render fallback order **[CONTRACT for observable behaviour]**

Decided in `StreamGridAdapter.VH.bind()` (`StreamGridAdapter.kt:167-211`):

```text
captured live frame  >  channel-preview tile (VIDEO only)  >  logo tile  >  favicon tile  >  kind placeholder
```

- A captured live frame (`04` section 9) wins outright; no atlas is consulted.
- A kind placeholder is shown first, then for a capture-able VIDEO channel the preview atlas is tried, and
  a miss falls through to the logo tier and then to the favicon (`StreamGridAdapter.kt:199-202`).
- For radio and other non-VIDEO channels the preview tier is skipped entirely - "radio has no frame to
  capture and no preview tile, so the logo tier is its first real chance at a picture"
  (`StreamGridAdapter.kt:207-208`) - and a miss falls through to the favicon.

Every tier degrades to the next on a miss, so an install with neither payload behaves exactly like the
favicon-only behaviour documented in `04`.

## 6. Portability

The **formats are platform-neutral**: a WebP sprite sheet on a fixed grid, or a ZIP of decimal-indexed
uncompressed tiles, plus a flat JSON `url -> index` sidecar. Nothing in the container or the coordinate
scheme is Android-specific, and coordinate persistence is a plain file, not a database table - no Room is
involved in any of these classes.

The **reader implementations are Android-bound** and would be rewritten rather than ported:
`BitmapRegionDecoder` / `Bitmap` / `BitmapFactory` / `Rect` for partial-region decode
(`StreamLogoAtlasSlicer.kt:3-6`), an SDK-level gate choosing `BitmapRegionDecoder.newInstance(stream)` over
the deprecated two-argument overload on API 31+ (`StreamLogoAtlasSlicer.kt:121-126`), `android.util.LruCache`
plus `ActivityManager.memoryClass` to size the tile cache at an eighth of the app heap class
(`StreamTilePackReader.kt:136-139`), and `ApplicationContext`/`filesDir` to locate the delivery directory.

## 7. Ticket index for this file

| Ticket | Claim | Where |
|---|---|---|
| S1154 | on-demand channel-preview sprite sheet + `url->index` sidecar | `ChannelPreviewAtlasSlicer.kt:17`, `DeliverableSet.kt:10` |
| S1201 | on-demand stream-logo sprite sheet + `url->index` sidecar | `StreamLogoAtlasSlicer.kt:17`, `DeliverableSet.kt:11` |
| S1445 | tile-pack container becomes the primary path, sheet becomes fallback | `StreamTilePackReader.kt:18`, `StreamLogoAtlasSlicer.kt:32` |
| S1220 | decoder-member access stays in one try block - `invalidate()` can recycle mid-read | `StreamLogoAtlasSlicer.kt:78`, `StreamTilePackReader.kt:64` |
| S1483 | artwork payloads are unpinned; code payloads are not | `DeliverableDescriptorCatalog.kt:97-108` |
| S0386 | the generic Deliverable mechanism these two ride on | `DeliverableSet.kt:3` |

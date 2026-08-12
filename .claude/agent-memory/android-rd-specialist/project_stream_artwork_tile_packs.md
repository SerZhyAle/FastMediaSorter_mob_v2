---
name: stream-artwork-tile-packs
description: The app fetches stream artwork as ZIP tile packs, not the sprite sheets - a sheet has no random access, so per-tile region decode made the grid fill one picture at a time (S1445)
metadata:
  type: project
---

Since 2026-08-06 (S1445) the app downloads `channel-preview-tiles.zip` and `stream-logo-tiles.zip`
(release `delivery-so-v1`), NOT the `*-atlas-*.webp` sprite sheets. The sheets are still published for
third-party consumers of the catalog; only the app moved.

**Current revisions (2026-08-07 rebuild):** packs `-v3`, coords `-v2`, sheets `-v2`. 1949 preview
entries (11 213 000 B) and 2006 logo entries covering 2350 channels (6 316 756 B). Every earlier
revision stays published untouched - a rebuild NEVER clobbers a name an installed app pins.

**Why:** a sprite sheet is not randomly addressable. `BitmapRegionDecoder.decodeRegion` over WebP (and
JPEG) walks the stream from the top to reach the requested row, so one tile costs a share of a full
sheet decode - measured 1,48 s for the 8160x7560 / 61,7 Mpx preview sheet on the dev PC, several times
that on a phone. With no tile cache anywhere, a grid of cells asking one tile each filled one cell at
a time and most decodes landed after their cell was recycled (the owner's device logs showed the
S1154 probe firing 9 times in four days over ~2000 video channels). That is the "обложки появляются
по одной" report of 2026-08-06.

**How to apply:**
- Container contract: ZIP with STORED entries; entry name is the slot index as a plain decimal string
  with no extension; the `url -> index` sidecar is unchanged and shared with the sheet. Reader is
  `StreamTilePackReader` (byte-budget `LruCache`); both slicers keep the sheet path as the fallback
  for installs that have not accepted the new payload.
- Rebuild/publish: `collect-stream-candidates.ps1 -WithTilePacks -PublishTilePacks`. It cuts the pack
  FROM the published sheet (one `ffmpeg untile` pass), which is what keeps tile indices identical to
  the sidecar. Verify a cut by PSNR against a sheet crop - a matching slot scored 31 dB, the
  neighbouring slot 8 dB.
- ffmpeg trap: `untile` refuses a grid whose tile size is not a whole number of chroma blocks, and the
  preview tile is 135 px tall (odd). Insert `format=rgb24` (or `rgba` for the logo sheet, whose
  transparent margins are load-bearing) before `untile`.
- Packs came out SMALLER than the sheets: 10 840 856 B vs 11 358 632 B (previews), 5 782 986 B vs
  6 645 666 B (logos) - per-tile encoding is not a size regression.
- Changing pins is what makes an installed copy read as stale (S1200), so an existing install is
  offered the new payload; until the user accepts it, they stay on the slow sheet path by design.

**Related memories:** [[stream-catalog-atlas-publish]], [[stream-favicon-atlas-delivery]].

---
name: atlas-publish-needs-tile-packs
description: Publishing a rebuilt preview/logo atlas only writes revision names; the app fetches stable names written solely by -WithTilePacks -PublishTilePacks
metadata:
  type: project
---

`-WithChannelPreviews -PublishPreviewAtlas` and `-WithStreamLogos -PublishStreamLogoAtlas` publish **only** revisioned assets (`channel-preview-atlas-v3.webp`, `stream-logo-atlas-v3.webp`). The app never fetches those - `ChannelPreviewAtlasStore` / `StreamLogoAtlasStore` + `DeliverableDescriptorCatalog` request the **stable** names `channel-preview-tiles.zip`, `channel-preview-coords.json`, `stream-logo-tiles.zip`, `stream-logo-coords.json`, which are written only by a separate run:

    pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -WithTilePacks -PublishTilePacks

**Why:** the sheets keep revisions for third parties; the app side is deliberately unpinned + stable-named so pictures refresh without an app release (S1483). Stopping after the atlas build looks fully successful - exit 0, "Published .. -> delivery-so-v1" - while users still get the previous payload. Observed 2026-08-12: both sheets rebuilt and published, yet the four stable assets were still dated 2026-08-07 until the tile-pack run.

**How to apply:** any "refresh the atlases" request is a **two-step** job - build/publish the sheets, then `-WithTilePacks -PublishTilePacks`. Verify by timestamp, not by exit code: `gh release view delivery-so-v1 --json assets` and confirm the four stable names carry today's date and that the coords sha256 matches the sheet just built.

**Superseded 2026-08-20 (S1831): the 2040-slot preview ceiling is gone.** It used to cap the sheet at
60 rows via a self-imposed 8192x8192 budget, and the overflow was dropped with a WARNING while the run
still reported success - that alone left 877 of 2917 video channels with no preview. Now the width is
fixed at 8160 px (240x135 tiles, 34 columns) and **the height follows the tile count**; the 2026-08-20
build is 8160x11340, 2830 tiles in 84 rows, 15,9 MiB. The real bounds are the WebP side limit of 16383 px
(121 rows = 4114 tiles) and the 48 MiB file size StreamsPlayer declared, and a build that cannot place
every tile now **fails instead of truncating**.

**How to apply:** never assume a row count for the preview sheet - derive it from the image. Anything
holding 60 rows slices the wrong rect for every tile past the first screenful, which looks like plausible
pictures on the wrong channels rather than like a bug. Same shape of trap as a stale favicon atlas.
Related: [[stream-catalog-delivery]], [[streams-player-catalog-consumer]].

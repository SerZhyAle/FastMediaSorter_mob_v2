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

Second trap in the same area: the preview sheet caps at **2040 slots**. With ~2900 video channels the run captures more frames than fit and drops the overflow with a WARNING, which is a capacity ceiling, not a failure - the uncovered channels fall back to the station logo. Related: [[stream-catalog-delivery]].

---
name: stream-favicon-atlas-delivery
description: Streams list showing country flags instead of channel icons = published catalog zip is missing favicon-atlas.png; fix by re-publishing; S0925 guards it
type: project
---

Symptom "streams list shows a country flag in every leading slot, never a channel icon" (reported under S0785 on 2026-07-03) is a **catalog-delivery** failure, not app code.

**Chain:** app import (`ImportStreamCatalogUseCase`) reads `stream-catalog.zip` (GitHub release tag `delivery-so-v1`). Icons come from a favicon sprite-atlas: the zip must contain `favicon-atlas.png` AND the CSV must have `favicon_index` populated. `FaviconAtlasStore.write(atlasBytes=null, coords)` **deletes the atlas and writes an EMPTY coords map** when `atlasPng == null` -> every row's `faviconResolver(url)` returns null -> every catalog row falls to the S0785 country-flag fallback. So "all flags, no icons" == atlas didn't ship. The flag ALSO appears as a separate country chip in metadata (S0761) - that's by design, unrelated to the leading slot.

**Root cause of the 2026-07-03 incident:** `Invoke-PublishCatalog` in `scripts/streams/collect-stream-candidates.ps1` silently published CSV-only because the atlas file wasn't found at publish time (relative path `delivery/stream-catalog/favicon-atlas.png` unresolved, e.g. run from a release worktree). The CSV had 1636 populated favicon_index rows but the zip carried only streams.csv.

**Fix (2026-07-04):** re-publish with `pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -SkipLiveness -Publish` from repo root (`-SkipLiveness` avoids the ~2489-URL probe and does NOT mutate the CSV, so it stays consistent with the atlas). Verified end-to-end on emulator: import lands `files/streams/favicon-atlas.png` (2.43 MB) + non-empty `favicon-coords.json`, leading slot shows atlas icon when a tile exists, country flag otherwise.

**Recurrence guard = S0925** ("stream-catalog-publish-drops-favicon-atlas"): `Invoke-PublishCatalog` now throws if the CSV carries favicon_index but no atlas was bundled, unless `-AllowFaviconlessPublish`. An over-cap atlas (> `$MaxAtlasBytes` 3 MB) is the deliberate S0583 CSV-only fallback and is NOT failed.

**Why:** the whole S0668/S0785 favicon feature looks broken to users whenever a publish drops the atlas; the failure is invisible on the publishing side (CSV-only "succeeds").

**How to apply:** if streams show flags-not-icons, first check the published zip (`unzip -l` the release asset) for `favicon-atlas.png` before touching adapter/app code. Atlas geometry: 32px tiles, 16 cols; atlas capacity must cover max favicon_index (512x3296 = 1648 tiles covered 1636 rows in this catalog).

---
name: stream-catalog-atlas-publish
description: Favicon atlas ships in the stream-catalog.zip release asset; publishing without it silently wipes all favicons
metadata:
  type: project
---

The stream favicon sprite-atlas (S0668) ships as `favicon-atlas.png` INSIDE the `stream-catalog.zip` GitHub Release asset (tag `delivery-so-v1`), alongside `streams.csv`. The CSV carries per-row `favicon_index` (column 18) pointing into that atlas.

**Why:** if the published zip has the CSV but NOT the atlas, `ImportStreamCatalogUseCase.extractCatalog()` gets `atlasPng=null`, and `FaviconAtlasStore.write(null, coords)` takes the null branch that DELETES the atlas and writes EMPTY coords - so every channel renders with no favicon on every device/orientation. The main-window streams panel in portrait is icon-only, so it degrades to raw text and the bug is glaring there; landscape masks it behind always-on labels. This actually shipped once (S0925, 2026-07-04): a publish dropped the atlas, favicons vanished app-wide.

**How to apply:**
- To re-publish the existing consistent CSV+atlas pair (no favicon re-fetch): `collect-stream-candidates.ps1 -CatalogOnly -SkipLiveness -Publish` (bundles both, uploads `--clobber`). Needs `gh` on PATH ([[gh-cli-location]]).
- `Invoke-PublishCatalog` now has an S0925 guard: it REFUSES to publish a CSV that has `favicon_index` values without bundling the atlas, unless `-AllowFaviconlessPublish` is passed (for the intentional over-cap / no-favicon case).
- The app only fetches the atlas on an explicit catalog import/refresh (Streams screen refresh, or Welcome enable-all) - not automatically on app update. Users who already imported a broken catalog keep seeing no favicons until they manually refresh.
- Consistency check before a manual re-bundle: max `favicon_index` in the CSV must be < atlas tile capacity (`(width/32) * (height/32)`; atlas grid is 32px tiles, 16 cols). The app guards out-of-bounds indices (no crash), but a stale atlas can show wrong tiles.

**Recurrence 2026-07-12 (SAME bug, different path):** favicons vanished app-wide again. Root cause was NOT `Invoke-PublishCatalog` (its S0925 guard held) - it was an UNGUARDED second publish path: `/spec-prerelease` Step 0 in `.claude/commands/spec-prerelease.md` did `Compress-Archive -Path delivery/stream-catalog/streams.csv ...` (CSV only, no atlas) + raw `gh release upload delivery-so-v1 temp/stream-catalog.zip --clobber`, bypassing the guard entirely. Published zip = 135 KB (CSV only) vs healthy 2.5 MB (CSV+atlas). Fixed the prod asset by re-running the guarded packer (`-CatalogOnly -SkipLiveness -Publish`) and rewrote that spec-prerelease snippet to call the guarded packer instead. **Lesson: the S0925 guard only lives inside `Invoke-PublishCatalog`; any raw `gh release upload delivery-so-v1 stream-catalog.zip` is unguarded and can drop the atlas. Grep for `gh release upload delivery-so-v1` before trusting the guard is universal.** After any re-publish, on-device users must MANUALLY refresh the Streams catalog (app does not auto-fetch on update) to regain icons.

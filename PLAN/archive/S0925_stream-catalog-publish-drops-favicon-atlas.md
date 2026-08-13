# S0925 - Stream catalog publish drops the favicon atlas

**Status:** Archived

## 0. Symptom & evidence

- Report: in portrait, pinned channels on the main-window streams panel render as plain text, missing the favicon thumbnails that should come from the sprite-atlas. Confirmed by owner: favicons appear NOWHERE (not landscape panel, not the full Streams screen).
- The favicon render path is fully orientation-independent (`FaviconAtlasStore` + `FaviconAtlasSlicer` + `faviconCoords[url]`); the chip layout has no land variant. The only orientation difference is `R.bool.main_streams_panel_show_labels` (portrait=false icon-only, landscape=true label+icon). So portrait merely EXPOSES a missing icon; landscape masks it behind the always-on label.
- Device evidence (emulator-5554): `filesDir/streams/` does not exist -> no atlas, empty coords -> `index == null` for every channel -> text fallback.
- Published release asset `stream-catalog.zip` (tag `delivery-so-v1`) contains ONLY `streams.csv` - no `favicon-atlas.png`.
- Published `streams.csv` is byte-identical (md5) to `delivery/stream-catalog/streams.csv`; it has 1636 rows with `favicon_index` (max index 1635).
- Local `delivery/stream-catalog/favicon-atlas.png` exists, is git-tracked, 2.43 MB (< 3 MB cap), 512x3296 px = 16x103 = 1648 tiles. Max index 1635 < 1648 -> the local atlas is a valid, consistent match for the published CSV.

## 1. Root cause

- The catalog publish shipped `stream-catalog.zip` with the CSV only, dropping the matching valid favicon atlas.
- On import, `ImportStreamCatalogUseCase.extractCatalog()` finds no `favicon-atlas.png` -> `atlasPng = null`.
- `FaviconAtlasStore.write(null, coords)` takes the `atlasBytes == null` branch: it DELETES any atlas and writes an EMPTY coords map (the passed coords are discarded).
- Net: `atlasFile() == null`, `coords() == {}` -> no favicon renders for any channel on any device/orientation.
- The app code (extract / write / slice / adapter) is correct. This is a publish-pipeline / release-artifact defect.

## 2. Contributing footgun

- `Invoke-PublishCatalog` in `scripts/streams/collect-stream-candidates.ps1` bundles the atlas only when the PNG exists on disk and fits the size cap; otherwise it silently publishes CSV-only.
- It does not check whether the CSV carries `favicon_index` values. A CSV with favicon indices but no bundled atlas is a broken artifact (the app wipes favicons), yet publish emits it without complaint. This is how the incident shipped.

## 3. Plan

- Harden `Invoke-PublishCatalog`: when the CSV about to be published has any `favicon_index >= 0` and no atlas is bundled, fail with a clear error, guarded by an explicit override switch (`-AllowFaviconlessPublish`) for the intentional over-cap / no-favicon case.
- Re-publish the existing consistent pair (no favicon re-fetch needed): `collect-stream-candidates.ps1 -CatalogOnly -SkipLiveness -Publish` bundles CSV + atlas and re-uploads with `--clobber`. Requires `gh` on PATH (installed at `C:\Program Files\GitHub CLI\gh.exe`, not on PATH by default).

## 4. Verification

- Re-download `stream-catalog.zip` and assert it contains both `streams.csv` (entry 0) and `favicon-atlas.png`.
- End-to-end on device: Streams screen -> refresh/import catalog -> `filesDir/streams/favicon-atlas.png` + non-empty `favicon-coords.json` land -> pinned-channel chips show favicons (portrait icon-only shows the thumbnail, not text).

## 5. Notes

- No app-code (`.kt`) change: the fix is the publish guard plus a corrected release artifact. No `BlockNeedUserTest` debug tag applies (no changed `.kt` flow).

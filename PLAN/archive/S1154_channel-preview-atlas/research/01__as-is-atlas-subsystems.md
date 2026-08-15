# S1154 Research 01 - AS-IS subsystems for a channel-preview atlas

**Захвачено:** 2026-07-23 (feeds strategic §4/§5/§6/§7/§9). Read-only codebase research.

---

## Downloadable extensions ("Скачиваемые расширения")

- Contract: `domain/delivery/DeliverableInventory.kt` (`ExtensionItem` sealed: `Module`, `LanguageData`, `Catalog`; `ExtensionSection.STREAMS` already exists).
- Impl: `data/delivery/DeliverableInventoryImpl.kt` - already has a `STREAM_CATALOG` row (`ExtensionItem.Catalog`, id `stream_catalog`, ~2.5 MB) wired to `ImportStreamCatalogUseCase`.
- UI: `ui/delivery/ExtensionsManagerFragment.kt` + `ExtensionsManagerViewModel.kt` - grouped list, per-row download/uninstall, bulk actions.
- **Two download contracts:**
  - Ad-hoc `ExtensionItem.Catalog`: small mutable fetch, NO real byte-progress, "uninstall" is a no-op reset (catalog re-importable). Used by `stream_catalog`.
  - `DeliverableSet` (closed enum: `TRANSLATION, OCR_ENGINES, AUDIO_VISUALIZATIONS, FFMPEG_DTS`) + `DeliverableSetDownloader`/`RealDeliverableSetDownloader` + `DeliverableDownloadWorker` (WorkManager, foreground progress notification, SHA-256 verify, atomic staging->promote, real on-disk delete). Payload at `filesDir/delivery/<set>/` (survives cache-clear).
- Adding a `DeliverableSet` member is mechanical but multi-file (~5 `when` sites: `DeliverableInventoryImpl`, `DeliverableDownloadWorker.featureNameRes/notificationId`, `RealDeliverableSetDownloader.isNativeCodeSet`, `FALLBACK_SIZE`).
- **noLegal / Play `.so` ban is irrelevant here:** `RealDeliverableSetDownloader.isNativeCodeSet()` gates only real `.so` payloads behind `isPlayInstall()`; image/data payloads download everywhere (same as the existing favicon atlas + `stream-catalog.zip`).

## Favicon atlas (closest precedent)

- Store: `data/repository/streams/FaviconAtlasStore.kt` - app-private sidecar `filesDir/streams/favicon-atlas.png` + `favicon-coords.json` (`url -> index` map), rewritten wholesale each import.
- Slicer: `ui/streams/FaviconAtlasSlicer.kt` - decode-once whole-bitmap cache, `rectFor(index)` = `col=index%COLS,row=index/COLS`, `TILE=32, COLS=16`. KDoc says decode-once is sized for a SMALL atlas; escalate to `BitmapRegionDecoder` if the full bitmap becomes a memory problem.
- Packer/app contract: `scripts/streams/collect-stream-candidates.ps1` (`Build-FaviconAtlas`, `$FaviconTile=32/$FaviconCols=16`) MUST match the slicer constants. Publish via `Invoke-PublishCatalog` (zips `streams.csv` entry-0 + `favicon-atlas.png`, `gh release upload` to permanent `delivery-so-v1` tag). S0925 guard refuses to publish a CSV with favicon rows but no bundled atlas.
- Adapter wiring: `StreamGridAdapter`/`StreamSourceAdapter` take `faviconResolver:(String)->Int?`, `faviconTileLoader:suspend(Int)->Bitmap?`, `faviconScope:CoroutineScope?` as plain constructor lambdas (not DI). Both already `@Suppress("LongParameterList")`.

## Frame cache / grid fallback chain

- `StreamGridAdapter.bind()` precedence: `frameProvider(url)` (captured live frame via `StreamFrameCache.get`) -> on miss `bindFavicon()` + (http/https VIDEO) `requestCapture(url)`.
- Capture: `StreamFrameSnapshotManager` (muted ExoPlayer + offscreen TextureView) -> `StreamFramePersistentStore` (on-disk JPEG per url, 150 MB budget) + `StreamFrameCache` (in-memory LRU 64) -> `repaintUrl` repaints one tile.
- Cold start: `StreamGridModeManager.prewarmPersistedFrames()` seeds the in-memory cache from disk.
- **Captured-frame-beats-fallback is the established precedent** for the captured idea's "own picture replaces atlas demo": the atlas-preview slot is a new tier between captured-frame and favicon in that same chain.

## Grid mode + filter

- `StreamGridModeManager.kt` owns list<->grid swap. `StreamsViewModel.onFilter()` (~l.274) and `onToggleDisplayMode()` (~l.299) are **fully independent today** - "video filter auto-switches to grid" is net-new logic with no precedent.

## Data / slot identity

- `StreamSourceEntity` (Room, unique index on `url`) has **no atlas/favicon-index column**. `id` is `UUID.randomUUID()` per import (unstable); `url` is the only stable key. Atlas slot map must be an external `url -> index` sidecar (as `FaviconAtlasStore` already is), not an entity column, unless a persistent column is added.

## Flavors / API

- `SUPPORT_STREAMS` true: standard, noLegal, legacy, vr. false: lite, photos. Atlas inherits the gate via `capabilityAvailability.isStreamsAvailable()`.
- No API-level gap above legacy's floor (`BitmapFactory`/`BitmapRegionDecoder`/WorkManager/DataStore all API 23+). `DeliverableDownloadWorker` already forks `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 29+.
- The "8192x8192 GPU texture" framing is a **packing/RAM-budget** notion only - the grid renders via `ImageView.setImageBitmap`, not GLES; no `GL_MAX_TEXTURE_SIZE` involvement unless a GL renderer is introduced (none exists in `src/main`).

## Post-import prompt hook

- `StreamsViewModel.StreamsEvent.CatalogUpdated` (emitted after a successful import) -> `StreamsActivity.showCatalogRefreshSuggestion()` (dismissible Snackbar with action) is the exact point to hook an "download the preview atlas?" prompt.

## Test coverage

- Only `DeliverableInventoryFilterTest` covers flavor-gating of the extensions list. No unit tests for `FaviconAtlasSlicer`/`FaviconAtlasStore`/`StreamGridAdapter`/`StreamGridModeManager`/`StreamFrameCache`/`StreamFrameSnapshotManager` - the subsystem this feature extends starts with near-zero regression protection.

## Document registry

- No `DOCUMENT_REGISTRY.jsonl` record covers the stream-catalog / favicon-atlas / Extensions-Manager docs today. A publishable third-party atlas resource (captured addendum) would be the FIRST candidate for a new registry entry - no existing convention to extend.

## Genuine owner/UX decisions (cannot be resolved from code)

- **Q-A (Rule 10 UX):** "video filter auto-switches to grid" semantics - override a user's manually chosen LIST mode? revert on switching away from video? only on first transition? No precedent to infer.
- **Q-B (scope + garbled capture):** third-party publishing/documentation of the atlas (captured addendum is a "побитое сообщение - уточнить у владельца"). In scope for iteration 1? Form: new `docs/` page vs `delivery/stream-catalog/README` addendum vs new registry entry?

## Resolvable-by-recommendation (forward bias; see strategic §5/§9)

- Download contract -> `DeliverableSet` pattern (real progress + real delete satisfy "скачать или удалить").
- Decode -> `BitmapRegionDecoder` per-tile (already used in `src/main`), not decode-once.
- Fallback precedence -> captured own frame > atlas preview > favicon > empty (captured idea states own-frame precedence explicitly).
- Slot key -> `url`-keyed external sidecar (mirror `FaviconAtlasStore`).

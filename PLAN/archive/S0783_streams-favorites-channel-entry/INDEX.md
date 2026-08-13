# S0783 - Tactical Plan: Streams in Favorites by channel name

**Ticket:** S0783
**Status:** BlockNeedUserTest
**Strategic spec:** `PLAN/S0783_streams-favorites-channel-entry.md`

## Outcome (2026-07-01)

All phases implemented. `standard debug` build PASS (`a.ps1 dq`, BUILD SUCCESSFUL 1m38s); debug tags compile-validated (`a.ps1 fk`, 23s). Room schema `38.json` generated. Ticket-log audit PASS. Status set to `BlockNeedUserTest`; two `Timber.d("S0783: ..")` probes in `FavoritesUseCase.toggleStreamFavorite` and `BrowseEventHandler.OpenStreamPlayer`. No online device at closure - device test deferred to `/spec-sweep`.

Owner decisions (from strategic §3.3):
- Store channels in the shared `favorites` table (unified list of files + channels).
- Offer "Add to favorites" everywhere "Pin" exists (streams catalog + main-window panel).
- Pin and favorite are independent.

Key anchors (verified in research):
- Room `AppDatabase` version = 37; recent migrations are standalone files (`Migration36To37.kt`). New = `Migration37To38.kt` / `MIGRATION_37_38`, bump to 38.
- Favorites materialize as virtual resource `SyntheticResourceIds.FAVORITES` (-100L) in `GetMediaFilesUseCase`; stream marker = `SyntheticResourceIds.STREAM` (-200L).
- Browse open funnel = `BrowseFileOpenManager.openFile` -> `BrowseEvent.NavigateToPlayer`.
- Channel launch by URL = `StreamsActivity.createPlayIntent(context, url)` -> `handlePlayIntent` -> `StreamsViewModel.playByUrl` (resolves mediaKind from `stream_sources`).
- Catalog channel menu = `PopupMenu` in `StreamSourceAdapter` (`onPin`/`onEdit`/..). Panel menu = `StreamsPanelMenuActions` + `StreamPanelChannelAdapter`.
- File-only consumers of `favorites`: `FavoritesWidgetService`/`FavoritesRemoteViewsFactory`, `ExportFavoritesUseCase`, Wear delta path.

---

## Phase 01 - Schema + migration (data)

- [ ] Extend `FavoritesEntity`: add `kind: String = "FILE"` (FILE|STREAM) and `streamMediaKind: String? = null`. For a channel row: `uri` = stream URL, `displayName` = channel title, `resourceId` = `SyntheticResourceIds.STREAM`, `mediaType` = VIDEO ordinal for VIDEO/RTSP else AUDIO ordinal, `size` = 0, `lastKnownPath` = URL, `dateModified` = addedAt.
- [ ] Bump `AppDatabase` `version` 37 -> 38.
- [ ] Create `Migration37To38.kt` with `val MIGRATION_37_38 = object : Migration(37, 38)`: `ALTER TABLE favorites ADD COLUMN kind TEXT NOT NULL DEFAULT 'FILE'` and `ALTER TABLE favorites ADD COLUMN streamMediaKind TEXT`.
- [ ] Register `MIGRATION_37_38` in the Room builder (`DatabaseModule`, `addMigrations(..)`).
- [ ] `FavoritesDao`: add file-only reads for consumer isolation - `getFileFavorites(): Flow<List<FavoritesEntity>>` and `getFileFavoritesSync(): List<FavoritesEntity>` (`WHERE kind = 'FILE'`). Keep `getAllFavorites()` (both kinds) for the favorites view.
- [ ] Verification: `.\a.ps1 fk` compiles; grep confirms `version = 38` and `MIGRATION_37_38` registered.

## Phase 02 - Toggle channel favorite (domain)

- [ ] Add channel favorite toggle keyed by URL. Extend `FavoritesRepository`/Impl if needed (reuse `addFavorite`/`removeFavorite(uri)`/`isFavoriteSync(uri)`).
- [ ] Add `FavoritesUseCase.toggleStreamFavorite(source: StreamSourceEntity)`: if `isFavoriteSync(source.url)` -> `removeFavorite(source.url)`; else build a STREAM `FavoritesEntity` (per Phase 01 mapping) and `addFavorite(..)`. Record `StatsEvent.Favorite(added=..)`.
- [ ] Expose channel favorite state for the UI: `FavoritesUseCase.observeFavoriteUrls(): Flow<Set<String>>` (map `getAllFavorites` to `uri` set) for per-row labels.
- [ ] Verification: `.\a.ps1 fk` compiles.

## Phase 03 - Materialize channel + open routing

- [ ] `GetMediaFilesUseCase` favorites branch (`resource.id == FAVORITES`): for `kind == "STREAM"` rows map to `MediaFile(path = uri, name = displayName, type = VIDEO|AUDIO by streamMediaKind, resourceId = SyntheticResourceIds.STREAM, isFavorite = true, width/height/duration = 0)`. Leave file rows as-is. Keep existing flavor filter + sort.
- [ ] Add `BrowseEvent.OpenStreamPlayer(url: String)`.
- [ ] `BrowseFileOpenManager.openFile`: at entry, if `file.resourceId == SyntheticResourceIds.STREAM` -> `inlineStop(); sendEvent(BrowseEvent.OpenStreamPlayer(file.path)); return` (before index lookup).
- [ ] Handle `OpenStreamPlayer` in the Browse host (fragment/activity that collects `BrowseEvent`): `startActivity(StreamsActivity.createPlayIntent(context, url))`.
- [ ] Verification: `.\a.ps1 fk` compiles; manual (device): tapping a favorited channel opens the stream player.

## Phase 04 - Action in both menus (UI)

- [ ] New strings (EN/RU/UK) via `scripts/utils/set-android-string.ps1 -Action add`: `streams_add_to_favorites`, `streams_remove_from_favorites`.
- [ ] `StreamSourceAdapter` popup: add favorite item (label add/remove from per-row favorite state) + `onToggleFavorite: (StreamSourceEntity) -> Unit`; show only when favorites enabled (pass a `favoritesEnabled` predicate/flag).
- [ ] `StreamsViewModel`: `toggleStreamFavorite(source)`; join `observeFavoriteUrls()` into channel list state so each row knows `isFavorite`.
- [ ] `StreamsActivity`: wire `onToggleFavorite`; gate on `settings.enableFavorites`.
- [ ] Panel path: add `onToggleFavorite` to `StreamsPanelMenuActions`; add the menu item in `StreamPanelChannelAdapter`; wire through `MainStreamsPanelManager`/`MainActivity` (host owns the toggle call + favorites-enabled gate).
- [ ] Verification: `.\a.ps1 fc` compiles (code + resources); manual: items appear next to Pin in both surfaces, hidden when favorites off.

## Phase 05 - Consumer isolation (file-only)

- [ ] Grep every `getAllFavorites`/`getAllFavoritesSync` usage. For file-only consumers switch to `getFileFavorites*`:
  - [ ] `FavoritesRemoteViewsFactory` (widget) -> `getFileFavoritesSync()`.
  - [ ] `ExportFavoritesUseCase` -> file favorites only.
  - [ ] Wear delta builder (`WearFavoritesDeltaPayload` producer) -> file favorites only.
- [ ] Leave the favorites view materialization on `getAllFavorites()` (needs both kinds).
- [ ] Verification: `.\a.ps1 fk` compiles; grep confirms no file-only consumer reads the unfiltered query.

## Phase 06 - Capability record + closure

- [ ] Record capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (EN-only). Do NOT edit `docs/FEATURES*.md` (release-owned).
- [ ] Insert `Timber.d("S0783: ..")` at changed-flow entries (toggle + favorites-open routing) as final edits before the last build (BlockNeedUserTest gate).
- [ ] Close via `scripts/post-change.ps1 -ScopeToFile` per touched file; catalog sync once; set status `BlockNeedUserTest` with a device-test note.
- [ ] Verification: `.\a.ps1 fc` + static gates PASS.

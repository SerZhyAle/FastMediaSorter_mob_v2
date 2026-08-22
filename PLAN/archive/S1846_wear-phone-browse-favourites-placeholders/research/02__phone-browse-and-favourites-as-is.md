# S1846 research 02 - AS-IS of the two placeholder screens

Date: 2026-08-20. Method: read of the wear and app_v2 sources named below; every claim carries `path:line`.
Written during `/spec-code` stage F2, before any phase was authored, because the finding refutes the
strategic spec's own planning premise.

## Verdict

The strategic spec assumed (§4, §5 before this artifact) that both screens are missing only a screen and
that the data layer is already there. That is true for **Favourites** in a limited sense and **false for the
five media-type chips**.

## 1. The phone-browse chips have no data path

- The watch-to-phone request carries no media type. `WearPhoneResourceRequest` declares
  `schemaVersion`, `requestId`, `kind`, `parentToken`, `pageToken`, `itemToken` and nothing else:
  `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPhoneResourcePayload.kt:21-28`.
  The `mediaType` argument the five chips already put on the route
  (`WearRoutes.browsePhone(mediaType)`, `WearRoutes.kt:82`) therefore has nowhere to go once it reaches
  the client.
- The phone side has no per-request filter either. `ROOT` returns every resource that passes
  `isExposedToWatch()` (`app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ListPhoneResourcePageUseCase.kt:35-42`);
  `CHILDREN` lists a folder with `supportedTypes = resource.supportedMediaTypes` - the resource's own
  configuration, not anything the request asked for (`ListPhoneResourcePageUseCase.kt:63-80`).
- Client-side filtering cannot substitute at the root level: a root wire item carries
  `token`, `name`, `mimeType`, `sizeBytes`, `isDirectory`, `thumbnailBase64`
  (`WearPhoneResourcePayload.kt:30-42`) and a directory's `mimeType` is always `null`
  (`ListPhoneResourcePageUseCase.kt:90`), so the watch cannot tell which resources hold images.
- Inside a folder, a client-side filter still cannot serve two of the five chips: everything that is not
  image, gif, video or audio is put on the wire as `mimeType = null`
  (`ListPhoneResourcePageUseCase.kt:184-189` `toWireMimeType()`), so `Documents` and an unsupported binary
  are indistinguishable.

**Consequence.** Showing phone media by type requires a wire-schema change (`WEAR_PHONE_RESOURCE_SCHEMA_VERSION`
is 2, `WearPhoneResourcePayload.kt:3`) plus a phone-side filter in `ListPhoneResourcePageUseCase` - both
inside S1697's transport, which S1846 declared out of its own scope.

## 2. A phone file cannot be opened from the watch at all

Tapping a file in the working `Phone` browser is a no-op by construction: both the chip and the row gate the
click on `entry.isDirectory` (`wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceScreen.kt:183,201`),
and `PhoneResourceViewModel` exposes only `openFolder` / `navigateUp` / `retry`
(`PhoneResourceViewModel.kt:93-107`) - `PhoneResourceClient.open()` has no call site.

**Consequence.** Even a correctly filtered media-type screen would be a list whose items do nothing, which is
the same dead end this ticket exists to remove, one level deeper. This is S1697's own recorded audit gap, not
a new defect.

## 3. Favourites can be listed, but not resolved back to a file

- The store keeps a flat `Set<String>` of `sourceId:filePath` keys in `EncryptedSharedPreferences`
  (`wear/src/main/java/com/sza/fastmediasorter/wear/data/repository/WearFavoritesRepositoryImpl.kt:36-54`).
  No name, no media type, no size, no thumbnail, no stable id.
- The repository interface offers no read-all: only `addFavorite`, `removeFavorite`, `isFavorite`,
  `hasAnyFavorite`, `getPendingDelta`, `clearPendingDelta`
  (`wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearFavoritesRepository.kt:5-14`).
  Adding one is trivial; the stored value is the problem, not the accessor.
- `sourceId` is written with two different meanings by the only two writers: `ImageViewerViewModel` hardcodes
  `"local"` / `"network"` (`ImageViewerViewModel.kt:247-264`), while `AudioPlayerViewModel` uses
  `selected.file.uri.host ?: "network"` (`AudioPlayerViewModel.kt:460-467`). A generic
  "favourite -> open the right thing" path cannot key off a field that means a category in one writer and a
  host name in the other.
- Nothing resolves a stored `filePath` back to an openable item. `SelectedMediaManager` is a single-slot
  hand-off whose `getSelectedFileById` matches only the currently selected file
  (`SelectedMediaManager.kt:22,56-69`), local lookup needs a MediaStore `Long` id
  (`WearMediaRepository.getMediaFileById`), and network files carry session-scoped synthetic ids assigned at
  browse time (`BrowseViewModel.kt:195`).

**Consequence.** A favourites screen that only lists last-path-segment text is buildable today. A favourites
screen whose rows open a player is not, without changing what the store keeps and normalising `sourceId`.

## 4. What is genuinely reusable

- `WearScreenScaffold` + `ScalingLazyColumn` + `PositionIndicator` + `wearScreenInsets()` - the shape every
  existing watch screen uses (`HomeScreen.kt:65-101`, `PhoneResourceScreen.kt:73-142`, `BrowseScreen.kt:91-133`).
- `fileListViewMode` + `GridColumnFit.columnsFor(..)` + `ThumbnailCell` - the list/grid duality S1730 built
  precisely so a third file list would not need a third implementation
  (`BrowseViewModel.kt:74-75`, `MediaFileGrid.kt:53-79`, `ThumbnailCell.kt:41-75`).
- `ToggleFavoriteUseCase.toggle(sourceId, filePath, wasFavorite)` - the single call site for unmarking
  (`ToggleFavoriteUseCase.kt:19-27`); `AudioPlayerViewModel` uses it, `ImageViewerViewModel` bypasses it.

## 5. Side finding, parked separately

`BrowseScreen.kt:242` hardcodes the label `"Retry"` while every neighbouring screen resolves a string
resource (`PhoneResourceScreen.kt:263`). Parked as its own ticket - it is a locale-parity change under Rule 30,
not a one-liner.

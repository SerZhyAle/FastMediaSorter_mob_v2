# IV.1 — Decompose Giant Files

**Target:** every `.kt` file ≤ 700 LOC (hard cap: ≤ 1000 per CLAUDE.md). **Importance weights:** Activity/Fragment = 5, ViewModel = 4, Manager/Helper/Handler/Adapter = 3, Data Client/Repository/UseCase/Strategy = 2. **Score** = LOC × weight.

**Last measured:** 2026-04-23

**Loop status:** running automated decomposition loop — pick the largest file by score, extract helpers until it is ≤ 1000 LOC, build standard debug, fix compile errors, commit, then advance to the next file.

---

## Completed (Waves 1–3)

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `ui/addresource/AddResourceActivity.kt` | 2 074 | 405 | −1 669 | `AddResourceConnectionManager`, `AddResourceScanManager`, `AddResourceFormManager` |
| `ui/addresource/AddResourceViewModel.kt` | 1 827 | 554 | −1 273 | `AddResourceSmbCoordinator`, `AddResourceSftpFtpCoordinator`, `AddResourceSftpKeyCoordinator`, `AddResourceVirtualCoordinator`, `AddResourceNetworkScanCoordinator`, `AddResourceBridge`, `AddResourceFinalizer` |
| `ui/settings/fragments/GeneralSettingsFragment.kt` | 2 358 | 209 | −2 149 | `GeneralSettings{Sections,Reset,Log,Permissions,ImportExport,Credential,Cache,Backup,Observers,ViewSetup,Prefetch}Helper` |
| `ui/player/ImageLoadingManager.kt` | 2 241 | 1 305 | −936 | `AudioInfoDisplayHelper`, `ImagePreloadHelper`, `AudioCoverArtLoader` (still ≥ 700 — see Wave 4) |
| `ui/browse/MediaFileAdapter.kt` | partial | 1 095 | — | `AdapterThumbnailLoader`, `AdapterFileInfoFormatter`, `AdapterDragController`, `InlinePlaybackAnimator`, `MediaFileDiffCallback` (still ≥ 700 — see Wave 4) |

`PlayerViewModel.kt` has already absorbed 3 coordinators (`PlayerStereoModeCoordinator`, `PlayerDeleteUndoCoordinator`, `PlayerPrefetchOffloadCoordinator`), but remains at 1 321 LOC — see Wave 4.

**Wave 4 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `ui/player/PlayerViewModel.kt` | 1 321 | 688 | −633 | `PlayerMediaFilesLoader` (4.1), `PlayerNavigationCoordinator` (4.2) |

**Wave 5 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `ui/main/MainActivity.kt` | 1 330 | 887 | −443 | `MainResumePlaybackHelper`, `MainResourceTabsManager`, `MainStoragePermissionsHelper`, `MainLayoutChromeManager` |

`MainActivity.kt` is now under the 1000-line hard cap. The 700-line stretch target is not yet hit; remaining content is the still-cohesive setupViews/observeData wiring plus error/info dialogs and intent action dispatch. Further reduction (e.g., extracting an `onCreate` intent-action router) is optional polish.

**Wave 7 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `ui/resourceeditor/ResourceEditorFragment.kt` | 1 057 | 978 | −79 | `ResourceEditorOutcomeRenderer` (statistics, connection-result, save button, error message, loading) |

`ResourceEditorFragment.kt` is now under the 1000-line hard cap.

**Wave 8 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `ui/player/helpers/PlayerMediaLoaderManager.kt` | 1 002 | 967 | −35 | `PlayerMediaViewVisibilityHelper` (per-viewer hide methods + path-scheme → ResourceType) |

`PlayerMediaLoaderManager.kt` is now under the 1000-line hard cap.

**Wave 9 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `ui/player/helpers/TranslationManager.kt` | 1 011 | 961 | −50 | `TranslationTextUtils` (OCR text cleanup + ML Kit language-code labelling) |

`TranslationManager.kt` is now under the 1000-line hard cap.

---

## Current sizes (files ≥ 700 LOC)

| # | File | LOC | Target | Weight | Score |
| --- | ---- | ---: | :----: | :----: | ---: |
| 1 | `app_v2/ui/player/helpers/EpubViewerManager.kt` | 2 191 | ≤ 700 | 3 | 6 573 |
| 2 | `app_v2/ui/player/PlayerViewModel.kt` | 1 321 | ≤ 700 | 4 | 5 284 |
| 3 | `app_v2/ui/player/helpers/TextViewerManager.kt` | 1 823 | ≤ 700 | 3 | 5 469 |
| 4 | `app_v2/ui/player/helpers/PdfViewerManager.kt` | 1 640 | ≤ 700 | 3 | 4 920 |
| 5 | `app_v2/data/remote/ftp/FtpClient.kt` | 1 603 | ≤ 700 | 2 | 3 206 |
| 6 | `app_v2/data/cloud/GoogleDriveRestClient.kt` | 1 452 | ≤ 700 | 2 | 2 904 |
| 7 | `app_v2/data/cloud/OneDriveRestClient.kt` | 1 433 | ≤ 700 | 2 | 2 866 |
| 8 | `app_v2/ui/main/MainActivity.kt` | 1 330 | ≤ 700 | 5 | 6 650 |
| 9 | `app_v2/data/remote/sftp/SftpClient.kt` | 1 311 | ≤ 700 | 2 | 2 622 |
| 10 | `app_v2/ui/player/ImageLoadingManager.kt` | 1 304 | ≤ 700 | 3 | 3 912 |
| 11 | `app_v2/data/network/SmbClient.kt` | 1 291 | ≤ 700 | 2 | 2 582 |
| 12 | `app_v2/data/cloud/CloudFileOperationHandler.kt` | 1 222 | ≤ 700 | 3 | 3 666 |
| 13 | `app_v2/data/cloud/DropboxClient.kt` | 1 181 | ≤ 700 | 2 | 2 362 |
| 14 | `app_v2/ui/player/StandalonePlayerActivity.kt` | 1 129 | ≤ 700 | 5 | 5 645 |
| 15 | `app_v2/ui/browse/MediaFileAdapter.kt` | 1 095 | ≤ 700 | 3 | 3 285 |
| 16 | `app_v2/data/network/SmbConnectionManager.kt` | 1 079 | ≤ 700 | 3 | 3 237 |
| 17 | `app_v2/ui/resourceeditor/ResourceEditorFragment.kt` | 1 057 | ≤ 700 | 5 | 5 285 |
| 18 | `app_v2/ui/player/helpers/TranslationManager.kt` | 1 011 | ≤ 700 | 3 | 3 033 |
| 19 | `app_v2/ui/player/helpers/PlayerMediaLoaderManager.kt` | 1 002 | ≤ 700 | 3 | 3 006 |
| 20 | `app_v2/ui/player/CommandPanelController.kt` | 986 | ≤ 700 | 3 | 2 958 |
| 21 | `app_v2/ui/dialog/FileInfoDialog.kt` | 946 | ≤ 700 | 3 | 2 838 |
| 22 | `app_v2/ui/browse/managers/BrowseFileOperationsManager.kt` | 941 | ≤ 700 | 3 | 2 823 |
| 23 | `app_v2/data/transfer/BaseFileOperationHandler.kt` | 939 | ≤ 700 | 3 | 2 817 |
| 24 | `app_v2/data/network/FtpFileOperationHandler.kt` | 938 | ≤ 700 | 3 | 2 814 |
| 25 | `app_v2/data/transfer/strategy/SmbOperationStrategy.kt` | 822 | ≤ 700 | 2 | 1 644 |
| 26 | `app_v2/domain/usecase/SearchLyricsUseCase.kt` | 804 | ≤ 700 | 2 | 1 608 |
| 27 | `app_v2/ui/browse/PagingMediaFileAdapter.kt` | 802 | ≤ 700 | 3 | 2 406 |
| 28 | `app_v2/data/repository/SettingsRepositoryImpl.kt` | 802 | ≤ 700 | 2 | 1 604 |
| 29 | `app_v2/ui/browse/managers/BrowseManagerInitializer.kt` | 795 | ≤ 700 | 3 | 2 385 |
| 30 | `app_v2/data/local/LocalMediaScanner.kt` | 785 | ≤ 700 | 2 | 1 570 |
| 31 | `app_v2/ui/player/VideoPlayerManager.kt` | 770 | ≤ 700 | 3 | 2 310 |
| 32 | `app_v2/ui/player/PlayerManagerInitializer.kt` | 760 | ≤ 700 | 3 | 2 280 |
| 33 | `app_v2/data/network/SmbMediaScanner.kt` | 746 | ≤ 700 | 2 | 1 492 |
| 34 | `app_v2/data/transfer/strategy/CloudOperationStrategy.kt` | 740 | ≤ 700 | 2 | 1 480 |
| 35 | `app_v2/domain/usecase/SmbOperationsUseCase.kt` | 738 | ≤ 700 | 2 | 1 476 |
| 36 | `app_v2/ui/main/ResourceAdapter.kt` | 733 | ≤ 700 | 3 | 2 199 |
| 37 | `app_v2/ui/browse/managers/BrowseDialogHelper.kt` | 733 | ≤ 700 | 3 | 2 199 |
| 38 | `app_v2/domain/usecase/ResourceEditorUseCase.kt` | 730 | ≤ 700 | 2 | 1 460 |
| 39 | `app_v2/ui/player/helpers/TouchZoneGestureManager.kt` | 719 | ≤ 700 | 3 | 2 157 |
| 40 | `app_v2/data/network/glide/NetworkFileModelLoader.kt` | 719 | ≤ 700 | 2 | 1 438 |
| 41 | `app_v2/data/transfer/strategy/SftpOperationStrategy.kt` | 711 | ≤ 700 | 2 | 1 422 |
| 42 | `app_v2/ui/player/PlayerActivity.kt` | 708 | ≤ 700 | 5 | 3 540 |
| 43 | `app_v2/data/transfer/strategy/FtpOperationStrategy.kt` | 707 | ≤ 700 | 2 | 1 414 |

---

## Priority list (by score ↓)

| Rank | File | LOC | Score |
| ---: | ---- | ---: | ---: |
| 1 | `PlayerViewModel.kt` | 1 321 | 5 284 |
| 2 | `MainActivity.kt` | 1 330 | 6 650 |
| 3 | `EpubViewerManager.kt` | 2 191 | 6 573 |
| 4 | `StandalonePlayerActivity.kt` | 1 129 | 5 645 |
| 5 | `TextViewerManager.kt` | 1 823 | 5 469 |
| 6 | `ResourceEditorFragment.kt` | 1 057 | 5 285 |
| 7 | `PdfViewerManager.kt` | 1 640 | 4 920 |
| 8 | `ImageLoadingManager.kt` | 1 304 | 3 912 |
| 9 | `CloudFileOperationHandler.kt` | 1 222 | 3 666 |
| 10 | `PlayerActivity.kt` | 708 | 3 540 |
| 11 | `MediaFileAdapter.kt` | 1 095 | 3 285 |
| 12 | `SmbConnectionManager.kt` | 1 079 | 3 237 |
| 13 | `FtpClient.kt` | 1 603 | 3 206 |
| 14 | `TranslationManager.kt` | 1 011 | 3 033 |
| 15 | `PlayerMediaLoaderManager.kt` | 1 002 | 3 006 |
| 16 | `CommandPanelController.kt` | 986 | 2 958 |
| 17 | `GoogleDriveRestClient.kt` | 1 452 | 2 904 |
| 18 | `OneDriveRestClient.kt` | 1 433 | 2 866 |
| 19 | `FileInfoDialog.kt` | 946 | 2 838 |
| 20 | `BrowseFileOperationsManager.kt` | 941 | 2 823 |
| 21 | `BaseFileOperationHandler.kt` | 939 | 2 817 |
| 22 | `FtpFileOperationHandler.kt` | 938 | 2 814 |
| 23 | `SftpClient.kt` | 1 311 | 2 622 |
| 24 | `SmbClient.kt` | 1 291 | 2 582 |
| 25 | `PagingMediaFileAdapter.kt` | 802 | 2 406 |
| 26 | `BrowseManagerInitializer.kt` | 795 | 2 385 |
| 27 | `DropboxClient.kt` | 1 181 | 2 362 |
| 28 | `VideoPlayerManager.kt` | 770 | 2 310 |
| 29 | `PlayerManagerInitializer.kt` | 760 | 2 280 |
| 30 | `ResourceAdapter.kt` | 733 | 2 199 |
| 31 | `BrowseDialogHelper.kt` | 733 | 2 199 |
| 32 | `TouchZoneGestureManager.kt` | 719 | 2 157 |
| 33 | `SmbOperationStrategy.kt` | 822 | 1 644 |
| 34 | `SearchLyricsUseCase.kt` | 804 | 1 608 |
| 35 | `SettingsRepositoryImpl.kt` | 802 | 1 604 |
| 36 | `LocalMediaScanner.kt` | 785 | 1 570 |
| 37 | `SmbMediaScanner.kt` | 746 | 1 492 |
| 38 | `CloudOperationStrategy.kt` | 740 | 1 480 |
| 39 | `SmbOperationsUseCase.kt` | 738 | 1 476 |
| 40 | `ResourceEditorUseCase.kt` | 730 | 1 460 |
| 41 | `NetworkFileModelLoader.kt` | 719 | 1 438 |
| 42 | `SftpOperationStrategy.kt` | 711 | 1 422 |
| 43 | `FtpOperationActionStrategy.kt` | 707 | 1 414 |

---

## Wave 4 — in progress

**Target:** `ui/player/PlayerViewModel.kt` (1 321 LOC → ≤ 700).

Already delegated to coordinators:

- Stereo / 3D detection → `PlayerStereoModeCoordinator`
- Delete + undo → `PlayerDeleteUndoCoordinator`
- Prefetch + offload → `PlayerPrefetchOffloadCoordinator`

Remaining extractions (in planned order, biggest chunks first):

| Step | Target block | Approx. LOC | Destination | Status |
| ---: | --- | ---: | --- | --- |
| 4.1 | `loadMediaFiles` + `loadSettings` + `reloadFiles` + `cancelLoading` + `normalizePath` + `isPlayerBrowsableFile` | ~360 | `PlayerMediaFilesLoader` | ✅ done (VM 1 321 → 966) |
| 4.2 | `nextFile`, `previousFile`, `jumpToIndex`, `syncAudioServiceIndex`, `getLookaheadTargets`, `getAdjacentFiles`, `getNextAudioFile`, `saveLastViewedFile(Debounced)` | ~280 | `PlayerNavigationCoordinator` | ✅ done (VM 966 → 688 — **target ≤ 700 reached**) |
| 4.3 | File-list mutations: `onFileMoved`, `removeMovedFile`, `removeDeletedFile`, `removeFileFromList`, `refreshCurrentFileInfo` | ~150 | `PlayerFileListMutator` | optional — further cleanup |
| 4.4 | Slideshow + controls/panel/fullscreen toggles | ~130 | `PlayerSlideshowPanelCoordinator` | optional — further cleanup |
| 4.5 | Resume state + last-viewed debounce | ~75 | `PlayerResumeCoordinator` | already covered by 4.2 |

`PlayerViewModel.kt` is now **688 LOC**, below the 700 target. Steps 4.3–4.4 are optional polish and can be picked up later; the file is no longer on the spec's priority list after Wave 4.2.

---

## Wave 6 — deferred (giant document viewers)

Top remaining score after Wave 5 was `EpubViewerManager.kt` (2 191 LOC, score 6 573). Inspection showed the rendering, gesture, translation, and search systems are deeply intertwined — splitting it requires a careful multi-step plan (`EpubChapterRenderer`, `EpubTranslationOverlayHelper`, `EpubSearchAndTocPresenter`, `EpubGestureRouter`, `EpubReaderSettingsHelper`) and extensive E2E verification. Same applies to `TextViewerManager.kt` (1 823) and `PdfViewerManager.kt` (1 640).

To keep the loop productive, the dynamic loop now picks the next file by **(LOC − 700) × weight, smallest delta first**, attacking the easier wins under 1500 LOC before circling back to the giant document viewers in dedicated future waves.

## Loop status

Already brought under the 1000 LOC hard cap (waves 4, 5, 7):

- `ui/player/PlayerViewModel.kt` (688)
- `ui/main/MainActivity.kt` (887)
- `ui/resourceeditor/ResourceEditorFragment.kt` (978)

Next dynamic-loop candidates (sub-1500 LOC, easy quick wins toward the 1000 cap):

| File | LOC | Margin |
| --- | ---: | ---: |
| `ui/player/StandalonePlayerActivity.kt` | 1 129 | 130 |
| `ui/player/ImageLoadingManager.kt` | 1 304 | 305 |
| `ui/main/MediaFileAdapter.kt` | 1 095 | 96 |
| `data/network/SmbConnectionManager.kt` | 1 079 | 80 |
| `data/cloud/CloudFileOperationHandler.kt` | 1 222 | 223 |
| `data/cloud/DropboxClient.kt` | 1 181 | 182 |
| `ui/player/helpers/TranslationManager.kt` | 1 011 | 12 |
| `ui/player/helpers/PlayerMediaLoaderManager.kt` | 1 002 | 3 |
| `ui/player/CommandPanelController.kt` | 986 | within cap |

The loop continues from the smallest-margin file each iteration.

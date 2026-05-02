# IV.1 — Decompose Giant Files

**Status:** Approved
**Priority:** 50
**Tactical plan:** _none — strategic-only loop tracker_

**Target:** every `.kt` file ≤ 700 LOC (hard cap: ≤ 1000 per CLAUDE.md). **Importance weights:** Activity/Fragment = 5, ViewModel = 4, Manager/Helper/Handler/Adapter = 3, Data Client/Repository/UseCase/Strategy = 2. **Score** = LOC × weight.

**Last measured:** 2026-04-24

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

**Wave 10 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/network/SmbConnectionManager.kt` | 1 099 | 1 000 | −99 | `SmbErrorClassifier` (retriable/non-retriable bucketing, transport/broken-pipe detection, user-facing message mapping, fast TCP pre-check) |

`SmbConnectionManager.kt` is now exactly at the 1000-line hard cap. Pool/lifecycle/health logic stays in the manager.

**Wave 11 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/cloud/DropboxClient.kt` | 1 181 | 981 | −200 | `DropboxClientUtils` (user-friendly error messages, TLS diagnostics logging, credential JSON serialization, Metadata → CloudFile mapping, MIME guessing, retry wrapper) |

`DropboxClient.kt` is now under the 1000-line hard cap.

**Wave 12 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `ui/player/StandalonePlayerActivity.kt` | 1 129 | 845 | −284 | `StandaloneFileOperationsHandler` (delete: file/SAF/MediaStore R+/Q-recoverable; share via FileProvider; Open-in-FMS reverse routing; SAF+MediaStore rename) |

`StandalonePlayerActivity.kt` is now under the 1000-line hard cap.

**Wave 13 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/cloud/CloudFileOperationHandler.kt` | 1 222 | 997 | −225 | `CloudFileOperationPathUtils` (path normalization, scheme→ResourceType, SFTP/FTP remote-path stripping, MIME guessing) + `CloudToCloudTransferHelper` (delete, native + cross-provider copy via temp file, native move with copy+delete fallback) |

`CloudFileOperationHandler.kt` is now under the 1000-line hard cap.

**Wave 14 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/network/SmbClient.kt` | 1 291 | 954 | −337 | `SmbClientErrorFormatter` (user-friendly error mapping, diagnostic message builder, race-tolerant ensureSmbDirectoryExists) + `SmbShareDiscoveryHelper` (trial-based listShares + performTestConnection that drives the share/path summary UI) |

`SmbClient.kt` is now under the 1000-line hard cap.

**Wave 15 result (partial — needs follow-up):**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/remote/sftp/SftpClient.kt` | 1 311 | 1 155 | −156 | `SftpConnectionTester` (stateless password + private-key connect tests, recursive `mkdir -p`); also collapsed single-line KDoc to comments |

**Still over the 1000 cap (1 155 LOC).** Channel-pool methods (`getOrCreateConnection`, `getOrCreateChannel`, `removeChannel`, `invalidateConnection`, `cleanupIdleConnections`, `getConnectionForExoPlayer`, `releaseExoPlayerConnection`) share deep state (connectionPool, semaphore, exoPlayerPoolLock, PooledConnection, ConnectionKey) and need a dedicated `SftpConnectionPool` extraction in a follow-up wave.

**Wave 20 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/remote/sftp/SftpClient.kt` | 1 155 | 627 | −528 | `SftpConnectionPool` (channel-pool state + getOrCreate/removeChannel/invalidate/cleanupIdle, suspending withConnection, BLOCKING ExoPlayer get/release with the dedicated TOCTOU lock, openInputStream that hot-recreates session on JSchException, plus a public `invalidate(info)` entry for client-driven retry) |

`SftpClient.kt` is now well under the 1000-line hard cap.

**Wave 16 result (partial — needs follow-up):**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/cloud/OneDriveRestClient.kt` | 1 433 | 1 349 | −84 | `OneDriveRestClientUtils` (MSAL account JSON serialize/deserialize, `cloud://onedrive/` reference normalization, Graph DriveItem JSON → CloudFile mapping, ApiResponse envelope) |

**Still over the 1000 cap (1 349 LOC).** Major remaining surface: MSAL auth flow (`signInInternal`, `acquireTokenSilently`, `handleAuthenticationResult`) and `makeAuthenticatedRequest` with 401 silent-refresh recursion — all share deep state (msalApp, accessToken, tokenTimestamp). Needs a dedicated `OneDriveAuthCoordinator` + `OneDriveHttpClient` extraction in a follow-up wave.

**Wave 16 follow-up (full, after partial 16a):**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/cloud/OneDriveRestClient.kt` | 1 349 | 896 | −453 | `OneDriveAuthCoordinator` (owns msalApp + accessToken + accountEmail + tokenTimestamp; initializeMsal / authenticate / signIn / signInInternal / acquireTokenSilently with MsalDeclinedScopeException recovery / handleAuthenticationResult / ensureTokenFresh / makeAuthenticatedRequest with 401 silent-refresh recursion / signOutLocal / initializeFromStored) |

`OneDriveRestClient.kt` is now under the 1000-line hard cap. Client retains every CloudStorageClient surface and Graph endpoint shaping; auth state and HTTP plumbing live in the coordinator.

**Wave 17 result (partial — needs follow-up):**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `ui/player/ImageLoadingManager.kt` | 1 304 | 1 239 | −65 | `ImageLoadingDiagnostics` (non-critical Glide network-image error classification + heap/native/preload-job memory snapshot logger); also collapsed single-line KDoc to comments |

**Still over the 1000 cap (1 239 LOC).** Major remaining surface: `loadCloudImage`/`loadNetworkImage`/`loadLocalImage` (~340 LOC combined) and `createGlideListener`/`createGifGlideListener` (~170 LOC) — all share deep state (animatedImageController, callback, loading-indicator handler, currentTargetView, currentCropSetting, dynamicBackgroundProcessor, etc.). Needs `ImageLoadingPipeline` extraction in a follow-up wave (extract Glide listener factory behind a callback interface, then route Cloud/Network/Local builders through it).

**Wave 18 result (partial — needs follow-up):**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/cloud/GoogleDriveRestClient.kt` | 1 452 | 1 386 | −66 | `GoogleDriveRestClientUtils` (Drive `files.list` JSON → CloudFile mapping with RFC 3339 modifiedTime parsing); also collapsed single-line KDoc to comments |

**Still over the 1000 cap (1 386 LOC).** Auth flow (`silentSignIn`, `getAccessToken`, `handleSignInResult`, `ensureTokenFresh`, etc.) + `makeAuthenticatedRequest` 401-retry recursion need `GoogleDriveAuthCoordinator` extraction in a follow-up wave (mirrors the OneDrive pattern).

**Wave 21 result (partial):**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/cloud/GoogleDriveRestClient.kt` | 1 452 | 1 102 | −350 | `GoogleDriveAuthCoordinator` (owns accessToken + accountEmail + tokenTimestamp; buildSignInOptions / authenticate / silentSignIn / handleSignInResult / getAccessToken / ensureTokenFresh / makeAuthenticatedRequest with 401 silent-refresh recursion / initializeFromStored / clearAuth) |

**Still over the 1000 cap (1 102 LOC).** Remaining surface is the long tail of CloudStorageClient methods (listFiles/listFolders/getFileMetadata/downloadFile/uploadFile/createFolder/deleteFile/renameFile/moveFile/copyFile/fileExists/searchFiles/findFolderByName/ensureFolderExists/getThumbnail/signOut/getFileInputStreamInternal). Each follows the same `withContext + token-guard + URL + makeAuthenticatedRequest + JSON parse` pattern — a `GoogleDriveOperations` extraction would route them through the coordinator and bring the file under the cap. Deferred.

**Wave 19 result:**

| File | Before | After | Δ | Helpers introduced |
| ---- | ---: | ---: | ---: | --- |
| `data/remote/ftp/FtpClient.kt` | 1 603 | 905 | −698 | `FtpStandaloneOperations` (stateless test/upload/delete/rename/createDirectory/exists/readFileBytes/downloadFile/openInputStream/ensureRemoteDirectoryExists), `FtpDirectoryScanner` (single-level, recursive, recursive-paged with passive→active mode fallback), `FtpExoPlayerPool` (per-DataSource client + semaphore-capped concurrency + idle pool sweep, plus the FtpConnectionInfo / ExoPlayerFtpConnection data classes); also collapsed single-line KDoc to comments |

`FtpClient.kt` is now under the 1000-line hard cap. External call sites in `FtpDataSource` and `NetworkMediaDataSource` were updated to use `FtpExoPlayerPool.FtpConnectionInfo` / `FtpExoPlayerPool.ExoPlayerFtpConnection` instead of the old nested types.

---

## Current sizes (files ≥ 700 LOC)

| # | File | LOC | Target | Weight | Score |
| --- | ---- | ---: | :----: | :----: | ---: |
| 1 | `app_v2/ui/player/helpers/EpubViewerManager.kt` | 2 191 | ≤ 700 | 3 | 6 573 |
| 2 | `app_v2/ui/player/helpers/TextViewerManager.kt` | 1 823 | ≤ 700 | 3 | 5 469 |
| 3 | `app_v2/ui/player/helpers/PdfViewerManager.kt` | 1 640 | ≤ 700 | 3 | 4 920 |
| 4 | `app_v2/ui/player/ImageLoadingManager.kt` | 1 239 | ≤ 700 | 3 | 3 717 |
| 5 | `app_v2/data/cloud/GoogleDriveRestClient.kt` | 1 102 | ≤ 700 | 2 | 2 204 |
| 6 | `app_v2/ui/browse/MediaFileAdapter.kt` | 1 095 | ≤ 700 | 3 | 3 285 |
| 7 | `app_v2/ui/player/CommandPanelController.kt` | 1 005 | ≤ 700 | 3 | 3 015 |
| 8 | `app_v2/data/network/SmbConnectionManager.kt` | 1 000 | ≤ 700 | 3 | 3 000 |
| 9 | `app_v2/data/cloud/CloudFileOperationHandler.kt` | 997 | ≤ 700 | 3 | 2 991 |
| 10 | `app_v2/data/cloud/DropboxClient.kt` | 981 | ≤ 700 | 2 | 1 962 |
| 11 | `app_v2/ui/resourceeditor/ResourceEditorFragment.kt` | 978 | ≤ 700 | 5 | 4 890 |
| 12 | `app_v2/ui/player/helpers/PlayerMediaLoaderManager.kt` | 967 | ≤ 700 | 3 | 2 901 |
| 13 | `app_v2/ui/player/helpers/TranslationManager.kt` | 961 | ≤ 700 | 3 | 2 883 |
| 14 | `app_v2/data/network/SmbClient.kt` | 954 | ≤ 700 | 2 | 1 908 |
| 15 | `app_v2/ui/dialog/FileInfoDialog.kt` | 946 | ≤ 700 | 3 | 2 838 |
| 16 | `app_v2/ui/browse/managers/BrowseFileOperationsManager.kt` | 941 | ≤ 700 | 3 | 2 823 |
| 17 | `app_v2/data/transfer/BaseFileOperationHandler.kt` | 939 | ≤ 700 | 3 | 2 817 |
| 18 | `app_v2/data/network/FtpFileOperationHandler.kt` | 938 | ≤ 700 | 3 | 2 814 |
| 19 | `app_v2/ui/player/StandalonePlayerActivity.kt` | 933 | ≤ 700 | 5 | 4 665 |
| 20 | `app_v2/ui/main/MainActivity.kt` | 928 | ≤ 700 | 5 | 4 640 |
| 21 | `app_v2/data/remote/ftp/FtpClient.kt` | 906 | ≤ 700 | 2 | 1 812 |
| 22 | `app_v2/data/cloud/OneDriveRestClient.kt` | 896 | ≤ 700 | 2 | 1 792 |
| 23 | `app_v2/data/transfer/strategy/SmbOperationStrategy.kt` | 822 | ≤ 700 | 2 | 1 644 |
| 24 | `app_v2/ui/player/PlayerActivity.kt` | 816 | ≤ 700 | 5 | 4 080 |
| 25 | `app_v2/domain/usecase/SearchLyricsUseCase.kt` | 804 | ≤ 700 | 2 | 1 608 |
| 26 | `app_v2/ui/browse/PagingMediaFileAdapter.kt` | 802 | ≤ 700 | 3 | 2 406 |
| 27 | `app_v2/data/repository/SettingsRepositoryImpl.kt` | 802 | ≤ 700 | 2 | 1 604 |
| 28 | `app_v2/ui/player/VideoPlayerManager.kt` | 801 | ≤ 700 | 3 | 2 403 |
| 29 | `app_v2/ui/browse/managers/BrowseManagerInitializer.kt` | 795 | ≤ 700 | 3 | 2 385 |
| 30 | `app_v2/data/local/LocalMediaScanner.kt` | 786 | ≤ 700 | 2 | 1 572 |
| 31 | `app_v2/data/network/SmbMediaScanner.kt` | 746 | ≤ 700 | 2 | 1 492 |
| 32 | `app_v2/data/transfer/strategy/CloudOperationStrategy.kt` | 740 | ≤ 700 | 2 | 1 480 |
| 33 | `app_v2/domain/usecase/SmbOperationsUseCase.kt` | 738 | ≤ 700 | 2 | 1 476 |
| 34 | `app_v2/ui/main/ResourceAdapter.kt` | 733 | ≤ 700 | 3 | 2 199 |
| 35 | `app_v2/ui/browse/managers/BrowseDialogHelper.kt` | 733 | ≤ 700 | 3 | 2 199 |
| 36 | `app_v2/domain/usecase/ResourceEditorUseCase.kt` | 731 | ≤ 700 | 2 | 1 462 |
| 37 | `app_v2/ui/player/helpers/TouchZoneGestureManager.kt` | 719 | ≤ 700 | 3 | 2 157 |
| 38 | `app_v2/data/network/glide/NetworkFileModelLoader.kt` | 719 | ≤ 700 | 2 | 1 438 |
| 39 | `app_v2/data/transfer/strategy/SftpOperationStrategy.kt` | 711 | ≤ 700 | 2 | 1 422 |
| 40 | `app_v2/data/transfer/strategy/FtpOperationStrategy.kt` | 707 | ≤ 700 | 2 | 1 414 |

---

## Priority list (by score ↓)

| Rank | File | LOC | Score |
| ---: | ---- | ---: | ---: |
| 1 | `EpubViewerManager.kt` | 2 191 | 6 573 |
| 2 | `TextViewerManager.kt` | 1 823 | 5 469 |
| 3 | `PdfViewerManager.kt` | 1 640 | 4 920 |
| 4 | `ResourceEditorFragment.kt` | 978 | 4 890 |
| 5 | `StandalonePlayerActivity.kt` | 933 | 4 665 |
| 6 | `MainActivity.kt` | 928 | 4 640 |
| 7 | `PlayerActivity.kt` | 816 | 4 080 |
| 8 | `ImageLoadingManager.kt` | 1 239 | 3 717 |
| 9 | `MediaFileAdapter.kt` | 1 095 | 3 285 |
| 10 | `CommandPanelController.kt` | 1 005 | 3 015 |
| 11 | `SmbConnectionManager.kt` | 1 000 | 3 000 |
| 12 | `CloudFileOperationHandler.kt` | 997 | 2 991 |
| 13 | `PlayerMediaLoaderManager.kt` | 967 | 2 901 |
| 14 | `TranslationManager.kt` | 961 | 2 883 |
| 15 | `FileInfoDialog.kt` | 946 | 2 838 |
| 16 | `BrowseFileOperationsManager.kt` | 941 | 2 823 |
| 17 | `BaseFileOperationHandler.kt` | 939 | 2 817 |
| 18 | `FtpFileOperationHandler.kt` | 938 | 2 814 |
| 19 | `PagingMediaFileAdapter.kt` | 802 | 2 406 |
| 20 | `VideoPlayerManager.kt` | 801 | 2 403 |
| 21 | `BrowseManagerInitializer.kt` | 795 | 2 385 |
| 22 | `GoogleDriveRestClient.kt` | 1 102 | 2 204 |
| 23 | `ResourceAdapter.kt` | 733 | 2 199 |
| 24 | `BrowseDialogHelper.kt` | 733 | 2 199 |
| 25 | `TouchZoneGestureManager.kt` | 719 | 2 157 |
| 26 | `DropboxClient.kt` | 981 | 1 962 |
| 27 | `SmbClient.kt` | 954 | 1 908 |
| 28 | `FtpClient.kt` | 906 | 1 812 |
| 29 | `OneDriveRestClient.kt` | 896 | 1 792 |
| 30 | `SmbOperationStrategy.kt` | 822 | 1 644 |
| 31 | `SearchLyricsUseCase.kt` | 804 | 1 608 |
| 32 | `SettingsRepositoryImpl.kt` | 802 | 1 604 |
| 33 | `LocalMediaScanner.kt` | 786 | 1 572 |
| 34 | `SmbMediaScanner.kt` | 746 | 1 492 |
| 35 | `CloudOperationStrategy.kt` | 740 | 1 480 |
| 36 | `SmbOperationsUseCase.kt` | 738 | 1 476 |
| 37 | `ResourceEditorUseCase.kt` | 731 | 1 462 |
| 38 | `NetworkFileModelLoader.kt` | 719 | 1 438 |
| 39 | `SftpOperationStrategy.kt` | 711 | 1 422 |
| 40 | `FtpOperationStrategy.kt` | 707 | 1 414 |

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
| `OneDriveRestClient.kt` | 896 | 392 |
| `FtpClient.kt` | 906 | 412 |
| `SmbClient.kt` | 954 | 508 |
| `DropboxClient.kt` | 981 | 562 |
| `PlayerActivity.kt` | 816 | 580 |
| `FtpFileOperationHandler.kt` | 938 | 714 |
| `BaseFileOperationHandler.kt` | 939 | 717 |
| `BrowseFileOperationsManager.kt` | 941 | 723 |
| `FileInfoDialog.kt` | 946 | 738 |
| `TranslationManager.kt` | 961 | 783 |
| `PlayerMediaLoaderManager.kt` | 967 | 801 |
| `GoogleDriveRestClient.kt` | 1 102 | 804 |
| `CloudFileOperationHandler.kt` | 997 | 891 |
| `SmbConnectionManager.kt` | 1 000 | 900 |
| `CommandPanelController.kt` | 1 005 | 915 |
| `MainActivity.kt` | 928 | 1 140 |
| `StandalonePlayerActivity.kt` | 933 | 1 165 |
| `MediaFileAdapter.kt` | 1 095 | 1 185 |
| `ResourceEditorFragment.kt` | 978 | 1 390 |
| `ImageLoadingManager.kt` | 1 239 | 1 617 |

The loop continues from the smallest-margin file each iteration.

---

## Revision History

- **2026-04-30** — by `/spec-update` (`claude-opus-4-7`, focus: language, structure, verifiability, consistency, completeness, style)
  - Applied: 2. Proposed (DISCUSS): 4.

---

## Proposed Structural Changes

### Proposal P-1 — Strategic body language is English, must be Russian  (proposed 2026-04-30 by `claude-opus-4-7`)

**Status:** Proposed
**Affected:** entire document body
**Rationale:** CLAUDE.md mandates "Strategic body: Russian. Tactical body: English." This file is fully English. Auto-translation is forbidden by `/spec-update` constraints, so the rewrite must be authored manually (or the spec re-classified — see P-3).
**Suggested edit:**
> Translate sections "Completed (Waves 1–N)", "Current sizes", "Priority list", "Loop status" headings and prose to Russian; keep code identifiers, file paths, and helper class names in English.

### Proposal P-2 — Missing mandatory strategic sections  (proposed 2026-04-30 by `claude-opus-4-7`)

**Status:** Proposed
**Affected:** document skeleton
**Rationale:** Strategic specs in this repo follow a `## 1. Проблема / ## 2. Цели / Non-goals / ## N. Риски / ## N. Критерии приёмки` skeleton (cf. `S0003_link-receive-download.md`). S0002 carries none of these — only an implementation log and a priority table. Without a Goals + Acceptance-Criteria framing, the spec offers no observable success condition beyond "every `.kt` file ≤ 700 LOC", which is implicit in the title.
**Suggested edit:**
> Add empty section skeleton: `## 1. Проблема`, `## 2. Цели`, `## 3. Non-goals`, `## 4. Ограничения и инварианты`, `## 5. Открытые вопросы (Research)`, `## 6. ADR`, `## 7. Риски`, `## 8. Критерии приёмки`. Adding empty skeletons in this case is structural (would split the document and demand authoring work) so it is filed as DISCUSS rather than auto-applied.

### Proposal P-3 — Wave logs are implementation reporting, not strategy  (proposed 2026-04-30 by `claude-opus-4-7`)

**Status:** Proposed
**Affected:** "Completed (Waves 1–21)", "Wave N — in progress", "Wave 6 — deferred", "Loop status" sections
**Rationale:** Per CLAUDE.md "Strategic: no class names, file paths, line budgets". The wave tables list ~50 file paths, dozens of helper class names, and per-file LOC budgets — every one of which violates the rule. The content is valuable but belongs in a tactical sibling (`PLAN/S0002_decompose-giant-files/INDEX.md`) or a dedicated implementation log. Strategic spec should retain only the goal, score formula, and acceptance criteria.
**Suggested edit:**
> Move every "Wave N result" table and the "Current sizes" / "Priority list" / "Loop status" tables out of the strategic file into a tactical INDEX (or `IMPLEMENTATION_LOG.md` under the spec folder). Keep the score formula and weights in strategic §4 (Constraints).

### Proposal P-4 — Dynamic-loop ordering rule should be an ADR  (proposed 2026-04-30 by `claude-opus-4-7`)

**Status:** Proposed
**Affected:** "Wave 6 — deferred" section, last paragraph
**Rationale:** The decision "(LOC − 700) × weight, smallest delta first" overrides the headline score formula and is currently buried in a deferred-wave note. It is a load-bearing architectural choice for the loop and deserves an explicit ADR with rationale (avoid stalling on giant document viewers) and consequences (giant viewers slip late).
**Suggested edit:**
> Lift the "smallest-margin first" rule into a dedicated ADR under §6 ADR with: Context (giant viewers block the loop), Decision (smallest-delta first under 1500 LOC), Consequences (priority table is informational, not execution order).

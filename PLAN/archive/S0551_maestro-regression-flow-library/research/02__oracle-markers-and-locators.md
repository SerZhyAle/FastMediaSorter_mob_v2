# 02 - Oracle markers and locators (resolves strategic §6.2)

Discovery 2026-06-20 (read-only). Verified against `app_v2/src/main/java` and `res/layout`.

## Log completion markers per operation

Use the marker as the "operation done" oracle where one exists; otherwise assert by element.

- Browse listing loaded: `BrowseLoadingManager: COMPLETE - N files loaded and displayed` - `ui/browse/loading/BrowseLoadingManager.kt:271`.
- File copy / move / delete (UI-level, cross-protocol, multi-file or slow ops only): `FileOperationProgressDialog: Completed` - `ui/dialog/FileOperationProgressDialog.kt:148`. Single-file fast ops may skip the dialog - then assert by element.
- File rename: NO marker - assert by element (renamed name appears in list).
- Undo: NO single "all done" marker - assert by element (restored file / toast).
- Video first frame: `VideoPlayerManager: onRenderedFirstFrame path=..` - `ui/player/VideoPlayerManager.kt:493`.
- Video playback ready: `VideoPlayerManager: Playback ready` - `ui/player/VideoPlayerManager.kt:427`.
- Image displayed: NO completion marker - assert `photoView` visible.
- Audio playback started: `AudioPlaybackService: playAudioPlaylist size=..` - `ui/player/AudioPlaybackService.kt:604`.
- Lyrics shown: `LyricsManager: Showing lyrics viewer` - `ui/player/helpers/LyricsManager.kt:131` (and `Lyrics found, showing viewer` :78).
- PDF first page: `PdfViewerManager: firstPageRendered ..` - `ui/player/helpers/PdfViewerManager.kt:808`.
- EPUB first chapter: `EpubViewerManager: firstChapterRendered ..` - `ui/player/helpers/EpubViewerManager.kt:107`.
- TXT viewer: NO completion marker - assert by element (text container visible).
- Slideshow started: `SlideshowController: Starting slideshow with interval ..s` - `ui/player/SlideshowController.kt:146`; auto-start done: `PlayerUiStateCoordinator: Slideshow auto-start COMPLETE` - `ui/player/helpers/PlayerUiStateCoordinator.kt:174`.
- Info dialog: NO "rendered" marker (`showFileInfo: ..` at `PlayerDialogAndUiStateManager.kt:588` fires just before show) - assert `tvFileName` visible.
- Resume position dialog: NO marker (silent restore `PlaybackPositionRestorer: restored ..` :41) - assert by element.

Consequence: oracle convention is "assertVisible(expected element) AND (marker present if one exists) AND no crash block". Never weaken to marker-absent + optional-only.

## Stable resource-ids (entry name; Maestro id = `com.sza.fastmediasorter.debug:id/<name>`)

Browse (`activity_browse.xml`): `rvMediaFiles` (list), `btnSort`, `btnFilter`, `tvFilterBadge`, `btnCopy`, `btnMove`, `btnRename`, `btnDelete`, `btnUndo`, `emptyStateView`, `tvEmptyStateMessage`.

Player (`activity_player_unified.xml`): `mediaContentArea` (root), `btnSlideshowCmd` / `btnSlideShow` (overlay), `btnOverflowMenu`, `imageView`, `photoView`, `pdfScrollRecyclerView`, `epubWebView`, `btnPlayPause`, `playerView`, `progressBar`, `btnInfoCmd`, `btnLyricsCmd`.

Info dialog (`dialog_file_info.xml`): `tvFileName`, `tvFileSize`, `tvFilePath`, `btnClose`, `sectionExif`, `sectionAudio`, `sectionVideo`.

Add-resource (already used by `_shared/navigate_to_add_resource.yaml`): `btnAddResource`, `layoutResourceTypes`.

## No-id targets (must use text locators, locale-fixed)

- Browse long-press context menu items (copy/move/rename/delete) - PopupMenu, dynamically built, NO id. Use text (`"Copy"`, `"Move"`, ..). The on-screen operations bar buttons (`btnCopy` etc.) DO have ids - prefer them over the popup where the flow can use the bar.

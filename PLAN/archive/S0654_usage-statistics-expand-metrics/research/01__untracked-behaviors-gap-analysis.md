# Research 01 - Untracked behaviors gap analysis

**Spec:** S0654
**Date:** 2026-06-23
**Method:** codebase survey (catalog query over `*UseCase*`, walk of `docs/ALL_FEATURES.jsonl`, read of `domain/stats/` + emission sites).

## Current coverage (S0473, live)

`StatsKey` enum (`domain/stats/StatsModels.kt`) - all-time scalar counters across categories:

- OPERATIONS: FILES_COPIED/BYTES_COPIED, FILES_MOVED/BYTES_MOVED, FILES_DELETED/BYTES_FREED, FILES_ARCHIVED, FILES_EXTRACTED, FOLDERS_CREATED, DUPLICATE_SCANS, DUPLICATES_REMOVED/DUPLICATE_BYTES_FREED
- CAPTURE: PHOTOS_CAPTURED, VIDEOS_RECORDED, VOICE_NOTES, SCREENSHOTS
- VIEWING: IMAGES_VIEWED, VIDEOS_WATCHED/VIDEO_WATCH_MS, AUDIO_PLAYED/AUDIO_LISTEN_MS, DOCUMENTS_OPENED/DOCUMENT_PAGES, FRAMES_EXPORTED
- EDITING: IMAGE_EDITS, DRAWINGS, NOTES
- SOURCES: SOURCES_CONNECTED
- USAGE baseline: SESSIONS, ACTIVE_MS, launchCount, firstLaunch, firstInstallVersion
- Matrix action x media-type (copied/moved/deleted/bytes by IMAGE/VIDEO/AUDIO/DOCUMENT/OTHER)

## Extension seam

Add metric = new `StatsKey` + (optional) new `StatsEvent` variant + a `StatsSink.record(...)` call at an existing completion point. Sink no-ops when opt-in is off; `record()` is non-suspending and hot-path safe. Established injection pattern: `AdjustImageUseCase` / `ApplyImageFilterUseCase` take `StatsSink` via constructor and record after the result is confirmed.

## Confirmed gap: rename is explicitly dropped

`FileOperationUseCase.recordFileOpStats()` returns early on `FileOperation.Rename` (`is FileOperation.Rename -> return`). Renames are invisible to statistics today.

## Candidate metrics with confirmed emission points

Sorted value/cost. "low" = single inject + one record() at an existing completion point.

| # | Metric | Emission point (class/use-case) | Value type | Effort |
|---|--------|----------------------------------|-----------|--------|
| 1 | FILES_RENAMED | `FileOperationUseCase.recordFileOpStats()` (remove early return; add `FileOpAction.RENAME`) | count | low |
| 2 | FAVORITES_ADDED / FAVORITES_REMOVED | `FavoritesUseCase.toggleFavorite()` (two branches) | count+count | low |
| 3 | SLIDESHOW_SESSIONS / SLIDESHOW_IMAGES_SHOWN | `SlideshowController` (start/stop + onSlideAdvance) | count+count | low |
| 4 | SCHEDULED_TASKS_RUN / SCHEDULED_TASK_FILES_PROCESSED | `ExecuteScheduledOperationUseCase.invoke()` (result carries filesProcessed) | count+count | low |
| 5 | STREAMS_PLAYED (audio + video) | `RecordStreamPlayOutcomeUseCase.invoke()` (ok=true) | count+count | low |
| 6 | STREAMS_ADDED / PLAYLISTS_IMPORTED | `AddStreamSourceUseCase`, `ImportStreamPlaylistUseCase.invoke()` | count+count | low |
| 7 | GIF_FRAMES_SAVED | `SaveGifFirstFrameUseCase.execute()` (mirror `ExtractGifFramesUseCase`) | count | low |
| 8 | UNDO_OPERATIONS | `BrowseUndoManager.undoLastOperation()` + `FileOperationUseCase.undo()` | count | med |
| 9 | OCR_SCANS | `RecognitionBackend.recognizeText()` / `recognizeAndTranslateBlocks()` | count | med |
| 10 | TEXT_TRANSLATIONS | EPUB/PDF/image translation helpers (3 entry points, needs unification) | count | high |
| - | FILES_SHARED | no single dispatch point; per `ShareTargetHandler.dispatch()` or hook in `ShareMaterializationManager` | count | med (needs verification) |
| - | AUDIO_INLINE_PLAYS | `BrowseInlineAudioManager` onPlay | count | med (needs verification) |
| - | CHROMECAST_SESSIONS | `CastMediaManager` onSessionStarted | count | med (needs verification) |
| - | LYRICS_SEARCHES | `SearchLyricsUseCase` (non-null result) via `LyricsManager` | count | low (needs verification) |

Already covered (no new key): delete-by-size (routes through FileOp DELETE), zip creation (FILES_ARCHIVED).

## Rejected candidates (privacy / noise)

- Screen-open counts (Browse/Settings entries), theme switches, settings-search counts - noise > signal.
- EXIF-view counts, sorted file paths, in-editor text search, per-named-resource slideshow counts - privacy (leak content or storage structure). The report's privacy invariant (no identifiers, only 4 build/device constants) must hold.
- Sleep-timer fires, PiP entries - too device-specific / low value.

## Privacy invariant to preserve

`BuildStatisticsReportUseCase` exports NO user identifiers - only app version, flavor, device model, Android version. Every new counter must be an aggregate scalar with no path/name/content payload.

## Out-of-scope findings parked separately

- `SaveGifFirstFrameUseCase` lacks the `recordStats: Boolean` flag that sibling image use-cases expose - API inconsistency to resolve when wiring metric #7.
- `DownloadNetworkFileUseCase` is uninstrumented; a future NETWORK_BYTES_DOWNLOADED metric has a known point.

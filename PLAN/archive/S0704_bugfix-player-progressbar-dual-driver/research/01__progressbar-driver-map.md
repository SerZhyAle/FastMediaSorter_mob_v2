# S0704 research artifact - unified-player progressBar driver map

**Produced:** 2026-06-26 by android-solution-researcher (read-only).
**Consumed by:** F2 tactical planning (phase ordering + per-file blast radius).

> Scope: the UNIFIED player only (`PlayerActivity` + `ui/player/**` helpers/callbacks writing `binding.progressBar` / `safeViews.progressBarOrNull` / `safeViews.playerProgressBar` - all the same physical `R.id.progressBar` of `activity_player_unified.xml`). Standalone activities and non-player screens each have a single reactive driver and are out of scope.

## Writer sites (authoritative inventory)

| File : line(s) | show / hide / clear | Trigger | Media type(s) | Timing | Touches shared runnables? |
| --- | --- | --- | --- | --- | --- |
| `PlayerObserverManager.kt:55` | show or hide | `viewModel.loading` emission (from `PlayerMediaFilesLoader.setLoading`) | all except PDF/EPUB (gated) | immediate | no |
| `PlayerActivity.kt:461-462` | show | `showLoadingIndicatorRunnable` fires (1s) | image or video/audio | 1s delayed | is the runnable body |
| `ImageLoadingManager.kt:225` | clear | `clearForVideoTransition()` | image -> video | immediate | removes show + safety |
| `ImageLoadingManager.kt:350` | clear | `hideLoadingSafetyRunnable` fires (30s) | image/gif | 30s safety | is the safety runnable body |
| `ImageLoadingManager.kt:380` | clear | `displayImage()` entry | image/gif | immediate | removes show |
| `ImageLoadingManager.kt:449` | clear | `displayImage()` HEIC/HEIF/AVIF unsupported early-return | image | immediate | removes show + safety |
| `ImageLoadingManager.kt:835` | clear | local file not found | image (local) | immediate | removes show |
| `ImageLoadingGlideListeners.kt:56` | hide | `onLoadFailed` (Drawable) | image | immediate (Glide main) | removes show + safety |
| `ImageLoadingGlideListeners.kt:93` | hide | `onResourceReady` (Drawable) | image | immediate | removes show + safety |
| `ImageLoadingGlideListeners.kt:130` | hide | `onLoadFailed` (GIF) | gif | immediate | removes show + safety |
| `ImageLoadingGlideListeners.kt:154` | hide | `onResourceReady` (GIF) | gif | immediate | removes show + safety |
| `PlayerPlaybackCallbackImpl.kt:42` | hide | `onPlaybackReady()` | video/audio | immediate | removes show |
| `PlayerPlaybackCallbackImpl.kt:98` | show | `onBuffering(true)` | video/audio | immediate | no |
| `PlayerPlaybackCallbackImpl.kt:100` | hide | `onBuffering(false)` | video/audio | immediate | no |
| `PlayerMediaLoaderManager.kt:263` | schedules show | `playVideo()` after `clearForVideoTransition` | video/audio | 1s delayed | posts show |
| `PlayerMediaLoaderManager.kt:724` | hide | `bindServicePlayerToView()` | audio (service) | immediate | removes show |
| `PlayerMediaLoaderManager.kt:785` | clear | `reloadCurrentImage()` entry | image (after edit) | immediate | removes show |
| `PlayerDialogAndUiStateManager.kt:197` | show | `exportPdfToJpg()` entry | PDF | immediate | no |
| `PlayerDialogAndUiStateManager.kt:249` | hide | `exportPdfToJpg()` finally | PDF | immediate | no |
| `ImageOcrManager.kt:80` | show | `extractTextFromCurrentImage()` before IO | image/gif | immediate | no |
| `ImageOcrManager.kt:97,109` | hide | OCR success / exception branch | image/gif | immediate | no |
| `PlayerImageTranslationManager.kt:101` | show | `translateCurrentImage()` entry | image/gif | immediate | no |
| `PlayerImageTranslationManager.kt:43,142,156,164,179,200,206` | hide | stop / each result branch / NonCancellable finally | image/gif | immediate | no |
| `TextViewerLoader` / `TextEditorModeController` / `TextOcrDisplayManager` | show/hide | text load / save / OCR (via `safeViews.progressBarOrNull`) | TEXT | immediate | no |
| `EpubViewerManager` | show/hide | EPUB load (via `safeViews.playerProgressBar`); WebViewClient hides | EPUB | immediate | no |

## Shared infrastructure

- `PlayerActivity.kt:311` `loadingIndicatorHandler = Handler(Looper.getMainLooper())` - the single main-looper Handler.
- `PlayerActivity.kt:461` `showLoadingIndicatorRunnable` - single show-after-delay runnable, passed by reference into `ImageLoadingManager`, `PlayerMediaLoaderManager`, `PlayerPlaybackCallbackImpl`, `ImageLoadingGlideListeners` (wired in `PlayerManagerInitializer` / `PlayerViewerFactory`).
- `ImageLoadingManager.kt:347` `hideLoadingSafetyRunnable` - 30s safety, passed to the Glide listeners.
- `PlayerLifecycleManager.kt:215` removes the show runnable on destroy; `ImageLoadingManager.cleanup()` removes both.

So the Handler + show-runnable are already half-centralised, but the `isVisible` writes are scattered, and the reactive `viewModel.loading` driver bypasses the Handler infra entirely - the second uncoordinated owner.

## Conflict scenarios (concrete)

1. Reactive flow vs image cycle: after `onResourceReady` hides the spinner, a late `viewModel.loading=true` emission re-shows it over an already-displayed image. No contract orders them.
2. Ghost spinner after fast slideshow nav: OCR/`displayImage` posts the 1s show; navigation to a cache-instant image leaves a stale show scheduled -> spinner blinks 1s after the image is already up.
3. PDF export blink: navigating to an image mid-export runs `displayImage()` which immediately hides the export spinner; the export `finally` later hides again - spinner disappears before export completes.
4. image->video stuck buffering: `onBuffering(true)` set true is only cleared because `displayImage()` eager-hides; relies on accidental ordering, not a contract.
5. TEXT double-drive: the PDF/EPUB carve-out does NOT cover TEXT, so `viewModel.loading` and `TextViewerLoader` both write the same bar during text load.

## Media-type authority today

- IMAGE/GIF: `ImageLoadingManager` Handler + Glide callbacks (authoritative); `viewModel.loading` is a second writer during file-list fetch.
- VIDEO/AUDIO (in-app): `PlayerPlaybackCallbackImpl` + `PlayerMediaLoaderManager` 1s show; `viewModel.loading` second writer.
- AUDIO (service): `bindServicePlayerToView()` clears on bind.
- PDF: only `exportPdfToJpg`; reactive driver gated off.
- EPUB: `EpubViewerManager`; reactive driver gated off.
- TEXT: `TextViewerLoader` family; reactive driver NOT gated (latent bug).

## Recommended coordinator API (source-counted)

`PlayerLoadingIndicatorCoordinator` (main-thread, no coroutines): holds `MutableSet<LoadingSource>`; bar visible iff set non-empty.

- `show(source)` - mark active, sync.
- `showDelayed(source, delayMs = 1000)` - cancel pending schedule for that source, then post delayed mark+sync.
- `armSafetyTimeout(source, timeoutMs = 30_000)` - post delayed `hide(source)` (replaces `hideLoadingSafetyRunnable`).
- `hide(source)` - unmark, sync.
- `reset(source)` - unmark this source + cancel its pending show/safety (for transitions; does NOT touch other sources).
- `clearAll()` - unmark everything + cancel all pending (destroy / hard video transition).
- private `sync()` - `if attached & !destroyed: progressBar.isVisible = activeSources.isNotEmpty()`.

`LoadingSource` enum (one per writer family): `FILE_LIST`, `IMAGE_GLIDE`, `VIDEO_EXOPLAYER`, `AUDIO_EXOPLAYER`, `AUDIO_SERVICE`, `PDF_EXPORT`, `EPUB_LOAD`, `TEXT_LOAD`, `TEXT_SAVE`, `OCR`, `TRANSLATION`.

The Handler + both runnables move into the coordinator. `PlayerMediaLoaderManager` keeps a Handler reference (or a coordinator `postDelayed` passthrough) only for its audio-readiness Toast, which is not a spinner write.

## Migration phase ordering (risk-minimising)

1. Create coordinator (new file, no callers) + unit tests.
2. `ImageLoadingManager` + `ImageLoadingGlideListeners` (image cycle, self-contained).
3. `PlayerPlaybackCallbackImpl` + `PlayerMediaLoaderManager` (video/audio).
4. `PlayerObserverManager` (reactive `FILE_LIST`).
5. `ImageOcrManager` + `PlayerImageTranslationManager` + `PlayerDialogAndUiStateManager` (operation spinners).
6. TEXT/EPUB family (`TextViewerLoader`, `TextEditorModeController`, `TextOcrDisplayManager`, `EpubViewerManager`) - also closes the TEXT carve-out gap.
7. Delete orphaned `showLoadingIndicatorRunnable` (PlayerActivity) + `hideLoadingSafetyRunnable` (ImageLoadingManager); wiring cleanup in `PlayerManagerInitializer` / `PlayerViewerFactory` / `PlayerLifecycleManager`.

Blast-radius / line-budget watch: `PlayerActivity.kt` 1246, `PlayerMediaLoaderManager.kt` 1132, `ImageLoadingManager.kt` 1015 - all under the 1500 limit but near; coordinator calls are small additions, net `ImageLoadingManager` shrinks (safety runnable leaves).

## Open questions surfaced (resolved in strategic spec §5)

1. TEXT carve-out vs TEXT_LOAD source. 2. Handler ownership. 3. `audioReadinessFeedbackRunnable` passthrough. 4. Coordinator instantiation point. 5. Per-source vs global safety timeout. 6. EPUB hide site / safety timeout (needs Phase 6 investigation).

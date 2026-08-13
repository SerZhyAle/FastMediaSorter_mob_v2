# S0704 tactical plan - PlayerLoadingIndicatorCoordinator

**Strategic:** `PLAN/S0704_bugfix-player-progressbar-dual-driver.md`
**Research:** `research/01__progressbar-driver-map.md`
**Status:** Tactical (authored for owner review; no code written).

Migration is incremental and risk-ordered: build the coordinator first, migrate the most self-contained driver families before the cross-cutting ones, delete the orphaned shared runnables last. Each phase ends with a `standard debug` build (`.\a.ps1 dq`). The coordinator (Phase 1) also gets a JVM unit test. Device verification of the whole spinner behavior happens once at the end (BlockNeedUserTest -> `/spec-test-device`).

## Phase status

- [x] Phase 1 - Create coordinator + unit test
- [x] Phase 2 - Migrate image cycle (ImageLoadingManager + Glide listeners)
- [x] Phase 3 - Migrate video/audio (PlaybackCallback + MediaLoaderManager)
- [x] Phase 4 - Migrate reactive driver (PlayerObserverManager)
- [x] Phase 5 - Migrate operation spinners (OCR / translation / PDF export)
- [x] Phase 6 - Migrate TEXT/EPUB family (closes TEXT carve-out gap)
- [x] Phase 7 - Delete orphaned runnables + wiring cleanup

All phases implemented (code-complete). NOT yet built, unit-tested, or device-tested - the
implementing session ran under a `no build` constraint. Remaining before Verified: run `.\a.ps1 dq`
+ the new `PlayerLoadingIndicatorCoordinatorTest`, then insert `Timber.d("S0704: ..")` probes,
set `BlockNeedUserTest`, and run `/spec-test-device`.

## Implementation notes (deviations from the tactical text)

- Glide listener completion sites (Phase 2) call `reset(IMAGE_GLIDE)`, not `hide(IMAGE_GLIDE)` as
  written. The legacy code cancelled both pending runnables (show + safety) before hiding; §2.1
  `hide()` does NOT cancel a pending delayed-show, so literal `hide()` would re-arm the ghost
  spinner on a sub-1s load. `reset()` matches the original behaviour and the spec's stated fix.
- `bindServicePlayerToView` (Phase 3) calls `reset(VIDEO_EXOPLAYER)`, not `reset(AUDIO_SERVICE)`.
  The 1s show is armed in `playVideo()` BEFORE the audio/video branch, so it is keyed
  `VIDEO_EXOPLAYER`; only resetting that source cancels it. `AUDIO_SERVICE` is therefore currently
  unused (kept in the enum, documented as reserved). Same reasoning leaves `AUDIO_EXOPLAYER` unused
  (in-app audio buffering flows through the shared `VIDEO_EXOPLAYER` callback path).
- `armSafetyTimeout` gained an optional `onTimeout` callback so the image load-timeout toast
  (`msg_loading_timeout`) from the deleted `hideLoadingSafetyRunnable` is preserved rather than
  silently dropped.
- The coordinator is a `by lazy` property on `PlayerActivity` referenced by the managers, instead
  of being explicitly constructed in `PlayerManagerInitializer`. Same single-instance effect,
  order-independent, less wiring.
- TEXT/EPUB helpers (`TextViewerManager` + its 3 sub-helpers, `EpubViewerManager`,
  `EpubWebViewLifecycle`) are shared with the out-of-scope standalone activities, so the coordinator
  is threaded as a **nullable** param (`= null`). Unified player passes it; standalone leaves it
  null and keeps its existing single reactive driver + direct progressBar writes.

## Out-of-scope discrepancy surfaced (for owner)

`PdfViewerManager` writes the SAME physical `R.id.progressBar` (`safeViews.playerProgressBar`) at
~11 sites for PDF page-display loading. The research artifact missed this (it claimed PDF touches the
bar only via export). It is OUT of this ticket's defined scope (the enum has only `PDF_EXPORT`, and
the reactive driver is gated off for PDF, so PDF page display has a single owner today - no race
within PDF). It remains a direct writer of the shared bar. Decide whether to fold it in (add a
`PDF_VIEW` source) as a follow-up, or leave it (PDF display has no competing writer today).

## Phase 1 - Create coordinator + unit test

- New file `ui/player/helpers/PlayerLoadingIndicatorCoordinator.kt` with the API from strategic §2.1 and `LoadingSource` enum from §2.1.
- Constructor: `(progressBar: View, loadingIndicatorHandler: Handler, isDestroyed: () -> Boolean)`. The two spinner runnables are internal (per-source mark/unmark); `showDelayed` / `armSafetyTimeout` post internal `Runnable`s keyed by source.
- `sync()` guards `progressBar.isAttachedToWindow && !isDestroyed()`.
- New JVM test `test/.../ui/player/helpers/PlayerLoadingIndicatorCoordinatorTest.kt`: empty-set hides; one source shows; two sources, hide one stays visible; `reset(source)` cancels its pending show; `clearAll` idempotent; `armSafetyTimeout` hides after timeout (Robolectric main-looper or a passed test Handler).
- No callers yet; no behavior change.
- Verification: `.\a.ps1 dq` BUILD SUCCESSFUL; new test class green via `--tests *PlayerLoadingIndicatorCoordinatorTest`.

## Phase 2 - Migrate image cycle

- `ImageLoadingManager`: replace each `binding.progressBar.isVisible = false` (lines 225, 380, 449, 835) and the `displayImage()` schedule (postDelayed show 424 + safety 426) with `coordinator.reset(IMAGE_GLIDE)` then `coordinator.showDelayed(IMAGE_GLIDE)` + `coordinator.armSafetyTimeout(IMAGE_GLIDE)`. Delete the private `hideLoadingSafetyRunnable` (347) - moved into coordinator.
- `clearForVideoTransition()` (225) -> `coordinator.reset(IMAGE_GLIDE)` (NOT clearAll - preserves OCR/FILE_LIST).
- `ImageLoadingGlideListeners`: 4 sites (56/93/130/154) -> `coordinator.hide(IMAGE_GLIDE)`; drop the per-callback `removeCallbacks` pair (coordinator owns scheduling). Keep `isDestroyed` guard.
- HEIC/HEIF early-return (449) and not-found (380, 835) -> `coordinator.reset(IMAGE_GLIDE)`.
- Verification: `.\a.ps1 dq` SUCCESSFUL; no remaining `progressBar.isVisible` write in either file (`Grep`).

## Phase 3 - Migrate video/audio

- `PlayerPlaybackCallbackImpl`: 42 -> `hide(VIDEO_EXOPLAYER)`; 98 -> `show(VIDEO_EXOPLAYER)`; 100 -> `hide(VIDEO_EXOPLAYER)`.
- `PlayerMediaLoaderManager`: postDelayed show at 263 -> `coordinator.showDelayed(VIDEO_EXOPLAYER)`; bind clear at 724 -> `coordinator.reset(AUDIO_SERVICE)`; reload-image clear at 785 -> `coordinator.reset(IMAGE_GLIDE)`. Keep the `Handler` reference for `audioReadinessFeedbackRunnable` (Toast, not spinner).
- Verification: `.\a.ps1 dq` SUCCESSFUL; both files free of direct `progressBar.isVisible` writes.

## Phase 4 - Migrate reactive driver

- `PlayerObserverManager:52-57`: replace `activity.activityBinding.progressBar.isVisible = isLoading` with `if (isLoading) coordinator.show(FILE_LIST) else coordinator.hide(FILE_LIST)`. Keep the PDF/EPUB type gate (those types do not activate FILE_LIST).
- Verification: `.\a.ps1 dq` SUCCESSFUL.

## Phase 5 - Migrate operation spinners

- `ImageOcrManager`: 80 -> `show(OCR)`; 97, 109 -> `hide(OCR)`.
- `PlayerImageTranslationManager`: 101 -> `show(TRANSLATION)`; 43, 142, 156, 164, 179, 200, 206 -> `hide(TRANSLATION)` (keep the `NonCancellable` finally that guarantees the hide).
- `PlayerDialogAndUiStateManager`: 197 -> `show(PDF_EXPORT)`; 249 -> `hide(PDF_EXPORT)`.
- Verification: `.\a.ps1 dq` SUCCESSFUL; three files free of direct writes.

## Phase 6 - Migrate TEXT/EPUB family

- First investigate the EPUB hide site (WebViewClient) - strategic §5 open item; locate where load completes, route to `coordinator.hide(EPUB_LOAD)`; if no deterministic completion, add `armSafetyTimeout(EPUB_LOAD)`.
- `TextViewerLoader` (load) -> `show/hide(TEXT_LOAD)`; `TextEditorModeController` (save) -> `show/hide(TEXT_SAVE)`; `TextOcrDisplayManager` -> `show/hide(OCR)`; `EpubViewerManager` -> `show/hide(EPUB_LOAD)`. These use `safeViews.progressBarOrNull` / `safeViews.playerProgressBar` today; pass the coordinator in.
- Closes the latent TEXT double-drive (TEXT no longer races `viewModel.loading`, because FILE_LIST + TEXT_LOAD are both counted, not competing writers).
- Verification: `.\a.ps1 dq` SUCCESSFUL.

## Phase 7 - Delete orphaned runnables + wiring cleanup

- Delete `PlayerActivity.showLoadingIndicatorRunnable` (461) and its `internal` exposure; stop passing `showLoadingIndicatorRunnable` through `PlayerManagerInitializer` (432-433, 762-763), `PlayerViewerFactory` (20-21), into `ImageLoadingManager`/`PlayerMediaLoaderManager`/`PlayerPlaybackCallbackImpl`/`ImageLoadingGlideListeners` constructors. Keep `loadingIndicatorHandler` (still needed by coordinator + audio Toast).
- `PlayerLifecycleManager.onDestroy` (215) and `ImageLoadingManager.cleanup()` -> `coordinator.clearAll()`.
- Create the coordinator in `PlayerManagerInitializer` before media managers; inject it into every migrated class.
- Verification: `.\a.ps1 dq` SUCCESSFUL; project-wide `Grep "progressBar.isVisible ="` over `ui/player/**` returns zero (only coordinator's single `sync()` write remains). Insert `Timber.d("S0704: <entry>")` probes at the changed flow entries before the final build, set `BlockNeedUserTest`, run device test.

## Device test checklist (BlockNeedUserTest, end of Phase 7)

- Rapid slideshow nav over cached + slow-network images: no ghost/stuck spinner.
- image -> video -> image transitions: spinner clears each time.
- PDF export while navigating: export spinner not pre-empted.
- OCR / translation started then navigate away: spinner tracks the operation, not the new image.
- TEXT + EPUB load: single clean spinner, no flicker.

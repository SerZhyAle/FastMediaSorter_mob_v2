# Tactical Plan: S0895 - player-misc-p2

**Strategic spec:** [`../S0895_player-misc-p2.md`](../S0895_player-misc-p2.md)
**Research inputs:** none (research folded into strategic §0 findings + live-code re-verification below)
**Feature:** Player subsystem misc - dead paths, scope races, singleton clobber
**Tier:** 3 - Moderate
**Priority:** 40
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-07-03

> Scope: tactical, English, developer handoff. Every finding was re-checked against live code before implementing. `PlayerManagerInitializer.kt` was edited by sibling ticket S0893 earlier in this batch - line numbers below are the current live ones (568 -> 569 in the strategic spec's original citation), re-verified via Grep, not assumed.

---

## Pre-flight re-verification (skeptic pass over the 9 static findings)

1. `AudioPlaybackService.kt:414` - CONFIRMED. `serviceScope.launch(Dispatchers.IO) { playbackPositionRepository.savePosition(..) }` is followed immediately by `serviceScope.cancel()` with no join/await - the launched coroutine is cancelled before or immediately after the dispatcher picks it up, so the save is dead in practice.
2. `PlayerEntryCoordinator.kt:40` - CONFIRMED dead. Grepped `PlayerEntryCoordinator` project-wide: only hit is the Hilt `@Binds` in `PlayerContractsModule.kt` and the class's own file - no ViewModel/Activity/UseCase injects it anywhere. Grepped `PlaybackEntryRequest`/`PlaybackEntryDecision`/`DeviceClass` - used only within `PlayerEntryCoordinator.kt` itself. Grepped `PLAN/` for scaffolding references (feedback_dead_code_vs_active_tickets caution) - only hits are S0878 (the triage container that produced this finding) and this ticket itself, no other ticket depends on it. Safe to delete.
3. `LocalPlaybackHelper.kt:100` - CONFIRMED. The BD-TS branch hand-builds `ExoPlayer.Builder(context).setMediaSourceFactory(..).setAudioAttributes(..).build()`, skipping every step `PlayerSetupHelper.createPlayer()` does (tuned `PrefetchLoadControlFactory` load control + its listener tracking via `activeExtraPlayerListener`, effects-pipeline flag reset, `onPlayerCreated` callback). Also uses `currentPlayerView?.player = exoPlayer` (safe call) where a null `currentPlayerView` would silently build an orphaned, unattached player - `createPlayer()` takes a non-null `PlayerView` parameter and has no such gap.
4. `PlayerLifecycleManager.kt:227` - CONFIRMED. `activity.videoPlayerManager` is a lazy-getter property (`get() = _videoPlayerManager ?: PlayerViewerFactory(this).createVideoPlayerManager().also { _videoPlayerManager = it }` in `PlayerActivity.kt:134-135`), not a `lateinit var` - it never throws `UninitializedPropertyAccessException`. The established idiom used everywhere else in `PlayerActivity.kt` (lines 718, 738, 952) and in this same file for other managers (`_textViewerManager`, `_pdfViewerManager`, `_epubViewerManager`) is `if (activity._xxxManager != null)`; this one site uses the wrong try/catch pattern, so the "not initialized, skip" catch never fires and a fresh `VideoPlayerManager` is constructed during teardown when none existed.
5. `PlayerVrLaunchManager.kt:124` - CONFIRMED. `markPromptDismissed()` (suspend, writes via `settingsRepository.updateSettings`) runs unguarded between the `Started` dispatch result and `activity.finishAndRemoveTask()`; both are inside `activity.lifecycleScope.launch { .. }` with no surrounding try/catch at that point (the function's only try/catch wraps `buildStartRequest` earlier). A write failure throws uncaught in the coroutine (crash) and skips `finishAndRemoveTask()` (2D host never torn down after VR already took over playback).
6. `NowPlayingViewModel.kt:172` - CONFIRMED. `startPositionPoll()`/`stopPositionPoll()` are gated only by `Player.Listener.onIsPlayingChanged` and `connect()`'s initial state - `positionPollJob` runs on `viewModelScope` (lives until `onCleared`), with no wiring to the hosting `NowPlayingBottomSheetFragment`'s `onStart`/`onStop`. The Fragment already has an `onStart()` override (`NowPlayingBottomSheetFragment.kt:77`) and no `onStop()`.
7. `PlayerManagerInitializer.kt` - line drifted 568 -> 569 (S0893 edited this file earlier in this batch). CONFIRMED via Grep: 3 sites at lines 569, 579, 594, all `activity.lifecycleScope.launch { <flow-chain>.collect { .. } }` with no `repeatOnLifecycle`. `androidx.lifecycle.Lifecycle` and `androidx.lifecycle.repeatOnLifecycle` are already imported (lines 50, 52) but unused anywhere in the file - ready to consume directly.
8. `StreamPlaybackHelper.kt:183` - CONFIRMED. Both recovery branches in `onPlayerError` do `managerScope.launch { delay(backoffMs); exoPlayer?.seekToDefaultPosition(); exoPlayer?.prepare() }`, reading the mutable `exoPlayer` property at execution time (after the delay) rather than the specific instance that errored. If the user navigates to a different file within the 5-16s backoff window, `exoPlayer` now points at the new file's player and the stale recovery seeks/re-prepares it.
9. `AudioServiceController.kt:309` - CONFIRMED. `MemoryEnduranceTracker` (read in full) is a plain Kotlin `object` with one mutable `scenarioId: String` field - a single global slot, no per-caller stack. `startScenario("AUD-playback")` fires only from the `StoredNew` connection-store branch; `release()` calls `endScenario()` unconditionally, including when this controller never connected (e.g. eager/defensive construction with no playback), which would end whatever scenario happens to be globally active at that moment.

---

## Phase Overview

- Phase 01 - `AudioPlaybackService.kt`: independent scope for the destroy-edge position save.
- Phase 02 - delete `PlayerEntryCoordinator.kt` + its Hilt binding in `PlayerContractsModule.kt`.
- Phase 03 - `PlayerSetupHelper.kt` + `LocalPlaybackHelper.kt`: BD-TS path routed through `createPlayer()`.
- Phase 04 - `PlayerLifecycleManager.kt` + `PlayerVrLaunchManager.kt` + `NowPlayingViewModel.kt` + `NowPlayingBottomSheetFragment.kt`: four independent single/dual-file correctness fixes.
- Phase 05 - `PlayerManagerInitializer.kt`: wrap 3 collectors in `repeatOnLifecycle`.
- Phase 06 - `StreamPlaybackHelper.kt` + `AudioServiceController.kt`: two independent staleness/single-slot-clobber fixes.

All phases touch disjoint file sets except Phase 03 (2 files, producer/consumer pair) - no cross-phase symbol dependency, order is arbitrary.

---

## Phase 01 - AudioPlaybackService.kt

**Steps:** In `onDestroy()`, replace the `serviceScope.launch(Dispatchers.IO) { .. }` final-position save with a dedicated `CoroutineScope(SupervisorJob() + Dispatchers.IO)` local to that save (not `GlobalScope` - CLAUDE.md Rule 19 - and not `serviceScope`, which is cancelled on the very next line). The coroutine cancels its own scope in a `finally` block once the write completes or fails.

**Verification:** `.\a.ps1 fc` PASS. Grep confirms `serviceScope.cancel()` no longer sits immediately after the save's `launch` call.

**Status:** Done.

---

## Phase 02 - Delete PlayerEntryCoordinator

**Steps:** Delete `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt` (interface + impl + `PlaybackEntryRequest`/`PlaybackEntryDecision`/`DeviceClass`, all dead). Remove `bindPlayerEntryCoordinator` from `PlayerContractsModule.kt` plus its two now-unused imports, keeping `bindStereoDetectionFacade` intact.

**Verification:** `.\a.ps1 fc` PASS (confirms no remaining reference anywhere breaks compilation). Grep `PlayerEntryCoordinator` project-wide returns zero hits outside `PLAN/`.

**Status:** Done.

---

## Phase 03 - BD-TS local playback via createPlayer()

**Steps:** Add an optional `mediaSourceFactory: MediaSource.Factory? = null` parameter to `VideoPlayerManager.createPlayer()` in `PlayerSetupHelper.kt`; when non-null, chain `.setMediaSourceFactory(it)` onto the `ExoPlayer.Builder` before `.build()`. In `LocalPlaybackHelper.kt`'s BD-TS branch, replace the hand-built player with a `createPlayer(playerView, isAudio = isAudio, mediaSourceFactory = localFactory.buildBdTsMediaSourceFactory(TsPacketFormat.BD_192))` call, guarded on `currentPlayerView` being non-null (log a warning instead of silently building an orphaned player when it is null). Remove the now-unused `AudioAttributes`/`C`/`ExoPlayer` imports from `LocalPlaybackHelper.kt`.

**Verification:** `.\a.ps1 fc` PASS. Grep confirms `createPlayer(` call site added in `LocalPlaybackHelper.kt`; the sole pre-existing call site (`LocalPlaybackHelper.kt:112`, the non-BD-TS fallback) is unaffected (new parameter is optional, trailing).

**Status:** Done.

---

## Phase 04 - Four independent correctness fixes

**Steps:**

1. `PlayerLifecycleManager.kt` `releaseResources()`: replace the try/catch around `activity.videoPlayerManager.releasePlayer()` with `if (activity._videoPlayerManager != null) { activity.videoPlayerManager.releasePlayer() }`, matching the established idiom used elsewhere in the same file and in `PlayerActivity.kt`.
2. `PlayerVrLaunchManager.kt` `launch()`: wrap `markPromptDismissed()` in try/catch (rethrow `CancellationException`, log+continue on other `Exception`) inside the `Started` branch, so `activity.finishAndRemoveTask()` always runs once VR dispatch succeeded regardless of the settings-write outcome.
3. `NowPlayingViewModel.kt`: add `hostStarted` field + `onHostStart()`/`onHostStop()` public methods; route `onIsPlayingChanged` and `connect()`'s initial-state branch through a single `updatePositionPoll()` gate (`hostStarted && state.isPlaying`) instead of calling `startPositionPoll()`/`stopPositionPoll()` directly.
4. `NowPlayingBottomSheetFragment.kt`: call `viewModel.onHostStart()` from `onStart()` and add an `onStop()` override calling `viewModel.onHostStop()`.

**Verification:** `.\a.ps1 fc` PASS. Grep confirms `if (activity._videoPlayerManager != null)` present; `catch (e: CancellationException)` present in `PlayerVrLaunchManager.kt`; `onHostStart`/`onHostStop` present in both `NowPlayingViewModel.kt` and `NowPlayingBottomSheetFragment.kt`.

**Status:** Done.

---

## Phase 05 - PlayerManagerInitializer.kt collector safety

**Steps:** Wrap each of the 3 `activity.lifecycleScope.launch { .. }` bodies (stereo-effect apply, image stereo-crop re-display, panelStereoSingleEye re-display) in `activity.repeatOnLifecycle(Lifecycle.State.STARTED) { .. }`, consuming the already-imported-but-unused `Lifecycle`/`repeatOnLifecycle` imports.

**Verification:** `.\a.ps1 fc` PASS. Grep `repeatOnLifecycle(Lifecycle.State.STARTED)` matches 3 times in the file. `assert-neuroslop.ps1 -ChangedFiles` unsafe-collect dimension shows a file-local improvement (fewer bare-collect occurrences), not merely a steady baseline.

**Status:** Done.

---

## Phase 06 - Two independent staleness/clobber fixes

**Steps:**

1. `StreamPlaybackHelper.kt` `onPlayerError()`: capture `val erroredPlayer = exoPlayer` at the top of the function; in both delayed-recovery `managerScope.launch { .. }` bodies, add `if (exoPlayer !== erroredPlayer) return@launch` immediately after `delay(backoffMs)`, before touching `exoPlayer`.
2. `AudioServiceController.kt`: add `private var ownsEnduranceScenario = false`; set it `true` alongside the existing `MemoryEnduranceTracker.startScenario("AUD-playback")` call in the `StoredNew` branch; in `release()`, only call `MemoryEnduranceTracker.endScenario()` (and reset the flag) when `ownsEnduranceScenario` is true.

**Verification:** `.\a.ps1 fc` PASS. Grep confirms `erroredPlayer` present with 2 guard sites; `ownsEnduranceScenario` present (field + set + guarded read).

**Status:** Done.

---

## Completion Gate

- All six phases Done.
- `docs/FEATURES*.md` - skip (internal contract/correctness fixes, no user-visible feature copy).
- `dev/CHANGELOG.md` - one batched entry via `add_to_dev_log.ps1`.
- `dev/CATALOG/app_v2.jsonl` regenerated (one class removed - `PlayerEntryCoordinatorImpl`/`PlayerEntryCoordinator`).
- All 9 findings are statically provable by code review + build; none require on-device behavioral confirmation the way S0896's audio-focus/multi-window findings did - candidate for `Verified` rather than `BlockNeedUserTest`, pending final audit pass.

---

## Rollback Plan

Low-risk: revert the 9 touched files + restore the deleted `PlayerEntryCoordinator.kt`. No Room schema, no Hilt scope change (only removes one dead `@Binds`), no new classes. `createPlayer()`'s new parameter is additive/optional - the one pre-existing call site is behaviourally unchanged.

# Phase 3 — Wire Checkpoints into Player/Viewer/Browse Surfaces

**Status:** [x] Done

## Goal

Add `MemoryEnduranceTracker` calls to the four target surfaces. Each call is a one-liner inside the existing flow — no restructuring, no new classes. All calls are inside `if (BuildConfig.DEBUG)` guards (or delegated to the tracker's own guard).

## Integration points

### Image slideshow — `SlideshowController`

- `startScenario("IMG-slideshow")` on slideshow start (inside `start()` or equivalent).
- `checkpoint("TRANSITION")` on each image advance (`next()`, `previous()`, `random()`).
- `endScenario()` on slideshow stop/pause/destroy.
- `cooldownCheckpoint()` via `Handler.postDelayed` 30 000 ms after `endScenario()`.

File: `ui/player/SlideshowController.kt`

### Audio player — `AudioPlaybackService` or `AudioServiceController`

Prefer `AudioServiceController.kt` if it manages track transitions.

- `startScenario("AUD-playback")` on audio session start.
- `checkpoint("TRANSITION")` on each track change (next/previous/shuffle pick).
- `endScenario()` on session stop/service destroy.
- `cooldownCheckpoint()` via `Handler.postDelayed` 30 000 ms after `endScenario()`.

File: `ui/player/helpers/AudioServiceController.kt` (or `AudioPlaybackService.kt` if the transition event lives there)

### Video player — ExoPlayerControlsManager or PlayerPlaybackCallbackImpl

- `startScenario("VID-playback")` on first media item load.
- `checkpoint("TRANSITION")` on each item transition (media item index change).
- `endScenario()` on player stop/release.
- `cooldownCheckpoint()` via `Handler.postDelayed` 30 000 ms after `endScenario()`.

File: `ui/player/helpers/ExoPlayerControlsManager.kt` (preferred) or `ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`

### Browse/sort surface — `BrowseCacheManager` or `BrowseFileListManager`

- `startScenario("BRW-sort")` on folder open that triggers a heavy list load (call from `BrowseFileListManager` when directory item count ≥ 500).
- `checkpoint("SORT_CHANGE")` on each sort-mode change.
- `checkpoint("FILTER_CHANGE")` on each filter-type change.
- `checkpoint("FOLDER_ENTER")` on each enter-folder navigation.
- `endScenario()` on folder exit / activity stop.

File: `ui/browse/filelist/BrowseFileListManager.kt` and/or `ui/browse/cache/BrowseCacheManager.kt`

## Wiring pattern

```kotlin
// At each integration site — one-liner, no restructuring needed:
MemoryEnduranceTracker.checkpoint("TRANSITION")
```

Full guard is inside the tracker itself. No `if (BuildConfig.DEBUG)` wrapping required at call sites.

## Verification predicates

- [ ] Logcat shows `MEM_ENDURANCE | scenario=IMG-slideshow` lines during a slideshow run.
- [ ] Logcat shows `MEM_ENDURANCE | scenario=AUD-playback` lines during audio playback with track changes.
- [ ] Logcat shows `MEM_ENDURANCE | scenario=VID-playback` lines during video playback with item transitions.
- [ ] Logcat shows `MEM_ENDURANCE | scenario=BRW-sort` lines during large-folder browse with sort changes.
- [ ] Release build compiles cleanly — no tracker calls in release APK bytecode.
- [ ] `Timber.d("S0120: MemoryEnduranceTracker wired — image/audio/video/browse checkpoints active")` present at app startup in `AppStartupInitializer` or equivalent.

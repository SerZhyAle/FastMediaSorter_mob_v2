## S0550 - PlayerActivity crash: dialogAndUiStateManager not initialized during slideshow-state callback on image open

**Status:** Archived

### 0. Context / raw capture

Auto-captured during S0497 device work (CLAUDE.md §3.1) - out of scope for that ticket. Opening a
local image in the player crashed into CrashActivity.

Repro (emulator-5556, Pixel 6, API 33, standard-debug `2.60.6192.144-DEBUG`):

- Browse `virtual://all_images`, grid view, tap `photo_001.jpg`
  (`/sdcard/Download/FastMediaSorter_Test/DCIM/photo_001.jpg`, 4032x2268, ~7.81 MB JPEG).
- `PlayerActivity` starts, `ImageLoadingManager.displayImage` begins, process crashes immediately.
- Not every image triggers it: a small image (`CAP_...jpg`, 1280x960, ~195 KB) opened fine earlier
  the same session. Likely an init-ordering / timing race, possibly correlated with the slideshow
  callback firing before manager init completes (large-decode latency may widen the window).

Stack trace (from `files/logs/fastmediasorter_crash_20260619_223301.log`):

```
kotlin.UninitializedPropertyAccessException: lateinit property dialogAndUiStateManager has not been initialized
    at com.sza.fastmediasorter.ui.player.PlayerActivity.getDialogAndUiStateManager$app_v2(PlayerActivity.kt:160)
    at com.sza.fastmediasorter.ui.player.helpers.PlayerNavigationManager$initializeSlideshowController$1.onSlideshowStateChanged(PlayerNavigationManager.kt:78)
    at com.sza.fastmediasorter.ui.player.SlideshowController.stopSlideshow(SlideshowController.kt:207)
    at com.sza.fastmediasorter.ui.player.helpers.PlayerNavigationManager.updateSlideshowState(PlayerNavigationManager.kt:387)
    at com.sza.fastmediasorter.ui.player.PlayerActivity.updateSlideShow$app_v2(PlayerActivity.kt:628)
    at com.sza.fastmediasorter.ui.player.callbacks.PlayerImageLoadingCallbackImpl.updateSlideShow(PlayerImageLoadingCallbackImpl.kt:56)
    at com.sza.fastmediasorter.ui.player.ImageLoadingManager$displayImage$1.invokeSuspend(ImageLoadingManager.kt:594)
    at com.sza.fastmediasorter.ui.player.PlayerManagerInitializer.initPlayerControlsAndOcr(PlayerManagerInitializer.kt:579)
    at com.sza.fastmediasorter.ui.player.PlayerManagerInitializer.initialize(PlayerManagerInitializer.kt:75)
    at com.sza.fastmediasorter.ui.player.PlayerActivity.initializeManagers(PlayerActivity.kt:501)
    at com.sza.fastmediasorter.ui.player.PlayerActivity.onCreate(PlayerActivity.kt:470)
    Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [StandaloneCoroutine{Cancelling}, Dispatchers.Main.immediate]
```

### 1. Problem

During `PlayerActivity.onCreate` → `initializeManagers` → `PlayerManagerInitializer.initialize` →
`initPlayerControlsAndOcr`, the image-load path (`ImageLoadingManager.displayImage`) invokes the
slideshow callback `PlayerImageLoadingCallbackImpl.updateSlideShow` → `PlayerActivity.updateSlideShow`
→ `PlayerNavigationManager.updateSlideshowState` → `SlideshowController.stopSlideshow` →
`onSlideshowStateChanged`, which reads `PlayerActivity.dialogAndUiStateManager` (a `lateinit`) before
it has been assigned in the init sequence → `UninitializedPropertyAccessException` → process crash →
CrashActivity.

### 2. Goal

- Opening any local image in `PlayerActivity` never crashes due to a manager accessed before init.
- The slideshow-state callback either is not wired until all referenced managers are initialized, or
  it null/ready-guards `dialogAndUiStateManager` access.

### 3. Notes / leads (unverified)

- Candidate fixes: initialize `dialogAndUiStateManager` before `initPlayerControlsAndOcr`; or defer
  `SlideshowController` callback registration until after manager init; or guard the
  `onSlideshowStateChanged` path against pre-init access. Pick after reading the init order in
  `PlayerManagerInitializer` and `PlayerActivity.initializeManagers`.
- Confirm whether large-image decode latency is required to reproduce, or it is purely ordering.

### 5. Root cause (verified)

- `PlayerManagerInitializer.initialize()` runs `initPlayerControlsAndOcr()` (assigns the stereo-mode
  collectors that synchronously call `imageLoadingManager.displayImage`) before `initUiCoordinators()`,
  where `dialogAndUiStateManager` is assigned (`PlayerManagerInitializer.kt:833`).
- The init-time `displayImage` coroutine calls `updateSlideShow()` ->
  `PlayerNavigationManager.updateSlideshowState()` -> `SlideshowController.stopSlideshow()` ->
  `onSlideshowStateChanged()`, which reads the not-yet-assigned `dialogAndUiStateManager` lateinit ->
  `UninitializedPropertyAccessException`.
- It is purely an init-ordering race; large-image decode latency only widens the window, it is not
  required. `navigationManager` and `imageLoadingManager` are already initialized at this point - only
  `dialogAndUiStateManager` is late.

### 6. Fix

- Added readiness flag `PlayerActivity.isDialogAndUiStateManagerInitialized` (mirrors the existing
  `isMediaLoaderManagerInitialized` / `isCommandPanelControllerReady` pattern).
- Guarded both `dialogAndUiStateManager` accesses in the slideshow callback
  (`PlayerNavigationManager.onSlideshowStateChanged` and `onCountdownTick`) behind that flag. Skipping
  the UI sync during init is safe: the default inactive button / hidden countdown state is rendered
  correctly once init completes and state observers fire.

### 7. Similar problems (research)

- `onCountdownTick` had the identical unguarded `dialogAndUiStateManager` access - fixed in the same
  change.
- `PlayerImageLoadingCallbackImpl.onAudioMetadataLoaded` -> `activity.onAudioMetadataLoaded` reaches
  `audioMetadataManager`, also assigned late in `initUiCoordinators` (`PlayerManagerInitializer.kt:831`).
  Not reachable from the reported image path (only fires for audio metadata, asynchronously), so it is a
  latent risk rather than the reported crash. Left untouched to keep this fix scoped; flag for a future
  audit if an audio-open init race surfaces.

### 4. Evidence

- Device crash log: `/sdcard/Android/data/com.sza.fastmediasorter.debug/files/logs/fastmediasorter_crash_20260619_223301.log`.
- Captured during S0497 Phase 03 screenshot work.

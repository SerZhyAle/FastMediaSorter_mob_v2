# Phase 03 - Standalone host: VR entry (new pipeline)

**Status:** ✅ Done
**Completed:** 2026-07-19

## Step 03.1 - StandaloneVrCinemaLaunchManager (new)

**Status:** `[x] done`

**Files Touched:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneVrCinemaLaunchManager.kt`

`@ActivityScoped @Inject` manager mirroring Browse/Resource VR-cinema managers: injects `XrDetectionFacade` + `StartVrPlaybackUseCase`; `isAvailable` from XR state; `launch(file)` cold-launches VR for the current video (`source = CONTROLS_ROW`, `returnTarget = null`). S1114 debug tag in launch.

## Step 03.2 - Wire StandaloneVideoControlsManager + PhotoVideoStandaloneActivity

**Status:** `[x] done`

**Depends on:** 03.1, 01.2

**Files Touched:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneVideoControlsManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`

Controls manager: added `onVrLaunchClicked()` + `isVrEntryAvailable()` to the callback, wired `btnVrLaunch`, controller-visibility listener refresh. Activity: injected `StandaloneVrCinemaLaunchManager`; callback launches VR for the current video and reports availability gated on XR + `mediaType == VIDEO`.

**Step Log:**
- 2026-07-19 - standalone VR entry complete. PASS (build).

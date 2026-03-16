# Specification: Resume Playback from Last Active Media

## 1. Business Context & Objective
Implement a feature to automatically resume the last active media playback (audio or video) upon application restart, provided the app was terminated unexpectedly (e.g., system killed the process, device was turned off, user swiped it away from recent apps).

**User Story:**
A user is listening to music or watching a video on a car tablet. The ignition is turned off, causing the device to lose power and the app to be killed by the Android system. Upon starting the car again and launching the app via the launcher icon, the app should automatically:
- Navigate to the appropriate screen (File Browser or Player).
- Open the exact same resource and directory.
- Select and resume the exact same media file.
- Restore playback state (playing or paused).
- Restore context settings (active sorting for correct next-track sequencing).

> [!IMPORTANT]
> **DEVELOPER DIRECTIVE:** This repository has strict protocols outlined in `dev/AGENT_WORKFLOW.md` and `dev/GEMINI.md`. You must rigorously follow all architectural patterns (Clean + MVVM), write logs strictly to `/temp`, and leave zero Activity-level logic. Any feature changes must preserve all existing functionality. Code carefully and iteratively, logging your progress with `add_to_dev_log.ps1`.

## 2. Technical Requirements
- **State Saving:** Periodically save the "global last file state". We will hook into the existing playback position saving mechanism. A ~10-20 seconds periodic interval is considered acceptable. State is also saved immediately on Play/Pause toggle and on track change. **A timestamp must be saved for TTL purposes.**
- **State Clearing (Reset):** The saved state must be cleared in the following cases:
  1. The user **explicitly exits** the Player or Browser (back button that finishes the Activity, or a close button in the UI).
  2. The user **opens a different root resource** (switches to a different `resourceId`). Navigating into subfolders within the **same** resource does **not** clear the state, as long as background playback continues.
  3. The user **revokes storage permissions** while the app was asleep.
  4. The network/cloud resource becomes unreachable at startup.
- **Conditional Resumption:** Resumption is triggered *only* when the application is launched via the main launcher icon (`Intent.ACTION_MAIN` + `Intent.CATEGORY_LAUNCHER` in `MainActivity`). The following launch types must bypass resume logic entirely:
  - Widget launch (`ResourceLaunchWidgetProvider` starts `BrowseActivity` directly, so `MainActivity` is not involved — no special handling needed there).
  - `ACTION_START_SLIDESHOW` intent received in `MainActivity` (slideshow widget — proceed to slideshow, skip resume).
  - External file-open intents (any intent with action other than `ACTION_MAIN`).
  - App is brought to foreground with `AudioPlaybackService` still running (process was not killed — user just backgrounded the app). In this case playback is already live; resume logic must be skipped.
- **Availability Check & Setup:** Before navigating to the saved screen, verify the target is reachable (local file exists, or network resource is connectable). This check must complete within a **5-second timeout** (`withTimeout(5000L)`).
  - During the entire check (especially for network resources which may take 1-3 seconds to wake), **show a full screen non-blocking loading indicator** (e.g., a Splash Screen or a blocking `ProgressBar` on `MainActivity`) to prevent UI glitches and user interaction before routing.
  - If the check fails, times out, or the state is older than the **defined TTL limit of 48 hours** (`RESUME_TTL_MS = 48 * 60 * 60 * 1000L` — define this constant in `ResumeStateRepository`): show a `Toast` ("Resumption impossible/expired"), call `clearState()`, dismiss loader, and proceed to the standard main screen.
  - Also ensure that the system's auto-restart of `AudioPlaybackService` (e.g. after OOM kills) does not conflict with this `MainActivity` logic.
- **Resume Strategy by Resource & Media Type:** The behavior upon successful availability check depends on both `ResourceType` and `MediaType`. Apply the following matrix:

  | Resource type | Media type | Resume behavior |
  |---|---|---|
  | `LOCAL` | AUDIO | Open Player/Browser, auto-play from saved position. |
  | `LOCAL` | VIDEO | Open Player, auto-play from saved position. |
  | `SMB` / `FTP` / `SFTP` | AUDIO | Open Player/Browser, show buffering progress indicator, auto-play once buffer is ready. |
  | `SMB` / `FTP` / `SFTP` | VIDEO | Open Player at saved position, **start in PAUSED state**. User manually presses Play. |
  | `CLOUD` | AUDIO | Attempt cloud stream (same as normal playback). Auto-play. On any cloud error → Toast + clear state + main screen. |
  | `CLOUD` | VIDEO | Attempt cloud stream. **Start in PAUSED state**. On any cloud error → Toast + clear state + main screen. |

  The `isPlaying` field stored in `ResumeState` is **overridden** for network VIDEO and all CLOUD VIDEO by forcing `isPlaying = false` at the routing step, regardless of what was saved.

- **Error Handling:** If any unexpected error occurs during resumption (bad state data, navigation crash, or cloud client exception), catch it, log via `Timber.e()`, clear state, and fall back to the standard main screen silently. Do not re-throw or crash.
- **Scroll Position:** No exact pixel scroll restoration is needed in the Browser view; relying on the existing feature that centers the list on the currently playing file is sufficient.

## 3. Implementation Steps (Developer Prompts)

This plan is broken down into small, iterative steps. Every step acts as a standalone prompt for a developer/agent, representing a minimal slice of work that must be completed, built, and committed cleanly. Note: All interactions must conform to `dev/AGENT_WORKFLOW.md`.

---

### Step 1: Data Layer (Resume State Model & Repository)

**Goal:** Provide the underlying database/preference structure to hold the resume context.

**Prompt for Developer:**
"Implement the data layer for the global resume playback feature keeping in mind Clean Architecture and Hilt DI.
1. Create a data class `ResumeState` in the `domain/model` package with the following **exact** fields:
   - `filePath: String` — absolute path of the last active media file (matches the `path` field of `MediaFile`).
   - `resourceId: Long` — ID of the `MediaResource` (`Long`, same type as `MediaResource.id`).
   - `currentFolderPath: String?` — the folder path currently open in BrowseActivity (equivalent to `BrowseState.currentPath`; `null` means resource root). Required to restore subfolder navigation depth.
   - `screenType: ScreenType` — enum with values `BROWSER` (file was playing inline in BrowseActivity) and `PLAYER` (PlayerActivity was open).
   - `sortMode: SortMode` — re-use the existing `SortMode` enum from `domain/model/Models.kt` (line 48). Do **not** create a duplicate.
   - `isPlaying: Boolean` — true if media was actively playing at the time of save.
   - `isSlideshowEnabled: Boolean` — true if slideshow mode was active in PlayerActivity at the time of save.
   - `mediaType: MediaType` — re-use the existing `MediaType` enum from `domain/model/Models.kt`.
   - `savedAt: Long` — standard Unix timestamp representing when the state was saved (for TTL validation).
2. Create `ResumeStateDataSource` using **SharedPreferences** (consistent with the existing `SettingsRepositoryImpl`). Store fields as individual key-value pairs. Use a dedicated preferences file name, e.g., `"resume_state_prefs"`.
3. Create `ResumeStateRepository` interface and implementation with methods: `saveState(state: ResumeState)`, `getState(): ResumeState?` (suspend, single read — not a Flow), and `clearState()`.
4. Create the corresponding Domain UseCases: `SaveResumeStateUseCase`, `GetResumeStateUseCase`, `ClearResumeStateUseCase`.
5. Provide all necessary Hilt bindings in the DI layer (`di/` module).

*Verification:* Run `.\dev\build-with-version.ps1` to ensure the project compiles successfully. Resolve any lint warnings introduced in touched files. Commit changes using `.\scripts\add_to_dev_log.ps1`."

---

### Step 2: Clear State Integration (Explicit Exits & Navigation)

**Goal:** Ensure the app abandons the idea of resuming if the user explicitly exits or moves to a new folder.

**Prompt for Developer:**
"Integrate the `ClearResumeStateUseCase` into the UI layer to reset the resume state upon explicit user actions. Use `Timber` for extensive logging of these events.
1. Inject `ClearResumeStateUseCase` into `PlayerViewModel` and `BrowseViewModel`.
2. Trigger `clearState()` when the user **explicitly closes** the Player or Browser:
   - In `PlayerActivity`: inside the existing `doFinish()` method (called by both `onBackPressed` and the close button), before `finish()`.
   - In `BrowseActivity`: inside `handleOnBackPressed()` at the point where `canNavigateUp()` returns `false` (i.e., the activity is actually finishing, not just going up a subfolder).
3. Trigger `clearState()` in `BrowseViewModel` when the user **opens a different resource** — specifically, when `loadResource()` is called with a `resourceId` that differs from the currently loaded `state.value.resource?.id`. Navigating into subfolders of the **same** resource (`navigateToFolder()`) must **not** trigger this.
4. Do **not** trigger `clearState()` on `onPause`, `onStop`, or Home button press — these are backgrounding events, not explicit exits.

*Verification:* Do a fast debug build. Verify via Logcat (Timber) that the state clearing logic fires exactly during standard exits and NOT during basic backgrounding (like pressing the Home button). Add changelog script execution."

---

### Step 3: Save State Integration (Playback Tracking)

**Goal:** Actively persist the playback context while media runs, integrating with an existing timer if possible.

**Prompt for Developer:**
"Integrate the `SaveResumeStateUseCase` into the media playback flow to periodically store the current context alongside position tracking.
1. **Locate existing save points:** `saveCurrentPlaybackPosition()` in `PlayerActivity` (line ~3148) and the position-save timer in `VideoPlayerManager` (line ~1350). Hook into these — do not create a separate timer.
2. **For PlayerActivity (PLAYER screen type):** When saving position, also call `SaveResumeStateUseCase` with:
   - `filePath` = `viewModel.state.value.currentFile?.path`
   - `resourceId` = resource ID passed in the launch intent
   - `currentFolderPath` = `null` (PlayerActivity does not track subfolder state)
   - `screenType` = `ScreenType.PLAYER`
   - `sortMode` = sort mode passed in the launch intent (store it in `PlayerViewModel.state` if not already there)
   - `isPlaying` = `!viewModel.state.value.isPaused`
   - `isSlideshowEnabled` = `viewModel.state.value.isSlideShowActive` (use ViewModel state — `SlideshowController.isActive` is private)
   - `mediaType` = `viewModel.state.value.currentFile?.type` (must be `AUDIO` or `VIDEO`; skip save if other type)
   - `savedAt` = `System.currentTimeMillis()`
3. **For BrowseActivity (BROWSER screen type):** When the inline audio player saves position (hook into `AudioPlaybackService` or `BrowseViewModel`'s inline player state), call `SaveResumeStateUseCase` with:
   - `filePath` = `_inlinePlayerState.value.playingPath`
   - `resourceId` = `state.value.resource?.id`
   - `currentFolderPath` = `state.value.currentPath`
   - `screenType` = `ScreenType.BROWSER`
   - `sortMode` = `state.value.sortMode`
   - `isPlaying` = `_inlinePlayerState.value.status == PlaybackStatus.PLAYING` (`status` is a `PlaybackStatus` enum `{ IDLE, PLAYING, PAUSED }` in `BrowseViewModel.kt:99`; there is no `isPlaying: Boolean` field)
   - `isSlideshowEnabled` = `false`
   - `mediaType` = `MediaType.AUDIO` (inline browser player only plays audio)
   - `savedAt` = `System.currentTimeMillis()`
4. Also trigger save immediately on: Play/Pause toggle and track change (new current file). Do not save for document types (PDF, EPUB, Text).
5. Never call `SaveResumeStateUseCase` from Activity classes directly — inject and call from ViewModel or Service.

*Verification:* Compile the app. Launch a file, check Logcat to confirm `SaveResumeStateUseCase` is periodically hit with valid domain objects. Test both BROWSER and PLAYER triggers. Run `add_to_dev_log.ps1`."

---

### Step 4: Routing & Error Handling (App Startup Entry Point)

**Goal:** Intercept the app launch, inspect the saved state, and route the user to the correct screen instantly or fallback safely if unavailable.

**Prompt for Developer:**
"Implement the startup routing logic to read the `ResumeState` and navigate the user accordingly, bypassing the main menu if applicable.
1. **Intent detection in `MainActivity.onCreate()`** (after the welcome check, before any other routing): inspect the launching `Intent`. Skip resume and proceed normally if **any** of the following is true:
   - `intent?.action == ACTION_START_SLIDESHOW` (slideshow widget)
   - `intent?.action != Intent.ACTION_MAIN` (external file open or other intent)
   - `AudioPlaybackService` is currently running — check via a static flag. `ActivityManager.getRunningServices()` is deprecated since API 26 and unreliable. **Required implementation:** add `@Volatile var isRunning: Boolean = false` to `AudioPlaybackService.companion object`; set it to `true` in `onCreate()` and to `false` in `onDestroy()`. Then check `AudioPlaybackService.isRunning` in `MainActivity`.
2. Otherwise (standard launcher icon start with killed process): call `GetResumeStateUseCase`. If state is `null` **or `savedAt` is older than TTL limit**, proceed normally.
3. If state is valid:
   a. **Verify standard permissions** (e.g., READ_EXTERNAL_STORAGE or exact Android 13/14 counterparts). If missing, call `clearState()` and proceed to normal flow (where permissions will be requested).
   b. **Show a blocking loading UI component** (e.g., a full-screen layout on `MainActivity` over the background) so the user doesn't see a "flicker" of the main menu while network checks happen.
   c. Launch a coroutine with a **5-second timeout** (`withTimeout(5000L)`) to verify availability:
      - Local resource: `File(state.filePath).exists()`
      - Network/Cloud resource: attempt a lightweight ping/stat using the existing client (do not download the file).
   c. If check fails or times out: call `clearState()`, dismiss loading indicator, show `Toast(R.string.resume_unavailable)` (add the string resource), and proceed to normal main screen.
   d. If check succeeds: dismiss loading indicator. Before navigating, **determine the effective `isPlaying` flag** using the resource/media type matrix from Section 2:
      - If `resource.type` is `SMB`/`FTP`/`SFTP` and `state.mediaType == VIDEO` → force `effectiveIsPlaying = false`.
      - If `resource.type` is `CLOUD` and `state.mediaType == VIDEO` → force `effectiveIsPlaying = false`.
      - Otherwise → use `state.isPlaying` as-is.
      
      Then navigate:
      - If `state.screenType == PLAYER`: call `PlayerActivity.createIntent(context, resourceId, initialFilePath = state.filePath, isPlaying = effectiveIsPlaying, isSlideshowEnabled = state.isSlideshowEnabled)`. Add the new `isPlaying` and `isSlideshowEnabled` extras to `PlayerActivity.createIntent()` companion method. Do **not** add a `sortMode` extra — `PlayerViewModel` loads `sortMode` automatically from the `MediaResource` object (via `resource.sortMode`) when `loadMediaFiles()` runs; passing it via intent would be redundant.
      - If `state.screenType == BROWSER`: call `BrowseActivity.createIntent(context, resourceId, initialFolderPath = state.currentFolderPath, initialFilePath = state.filePath, isPlaying = effectiveIsPlaying)`. Add the new `initialFolderPath` and `initialFilePath` extras to `BrowseActivity.createIntent()` companion method.
      
      ***CRITICAL PLAYLIST REBUILD***: When routing to Player or Browser, the target activity **MUST** reconstruct the entire playlist/queue containing all media files of that folder using `state.sortMode`. It is NOT enough to just load the single file. Next/Prev buttons must work immediately!

4. **In `PlayerActivity`:** Read `isPlaying` and `isSlideshowEnabled` extras. Store them as ViewModel arguments (via `SavedStateHandle`). If `isPlaying == true`, start playback automatically (same as normal open). If `isSlideshowEnabled == true`, **do NOT call `slideshowController.startSlideshow()` in `onCreate()` directly** — the slideshow interval is only available after `loadMediaFiles()` completes. Instead, set a one-shot flag and activate the slideshow inside the `state` observer at the moment `state.files.isNotEmpty()` first becomes true: call `slideshowController.startSlideshow((state.slideShowInterval / 1000).toInt())`.
5. **In `BrowseActivity`:** Read `initialFolderPath` extra and navigate to that subfolder on load (call `viewModel.navigateToFolder(initialFolderPath)` if non-null). Read `initialFilePath` and `isPlaying` extras to resume the inline audio player at that file. Ensure the item list is populated before attempting playback to fulfill the playlist rule.
6. Update markdown docs: `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

*Verification:* Run Android unit/integration tests if applicable. Perform manual tests: Kill app process while playing, restart by icon → verifies resume. Kill Wi-Fi, restart → verifies Toast + fallback. Start app via slideshow widget while state exists → verifies widget is not affected. Clear any lint warnings in touched files, then build release."

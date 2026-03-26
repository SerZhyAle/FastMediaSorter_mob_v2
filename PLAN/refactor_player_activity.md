# Refactoring Plan: PlayerActivity.kt (3576 lines → ~600 lines)

## Current State

`PlayerActivity.kt` — **3576 lines**, the single largest file in the project (limit is 1000).
It already delegates heavily to helpers, but two structural problems keep it bloated:

1. **`initializeManagers()` is ~1200 lines** — every manager is instantiated with a massive inline anonymous callback object that re-routes calls back into the Activity.
2. **Several complex methods (translate, share, dialogs) live directly in the Activity** instead of in dedicated managers.

### What Already Exists (do not re-create)

```
ui/player/
  helpers/         ~50 *Manager.kt files
  callbacks/
    PlayerPlaybackCallbackImpl.kt   ← the pattern to replicate
    PlayerGestureCallbackImpl.kt
  PlayerDialogHelper.kt
  FileOperationsHandler.kt
  CommandPanelController.kt
  PlayerViewModel.kt
```

---

## Root Cause Analysis

### Problem 1 — Anonymous callbacks inside `initializeManagers()`

Each manager is constructed like:
```kotlin
someManager = SomeManager(
    ...,
    callback = object : SomeManager.Callback {
        override fun doSomething() {
            this@PlayerActivity.doSomething()
        }
        // ... 50–200 more lines
    }
)
```
These anonymous objects exist only to forward calls back to the Activity or its other managers.
They add ~900 lines to `initializeManagers()` alone without adding logic.

### Problem 2 — Complex methods living in the Activity

| Method | Lines | Should live in |
|---|---|---|
| `translateCurrentImage()` | ~170 | `PlayerImageTranslationManager` |
| `openInExternalPlayer()` | ~60 | `PlayerShareManager` |
| `shareCurrentFile()` + `shareCurrentFileToGoogleLens()` + `shareFileToGoogleLens()` + `extractBitmapFromDrawable()` | ~130 | `PlayerShareManager` |
| `showAudioTrackDialog()` + `showSubtitleTrackDialog()` | ~80 | `PlayerDialogHelper` |
| `stopTranslation()` | ~30 | `PlayerImageTranslationManager` |

### Problem 3 — `initializeManagers()` is monolithic

Even after extracting callbacks it is a single 200+ line method.
Should be split into focused private sub-methods.

---

## Target State

After refactoring:
- `PlayerActivity.kt` → **≤700 lines** (wiring + lifecycle, zero business logic)
- Each new file ≤500 lines

---

## Step-by-Step Plan

### Step 1 — Extract `PlayerCommandPanelCallbackImpl` *(~700 → ~250 lines saved)*

**Why first:** This is the single largest anonymous callback block (~700 lines, lines ~959–1660 in `initializeManagers()`).

**New file:** `ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`

```kotlin
class PlayerCommandPanelCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val navigationManager: PlayerNavigationManager,
    // ... other needed refs passed in constructor
) : CommandPanelController.CommandPanelCallback {

    override fun onBackClicked() = activity.exitPlayerWithAudioCheck()
    override fun onDeleteClicked() = activity.deleteCurrentFile()
    override fun onSlideshowClicked() { /* extracted logic */ }
    // ... all ~40 override methods
}
```

**In `initializeManagers()`**, replace the inline object with:
```kotlin
commandPanelController = CommandPanelController(
    ...,
    callback = PlayerCommandPanelCallbackImpl(
        activity = this,
        viewModel = viewModel,
        navigationManager = navigationManager,
        // ...
    )
)
```

**Notes:**
- The callback impl file accesses `activity.someField` or `activity.someMethod()` — this is acceptable for a callback impl class.
- Methods currently delegated to `binding.btnXxx.performClick()` stay as-is.
- Slideshow toggle logic (lines 1022–1053) has real logic — keep it in the impl, not in the Activity.

---

### Step 2 — Extract `PlayerKeyboardCallbackImpl` *(~120 lines saved)*

**New file:** `ui/player/callbacks/PlayerKeyboardCallbackImpl.kt`
(Pattern already exists for `PlayerPlaybackCallbackImpl`)

```kotlin
class PlayerKeyboardCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val navigationManagerProvider: () -> PlayerNavigationManager,
    // pdfManager, epubManager, textManager, videoManager all via lazy providers
) : PlayerKeyboardHandler.PlayerKeyboardCallback {
    override fun onDeleteFile() = activity.deleteCurrentFile()
    override fun onExitPlayer() = activity.exitPlayerWithAudioCheck()
    override fun onPdfNextPage() { activity._pdfViewerManager?.showNextPage() }
    // ... etc.
}
```

**In `initializeManagers()`:**
```kotlin
keyboardHandler = PlayerKeyboardHandler(
    viewModel = viewModel,
    callback = PlayerKeyboardCallbackImpl(this, viewModel, { navigationManager }, ...)
)
```

---

### Step 3 — Extract `PlayerUiStateCoordinatorCallbackImpl` *(~120 lines saved)*

**New file:** `ui/player/callbacks/PlayerUiStateCoordinatorCallbackImpl.kt`

```kotlin
class PlayerUiStateCoordinatorCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val binding: ActivityPlayerUnifiedBinding,
    // ...
) : PlayerUiStateCoordinator.Callback {
    override fun displayImage(path: String) { activity.stopVideoPlayback(); activity.displayImage(path) }
    override fun isImageVisible(): Boolean { /* extracted logic */ }
    // ... ~25 overrides
}
```

---

### Step 4 — Extract `PlayerMediaLoaderCallbackImpl` *(~80 lines saved)*

**New file:** `ui/player/callbacks/PlayerMediaLoaderCallbackImpl.kt`

Covers the `PlayerMediaLoaderManager.Callback` anonymous object inside `initializeManagers()` (~lines 1196–1260).

---

### Step 5 — Extract `PlayerGestureManagerCallbackImpl` *(~80 lines saved)*

**New file:** `ui/player/callbacks/PlayerGestureManagerCallbackImpl.kt`

Covers the `TouchZoneGestureManager.Callback` anonymous object (~lines 1334–1407).

---

### Step 6 — Extract `PlayerTranslationButtonCallbackImpl` *(~60 lines saved)*

**New file:** `ui/player/callbacks/PlayerTranslationButtonCallbackImpl.kt`

Covers the `TranslationButtonManager.Callback` anonymous object (~lines 1408–1460).

---

### Step 7 — Create `PlayerImageTranslationManager` *(~200 lines extracted)*

**New file:** `ui/player/helpers/PlayerImageTranslationManager.kt`

Move out of `PlayerActivity`:
- `translateCurrentImage()` (~170 lines, currently at ~line 2966)
- `stopTranslation()` (~30 lines, currently at ~line 2944)
- `translationJob: Job?` field

```kotlin
class PlayerImageTranslationManager(
    private val activity: PlayerActivity,
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: Callback
) {
    private var translationJob: Job? = null

    fun translateCurrentImage() { /* full body */ }
    fun stopTranslation() { /* full body */ }

    interface Callback {
        fun getCurrentFile(): MediaFile?
        fun getSafeViews(): PlayerBindingSafeViews
        fun showError(msg: String)
    }
}
```

`translateCurrentImage()` currently references `translationManager` for OCR/translation — pass that as constructor arg.

---

### Step 8 — Create `PlayerShareManager` *(~200 lines extracted)*

**New file:** `ui/player/helpers/PlayerShareManager.kt`

Move out of `PlayerActivity`:
- `shareCurrentFile()` (~30 lines, ~line 2887)
- `extractBitmapFromDrawable()` (~50 lines, ~line 2897)
- `openInExternalPlayer()` (~60 lines, ~line 2717)
- `shareCurrentFileToGoogleLens()` (~25 lines, ~line 3250)
- `shareFileToGoogleLens()` (~50 lines, ~line 3270)
- `setupGoogleLensButtons()` (~8 lines, ~line 3245)

```kotlin
class PlayerShareManager(
    private val activity: PlayerActivity,
    private val binding: ActivityPlayerUnifiedBinding,
    private val callback: Callback
) {
    fun shareCurrentFile() { /* ... */ }
    fun openInExternalPlayer(path: String) { /* ... */ }
    fun shareCurrentFileToGoogleLens() { /* ... */ }
    fun shareFileToGoogleLens(file: File) { /* ... */ }
    fun setupGoogleLensButtons() { /* ... */ }

    interface Callback {
        fun getCurrentFile(): MediaFile?
        fun showError(msg: String)
    }
}
```

Initialize in `initializeManagers()`, call from `CommandPanelCallbackImpl`.

---

### Step 9 — Move audio/video dialog methods to `PlayerDialogHelper` *(~80 lines extracted)*

Move these two methods out of `PlayerActivity` into `PlayerDialogHelper`:
- `showAudioTrackDialog()` (~49 lines, ~line 1892)
- `showSubtitleTrackDialog()` (~32 lines, ~line 1943)

Both only use `viewModel` and `binding` which `PlayerDialogHelper` already has access to.

---

### Step 10 — Split `initializeManagers()` into sub-methods *(zero line savings, massive readability win)*

After Steps 1–9, `initializeManagers()` should be ~300 lines of pure wiring.
Split it into focused private sub-methods called from `initializeManagers()`:

```kotlin
private fun initializeManagers() {
    initBackgroundMedia()          // BackgroundMusicManager + AudioBackgroundPhotosManager
    initCloudAuth()                // BrowseCloudAuthManager
    initNavigationAndGestures()    // NavigationManager, SlideshowController, GestureManagers
    initKeyboardAndInput()         // PlayerKeyboardHandler, TouchZoneGestureManager
    initFileOperations()           // FileOperationsHandler, DestinationButtonsManager
    initAudioManagers()            // AudioServiceController, SleepTimerManager, PipManager
    initDocumentManagers()         // NetworkFileManager, ImageLoadingManager, MediaLoaderManager
    initUiCoordinators()           // UiStateCoordinator, UndoOperationManager, CommandPanelController
    initViewerManagers()           // DialogAndUiStateManager, AudioSlideshowPhotoModeManager
    initSetupManagers()            // ControlsSetupManager, GestureSetupManager
}
```

Each sub-method stays ≤80 lines.

---

## Line Budget Projection

| Step | What | Lines removed from Activity |
|---|---|---|
| 1 | CommandPanelCallbackImpl | ~650 |
| 2 | KeyboardCallbackImpl | ~120 |
| 3 | UiStateCoordinatorCallbackImpl | ~120 |
| 4 | MediaLoaderCallbackImpl | ~80 |
| 5 | GestureManagerCallbackImpl | ~80 |
| 6 | TranslationButtonCallbackImpl | ~60 |
| 7 | ImageTranslationManager | ~200 |
| 8 | ShareManager | ~200 |
| 9 | Dialog methods → DialogHelper | ~80 |
| 10 | Split initializeManagers | 0 (structural) |
| **Total** | | **~1590 lines removed** |

**Resulting Activity size: ~3576 − 1590 ≈ 1986 lines**

That is still over 1000 lines. To get under 1000:

### Additional Steps (Phase 2)

#### Step 11 — Extract `observeViewModel()` + state logic → `PlayerStateObserver`
`observeViewModel()` + helper flows (~70 lines) → `PlayerStateObserver.kt`.

#### Step 12 — Extract `showError()` + `handleEvent()` → `PlayerEventHandler`
`showError()` (~65 lines) + `showUnsupportedFormatError()` (~50 lines) + `handleEvent()` (~35 lines) + `showCloudAuthenticationError()` (~20 lines) → `PlayerEventHandler.kt` (~170 lines).

#### Step 13 — Extract Activity Result launchers → `PlayerActivityResultManager`
`googleSignInLauncher`, `batchDeletePermissionLauncher`, `deletePermissionLauncher` + their callback logic (~80 lines) → `PlayerActivityResultManager.kt`.
Note: `registerForActivityResult()` must be called during Activity initialization (before `onCreate` completes); use `ComponentActivity` reference pattern.

#### Step 14 — Extract `showReaderSettingsDialog()` + `showSleepTimerDialog()` + `showEncodingDialog()` → into `PlayerDialogHelper`
~80 lines moved.

After Phase 2: **~3576 − 1590 − 170 − 170 − 80 − 80 ≈ 1486 lines**

Still over. Phase 3 would target the remaining large blocks:
- `updateSystemBarsForPlayer()` + panel toggle logic (~80 lines)
- `saveCurrentPlaybackPosition()` (~30 lines) → `PlayerLifecycleManager`
- Various `update*()` UI methods → consolidate into `PlayerDialogAndUiStateManager`

Realistically **~800–1000 lines** is achievable while keeping the Activity readable.

---

## Implementation Order (priority)

1. **Steps 1–6** (callback extraction) — pure mechanical extraction, zero logic change, highest line savings.
2. **Steps 7–9** (logic extraction) — require creating new managers with proper callback interfaces.
3. **Step 10** (structural cleanup) — do after 1–9 to see the true shape.
4. **Steps 11–14** (Phase 2) — evaluate after Phase 1 to decide if still needed.

---

## Rules to Follow During Refactoring

- Each new file ≤ 500 lines.
- Callback impl classes go in `ui/player/callbacks/`.
- New manager classes go in `ui/player/helpers/`.
- Naming: `NounVerbManager`, `PlayerNounCallbackImpl`.
- No `Log.d` — use `Timber.d`.
- After each file change: run `.\scripts\add_to_dev_log.ps1`.
- After all changes: run `.\gradlew.bat assembleStandardDebug` to verify build.
- Create `temp/<timestamp>_PlayerActivity_backup.kt` before modifying (file is >500 lines).

---

## Risks

| Risk | Mitigation |
|---|---|
| Callback impl breaks `internal` visibility of Activity members | Keep Activity members `internal` so callback impls (same package) can access them |
| Circular references (callback → activity → manager → callback) | Pass only what is needed; use provider lambdas for lazy managers |
| `registerForActivityResult` must be called before `onCreate` | `PlayerActivityResultManager` must be initialized as a field, not inside `onCreate` |
| Translation job cancellation on destroy | `PlayerImageTranslationManager` must expose `cancel()` and be called from `onDestroy` |
| **Functional Loss: Navigation** | Large number of dependencies in `navigationManager` and `PlayerNavigationManager` must be carefully wired to ensure next/prev logic (including circular and random modes) remains intact. |
| **Functional Loss: State Sync** | `viewModel.state` is used by almost all managers. Ensure `distinctUntilChanged` observers don't miss updates after extraction. |
| **Functional Loss: Lifecycle & PiP** | Video playback position saving and PiP transitions rely on `Activity` lifecycle. Managers must implement `LifecycleObserver` or receive lifecycle events. |
| **Memory Leaks** | Callback implementations holding a hard reference to `PlayerActivity` must be lifecycle-aware or cleared in `onDestroy`. |
| **Resource Leaks (OCR/Translate)** | `bitmap` extraction and `OCR` jobs can be memory-intensive. Ensure intermediate bitmaps are recycled and jobs are cancelled. |

## Mandatory Safety Protocols

### 1. Backups
- **CRITICAL**: Before starting any step that modifies `PlayerActivity.kt`, create a timestamped backup in `temp/`.
- **Command**: `cp p:\ANDROID\FastMediaSorter_mob_v2\app_v2\src\main\java\com\sza\fastmediasorter\ui\player\PlayerActivity.kt p:\ANDROID\FastMediaSorter_mob_v2\temp\$(Get-Date -Format "yyyyMMdd_HHmmss")_PlayerActivity.kt.backup`

### 2. Incremental Builds
- **RULE**: Build the project **after every single step**.
- Never batch multiple extraction steps without a successful intermediate build.
- **Command**: `.\gradlew.bat assembleStandardDebug` (or use `./build-debug.PS1` for speed).

### 3. Change Logging
- Run `.\scripts\add_to_dev_log.ps1` after every successful step.

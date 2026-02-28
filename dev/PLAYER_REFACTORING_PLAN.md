# PlayerActivity Refactoring Plan
**Date**: 2026-02-28  
**Baseline**: 3363 lines, 102 methods, 88 fields, 18 anonymous callbacks  
**Target**: ≤1200 lines (thin Activity shell — lifecycle, overrides, delegation only)  
**Constraint**: backup before each file >500 lines, BUILD SUCCESSFUL after each step

---

## Current State (post-Steps 1–3)

| Metric | Value |
|--------|-------|
| Lines | 3363 |
| Methods | 102 |
| Biggest method | `initializeManagers()` — **1125 lines** (L457–L1583) |
| Helpers count | 55 files / ~14 700 lines |
| EASY extractable | ~55 methods |
| MEDIUM extractable | ~25 methods |
| HARD (lifecycle/registerForResult) | ~22 methods |

---

## Phase 1 — High-Impact Extractions (target: 3363 → ~1800)

### Step 4. Split `initializeManagers()` into domain-specific init functions
**Impact: ~1125 lines → 8 short calls ≈ 30 lines = −1095**  
**Risk: HIGH (touches all wiring)**

`initializeManagers()` is 33% of the entire file. Split it into:

| Sub-function | Lines | Content |
|---|---|---|
| `initCloudAndAuth()` | ~40 | cloudAuthManager, playlistCreator |
| `initGesturesAndCallbacks()` | ~130 | gestureHelper, PlayerGestureCallbackImpl, playerGestureCallback |
| `initFileOperations()` | ~110 | undoOperationManager, fileOperationsHandler, destinationButtonsManager |
| `initCommandPanel()` | ~100 | commandPanelController + its Callback (90 lines) |
| `initMediaPipeline()` | ~180 | playerSettingsManager, mediaDisplayCoordinator, imageLoadingManager, mediaLoaderManager, navigationManager, networkFileManager |
| `initOcrAndTranslation()` | ~180 | TesseractManager, imageOcrManager, touchZoneGestureManager, translationButtonManager, exoPlayerControlsManager, searchControlsManager |
| `initAudioSlideshow()` | ~70 | audioSlideshowPhotoModeManager, dialogAndUiStateManager wiring |
| `initSetupManagers()` | ~30 | controlsSetupManager, gestureSetupManager |

Each stays in PlayerActivity as a `private fun` first (no new files). Lines stay the same but readability improves. The **real** reduction comes when each sub-function's anonymous callbacks are moved to named classes (see Step 5).

### Step 5. Extract anonymous callback objects into named classes
**Impact: ~−800 lines**

18 anonymous `object : Interface { ... }` blocks inside `initializeManagers()` total ~800 lines. Extract each into a named inner class or top-level class in `helpers/`:

| Callback class (new) | Interface | ~Lines | From sub-init |
|---|---|---|---|
| `PlayerGestureCallbackImpl.kt` | PlayerGestureHelper.Callback | 113 | EXISTS already (L527–640) — move remaining wiring |
| `PlayerUndoCallback.kt` | UndoOperationManager.Callback | 75 | initFileOperations |
| `PlayerFileOpsCallback.kt` | FileOperationsHandler.Callback | 43 | initFileOperations |
| `PlayerDestinationCallback.kt` | DestinationButtonsManager.Callback | 45 | initFileOperations |
| `PlayerCommandPanelCallback.kt` | CommandPanelController.Callback | 86 | initCommandPanel |
| `PlayerNavigationCallback.kt` | NavigationManager.NavigationCallback | 60 | initMediaPipeline |
| `PlayerTouchZoneCallback.kt` | TouchZoneGestureManager.Callback | 76 | initOcrAndTranslation |
| `PlayerTranslationBtnCallback.kt` | TranslationButtonManager.Callback | 41 | initOcrAndTranslation |
| `PlayerExoControlsCallback.kt` | ExoPlayerControlsManager.Callback | 9 | initOcrAndTranslation |
| `PlayerSearchControlsCallback.kt` | SearchControlsManager.Callback | 32 | initOcrAndTranslation |

**Strategy**: Each named callback takes `activity: PlayerActivity` (or an interface) in constructor. Body remains identical but lives in its own file.

### Step 6. Extract `translateCurrentImage()` → `ImageTranslationManager`
**Impact: −170 lines**  
**Risk: LOW**

New class `helpers/ImageTranslationManager.kt`:
- `translateCurrentImage()` (170 lines)
- `extractBitmapFromDrawable()` (45 lines) — static utility
- `stopTranslation()` (15 lines)

Dependencies: `viewModel`, `binding`, `settingsRepository`, `translationManager`, `lifecycleScope`, `imageLoadingManager`. All passable through constructor.

### Step 7. Extract error/unsupported dialogs → `PlayerErrorManager`
**Impact: −105 lines**  
**Risk: MEDIUM** (`showError` uses `deletePermissionLauncher`)

New class `helpers/PlayerErrorManager.kt`:
- `showError()` (50 lines) — pass `deletePermissionLauncher` as constructor param
- `showUnsupportedFormatError()` (40 lines)
- `showCloudAuthenticationError()` (15 lines)

The `deletePermissionLauncher` is an `ActivityResultLauncher<IntentSenderRequest>` — can be passed by reference without issues.

### Step 8. Extract dialogs → existing `PlayerDialogAndUiStateManager`
**Impact: −115 lines**  
**Risk: LOW**

Move these methods:
| Method | ~Lines | Depends on |
|---|---|---|
| `showEncodingDialog()` | 18 | textViewerManager, Context |
| `showReaderSettingsDialog()` | 18 | textViewerManager, Context |
| `showSleepTimerDialog()` | 33 | sleepTimerManager, Context |
| `showAudioTrackDialog()` | 18 | videoPlayerManager, Context |
| `showSubtitleTrackDialog()` | 30 | videoPlayerManager, Context |

`PlayerDialogAndUiStateManager` already has `activity` (Context), add `sleepTimerManager` and lazy `videoPlayerManager` as optional params.

---

## Phase 2 — Medium-Impact Cleanup (target: ~1800 → ~1200)

### Step 9. Extract `observeViewModel()` → `PlayerObserverManager`
**Impact: −60 lines**  
**Risk: MEDIUM** (settings flow, pipManager)

New class `helpers/PlayerObserverManager.kt`:
- `observeViewModel()` — observe state + settings flows
- `observeSettings()` — extracted settings collection

### Step 10. Extract `updateSystemBarsForPlayer()` → merge into `SystemBarsManager`
**Impact: −45 lines**  
**Risk: MEDIUM** (references 5+ managers)

`SystemBarsManager` (153 lines) already exists. Add method `updateForPlayer(...)` that takes current state as params instead of reading Activity fields.

### Step 11. Extract file operations methods
**Impact: −85 lines**  
**Risk: MEDIUM**

Move to existing `FileOperationsHandler` or new `PlayerFileActionsManager`:
- `deleteCurrentFile()` (35 lines)
- `handleDeleteSuccess()` (12 lines)
- `shareCurrentFile()`, `performCopyOperation()`, `performMoveOperation()` (9 lines)
- `shareCurrentFileToGoogleLens()` (18 lines)
- `openInExternalPlayer()` (40 lines) → needs Activity context (startActivity)

### Step 12. Extract `handleEvent()` → `PlayerEventRouter`
**Impact: −35 lines**  
**Risk: LOW**

Simple when-expression routing. New class or add to existing `PlayerUiStateCoordinator`.

### Step 13. Collapse trivial 1-3 line delegators
**Impact: −60 lines (overhead reduction)**  
**Risk: LOW**

~40 methods are pure 1-line delegates:
```kotlin
private fun displayImage(path: String) = mediaLoaderManager.displayImage(path)
```
Options:
- A) Leave as documenting proxies (current)
- B) Make callers invoke managers directly (requires changing callback interfaces)
- C) Group into extension functions

Recommendation: **Leave as-is** for readability but compress formatting where possible.

### Step 14. Extract factory methods
**Impact: −145 lines**  
**Risk: LOW**

Move lazy factory methods to a `PlayerManagerFactory`:
- `createVideoPlayerManager()` (20 lines)
- `createPdfViewerManager()` (60 lines)  
- `createEpubViewerManager()` (30 lines)
- `createTextViewerManager()` (35 lines)

---

## Phase 3 — Structural Polish (target: ~1200 → ≤1000)

### Step 15. Reduce field count
88 fields → group into data classes:
- `PlayerNetworkClients(smb, sftp, ftp, googleDrive, dropbox, oneDrive)`
- `PlayerUseCases(rotate, flip, filter, adjust, extractGif, saveGif, changeGifSpeed, download, searchLyrics, networkImageEdit)`
- `PlayerHandlers(smb, sftp, ftp, cloud)` — file operation handlers

### Step 16. Convert `lateinit var` → constructor injection via factory
Create `PlayerDependencies` holder initialized in `onCreate()`, passed to all managers.

### Step 17. Final cleanup and line count verification
- Remove unused imports
- Verify all methods are either overrides, thin delegates, or lifecycle-bound
- Run lint, tests, build

---

## Execution Priority Matrix

| Step | Impact (lines) | Risk | Effort | Dependencies |
|------|---------------|------|--------|-------------|
| **4** | −0 (readability) | HIGH | 2h | None |
| **5** | **−800** | HIGH | 4h | Step 4 |
| **6** | **−170** | LOW | 1h | None |
| **7** | **−105** | MED | 1h | None |
| **8** | **−115** | LOW | 1h | None |
| **9** | −60 | MED | 1h | None |
| **10** | −45 | MED | 30m | None |
| **11** | −85 | MED | 1.5h | None |
| **12** | −35 | LOW | 30m | None |
| **13** | −60 | LOW | 1h | None |
| **14** | −145 | LOW | 1h | None |
| **15** | −0 (readability) | LOW | 1h | None |
| **16** | −0 (readability) | MED | 2h | Step 15 |
| **17** | ~−30 | LOW | 30m | All |

### Recommended Execution Order

```
Independent (can run in parallel):
  Step 6 (translateCurrentImage)  ─┐
  Step 7 (error dialogs)          ─┤── Phase 1 quick wins (−390 lines, ~3h)
  Step 8 (other dialogs)          ─┘

Sequential (biggest payoff, needs care):  
  Step 4 (split initializeManagers) → Step 5 (extract callbacks) ── −800 lines, ~6h

Then mop-up:
  Steps 9–14 in any order ── −430 lines, ~6h
  Steps 15–17 polish ── readability, ~4h
```

---

## Estimated Final State

| Metric | Before | After |
|--------|--------|-------|
| **Lines** | 3363 | **~1000–1200** |
| **Methods** | 102 | ~40 (overrides + thin delegates) |
| **Fields** | 88 | ~20 (grouped via data classes) |
| **Anonymous callbacks** | 18 | 0 (all named classes) |
| **Helpers** | 55 | ~65–70 |
| **initializeManagers()** | 1125 lines | ~30 (calls to sub-inits) |

---

## Rules

1. **Backup** → `temp/` with timestamp before modifying any file >500 lines
2. **BUILD SUCCESSFUL** after each step — no broken intermediate states
3. **No behavior changes** — pure structural refactoring, no logic modifications
4. **Thin delegates** stay in PlayerActivity (1-liners OK)
5. **Override methods** stay in PlayerActivity (Android contract)
6. Named callback classes get `Player*Callback` prefix for discoverability

# Phase 02 — position-fallback

**Strategic spec:** [`../S0146_bugfix-player-stale-initial-file-path-reload.md`](../S0146_bugfix-player-stale-initial-file-path-reload.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Replace the unconditional `0` fallback in position restoration with `currentIndexBeforeReload.coerceIn(0, size-1)` so the player lands at the nearest-by-order position when the target file is gone.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` | Modified | <= 400 |

---

## Steps

### Step 02.1 — Capture `currentIndexBeforeReload` at the start of `loadMediaFiles()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** start of phase (Phase 01 complete)

**Prompt for developer:**

> In `loadMediaFiles()`, find the existing line that captures the current file path:
>
> ```kotlin
>                 // Save current file path to restore position after reload
>                 val currentFilePath = stateFlow.value.currentFile?.path
> ```
>
> Immediately after it, add:
>
> ```kotlin
>                 // Captured before reload for use as nearest-by-order fallback when the target file is gone.
>                 val currentIndexBeforeReload = stateFlow.value.currentIndex
> ```

**Verification:**

- [x] `currentIndexBeforeReload` is declared right after `currentFilePath` in `loadMediaFiles()`.
- [x] No other logic changed.
- [x] Compile passes.

---

### Step 02.2 — Replace `0` fallbacks with `currentIndexBeforeReload.coerceIn`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> There are two `0` fallbacks in the `safeIndex` computation inside `loadMediaFiles()`. Replace both:
>
> **Fallback A** — inside the branch `if (normalizedCurrentPath != null) { ... } else { ... }` when current file is not found and initial is also not found:
>
> Old:
> ```kotlin
>                             if (normalizedInitialPath != null) {
>                                 val initialFoundIndex = filesWithFavorites.indexOfFirst { normalizePath(it.path) == normalizedInitialPath }
>                                 if (initialFoundIndex >= 0) initialFoundIndex else 0
>                             } else {
>                                 initialIndex.coerceIn(0, filesWithFavorites.size - 1)
>                             }
> ```
> New:
> ```kotlin
>                             if (normalizedInitialPath != null) {
>                                 val initialFoundIndex = filesWithFavorites.indexOfFirst { normalizePath(it.path) == normalizedInitialPath }
>                                 // Neither current nor initial found — land at nearest-by-order position.
>                                 if (initialFoundIndex >= 0) initialFoundIndex else currentIndexBeforeReload.coerceIn(0, filesWithFavorites.size - 1)
>                             } else {
>                                 initialIndex.coerceIn(0, filesWithFavorites.size - 1)
>                             }
> ```
>
> **Fallback B** — inside the branch `else if (normalizedInitialPath != null) { ... }` when there is no current file and initial is not found (the `0` at the very end of that `else` block, after the `ShowMissingFileInfo` event):
>
> Old:
> ```kotlin
>                             Timber.w("File not found by path: $initialFilePath, using index 0")
>                             if (resource.rememberFileList && !initialFileExistsInUnfiltered) {
>                                 val missingName = initialFilePath.substringAfterLast('/').ifBlank { initialFilePath }
>                                 // Auto-clean stale file reference (no blocking dialog)
>                                 MediaFilesCacheManager.removeFile(resource.id, initialFilePath)
>                                 cachedFileListRepository.deleteFile(resource.id, initialFilePath)
>                                 sendEvent(PlayerViewModel.PlayerEvent.ShowMissingFileInfo(missingName))
>                             } else if (resource.rememberFileList && initialFileExistsInUnfiltered) {
>                                 Timber.w("Initial file exists in source list but was filtered out by supportedMediaTypes, skipping missing file dialog")
>                             }
>                             0
> ```
> New:
> ```kotlin
>                             Timber.w("File not found by path: $initialFilePath, falling back to index $currentIndexBeforeReload")
>                             if (resource.rememberFileList && !initialFileExistsInUnfiltered) {
>                                 val missingName = initialFilePath.substringAfterLast('/').ifBlank { initialFilePath }
>                                 // Auto-clean stale file reference (no blocking dialog)
>                                 MediaFilesCacheManager.removeFile(resource.id, initialFilePath)
>                                 cachedFileListRepository.deleteFile(resource.id, initialFilePath)
>                                 sendEvent(PlayerViewModel.PlayerEvent.ShowMissingFileInfo(missingName))
>                             } else if (resource.rememberFileList && initialFileExistsInUnfiltered) {
>                                 Timber.w("Initial file exists in source list but was filtered out by supportedMediaTypes, skipping missing file dialog")
>                             }
>                             currentIndexBeforeReload.coerceIn(0, filesWithFavorites.size - 1)
> ```

**Verification:**

- [x] No unconditional literal `0` (or `0` as expression) remains as the final expression of any `safeIndex` branch.
- [x] The existing `initialIndex.coerceIn(0, filesWithFavorites.size - 1)` branch (when `normalizedCurrentPath == null && normalizedInitialPath == null`) is **unchanged**.
- [x] Fallback A uses `currentIndexBeforeReload.coerceIn(0, filesWithFavorites.size - 1)`.
- [x] Fallback B log message says "falling back to index $currentIndexBeforeReload".
- [x] Compile passes: `./gradlew.bat :app_v2:compileStandardDebugKotlin --console=plain --no-daemon`.
- [x] Full build passes: `./gradlew.bat assembleStandardDebug --console=plain --no-daemon`.

---

## Phase Done Criteria

- [x] Both steps `[x]` done.
- [x] `safeIndex` never evaluates to a bare `0` literal in any reachable branch.
- [x] `currentIndexBeforeReload` is used in both fallback A and fallback B.
- [x] Full `assembleStandardDebug` build passes.

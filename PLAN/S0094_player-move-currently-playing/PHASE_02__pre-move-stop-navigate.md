# Phase 02 — Pre-Move Stop + Optimistic Navigate

**Strategic spec:** [`../S0094_player-move-currently-playing.md`](../S0094_player-move-currently-playing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 6 / 6  *(debounce и guard интегрированы в шаги 02.3/02.4/02.5)*
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Before the background IO transfer starts: stop playback and free the SFTP/network stream, evict the cache entry, and navigate optimistically to the next file. On operation completion, reconcile the file list by path only (no second navigation). On error, leave the file in the list and show the exact failure reason.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`removeMovedFile` uses path-based lookup).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt` | Modified | ≤ 688 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 820 |

> Both files exceed 500 lines — backup both before edits.

---

## Steps

### Step 02.1 — Backup both files before changes

**Files:** `FileOperationsHandler.kt`, `PlayerManagerInitializer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create timestamped backups in `temp/`:
> ```powershell
> $ts = Get-Date -Format 'yyyyMMdd_HHmmss'
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt" "temp/FileOperationsHandler_$ts.kt"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt" "temp/PlayerManagerInitializer_$ts.kt"
> ```

**Verification:**

- `Glob` — `temp/FileOperationsHandler_*.kt` returns at least one match.
- `Glob` — `temp/PlayerManagerInitializer_*.kt` returns at least one match.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 2/2 PASS. Backups: temp/FileOperationsHandler_20260505_151424.kt, temp/PlayerManagerInitializer_20260505_151424.kt.

---

### Step 02.2 — Add `onBeforeMove` to `FileOperationCallback` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a new method to the `FileOperationCallback` interface (inside `FileOperationsHandler`):
>
> ```kotlin
> fun onBeforeMove(movedFilePath: String)
> ```
>
> Place it as the first method in the interface, before `onCopySuccess`. This method is called synchronously on the main thread before the background IO operation starts. It is responsible for stopping playback, evicting the file from cache, and navigating to the next item.

**Verification:**

- `Grep` — `fun onBeforeMove(movedFilePath: String)` present exactly once in `FileOperationsHandler.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 1/1 PASS. `fun onBeforeMove(movedFilePath: String)` at line 60.

---

### Step 02.3 — Add debounce flag; call `onBeforeMove` in `performMove` and `performMoveToPath` before launching coroutine

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> **Debounce guard.** At the class level in `FileOperationsHandler`, add:
> ```kotlin
> @Volatile private var moveInProgress = false
> ```
>
> In `performMove(destination: MediaResource)`:
> 1. Immediately after `val currentFile = callback.getCurrentFile() ?: return`, guard:
>    ```kotlin
>    if (moveInProgress) return
>    moveInProgress = true
>    ```
> 2. Then add:
>    ```kotlin
>    callback.onBeforeMove(currentFile.path)
>    ```
>    This must appear **before** `appScope.launch {` so the stream is freed before IO begins.
>
> Apply the identical `moveInProgress` guard and `callback.onBeforeMove(currentFile.path)` call to `performMoveToPath(destinationPath: String)`.
>
> In both methods' `catch (e: Exception)` block, replace `e.message` with `e.message ?: e.javaClass.simpleName` to prevent a null string appearing in the error toast. Also pass the throwable to `onOperationError` instead of `null`, and reset the flag:
> ```kotlin
> moveInProgress = false
> callback.onOperationError(
>     appCtx.getString(com.sza.fastmediasorter.R.string.error_move_failed, e.message ?: e.javaClass.simpleName),
>     e   // was: null
> )
> ```

**Verification:**

- `Grep` — `moveInProgress = true` present in `FileOperationsHandler.kt` (should match twice — once per method).
- `Grep` — `callback.onBeforeMove(currentFile.path)` present in `FileOperationsHandler.kt` (should match twice — once per method).
- `Grep` — `e.javaClass.simpleName` present in `FileOperationsHandler.kt`.
- `Grep` — `moveInProgress = false` present inside `catch` blocks in `FileOperationsHandler.kt`.
- `Grep` — `Log\.d(` returns zero hits in `FileOperationsHandler.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 5/5 PASS. moveInProgress×2, onBeforeMove×2, javaClass.simpleName×2, moveInProgress=false×2 (catch), Log.d=0. Dev log recorded.

---

### Step 02.4 — Implement `onBeforeMove` in `PlayerManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `initFileOps()`, inside the anonymous `FileOperationCallback` object, add the `onBeforeMove` override as the first method:
>
> ```kotlin
> override fun onBeforeMove(movedFilePath: String) {
>     if (movedFilePath != activity.viewModel.state.value.currentFile?.path) return
>     activity.stopVideoPlayback()
>     activity.viewModel.state.value.resource?.let { resource ->
>         MediaFilesCacheManager.removeFile(resource.id, movedFilePath)
>     }
>     activity.navigationManager.navigateNextAfterOperation("Pre-move: stop and optimistic advance")
> }
> ```
>
> The guard `if (movedFilePath != currentFile?.path) return` ensures that if the user somehow triggers a move for a file that is not currently loaded (edge case: move in Browse while player shows a different file), the playback is not disturbed. `stopVideoPlayback()` releases the ExoPlayer instance and closes any open SFTP/network streams synchronously before the IO operation starts.

**Verification:**

- `Grep` — `override fun onBeforeMove(movedFilePath: String)` present in `PlayerManagerInitializer.kt`.
- `Grep` — `activity.stopVideoPlayback()` present in the `onBeforeMove` body in `PlayerManagerInitializer.kt`.
- `Grep` — `navigateNextAfterOperation("Pre-move` present in `PlayerManagerInitializer.kt`.
- `Grep` — `!= activity.viewModel.state.value.currentFile?.path` present in `PlayerManagerInitializer.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 4/4 PASS. onBeforeMove override, stopVideoPlayback, navigateNextAfterOperation, path guard — all present.

---

### Step 02.5 — Simplify `onMoveSuccess` and `onMoveToPathSuccess`: remove redundant navigation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> In `initFileOps()`, replace the bodies of `onMoveSuccess` and `onMoveToPathSuccess` as follows.
>
> **`onMoveSuccess`** — new body:
> ```kotlin
> override fun onMoveSuccess(
>     destination: com.sza.fastmediasorter.domain.model.MediaResource,
>     movedFilePath: String,
>     @Suppress("UNUSED_PARAMETER") goToNext: Boolean
> ) {
>     fileOperationsHandler.resetMoveInProgress()
>     activity.lifecycleManager.trackModifiedFile(movedFilePath)
>     val hasRemainingFiles = activity.viewModel.removeMovedFile(movedFilePath)
>     if (!hasRemainingFiles) activity.finish()
>     // Navigation already performed pre-move in onBeforeMove — do not navigate again.
> }
> ```
>
> **`onMoveToPathSuccess`** — new body (identical structure):
> ```kotlin
> override fun onMoveToPathSuccess(
>     destinationPath: String,
>     movedFilePath: String,
>     @Suppress("UNUSED_PARAMETER") goToNext: Boolean
> ) {
>     fileOperationsHandler.resetMoveInProgress()
>     activity.lifecycleManager.trackModifiedFile(movedFilePath)
>     val hasRemainingFiles = activity.viewModel.removeMovedFile(movedFilePath)
>     if (!hasRemainingFiles) activity.finish()
>     // Navigation already performed pre-move in onBeforeMove — do not navigate again.
> }
> ```
>
> Also expose a `fun resetMoveInProgress()` in `FileOperationsHandler` that sets `moveInProgress = false` — call it from both success callbacks above so the debounce guard is released on successful completion.
>
> Key changes vs. old code:
> - `MediaFilesCacheManager.removeFile(...)` removed (now in `onBeforeMove`).
> - `navigationManager.navigateNextAfterOperation(...)` removed (already called pre-move).
> - `goToNext` parameter is now unused — annotated `@Suppress("UNUSED_PARAMETER")` to silence lint.
> - `resetMoveInProgress()` called to re-enable the button after transfer completes.

**Verification:**

- `Grep` — `navigateNextAfterOperation` appears **zero** times inside `onMoveSuccess` body in `PlayerManagerInitializer.kt`.
- `Grep` — `navigateNextAfterOperation` appears **zero** times inside `onMoveToPathSuccess` body in `PlayerManagerInitializer.kt`.
- `Grep` — `activity.viewModel.removeMovedFile(movedFilePath)` appears in both methods.
- `Grep` — `MediaFilesCacheManager.removeFile` appears **zero** times in `onMoveSuccess` and `onMoveToPathSuccess`.
- `Grep` — `@Suppress("UNUSED_PARAMETER")` present twice in `PlayerManagerInitializer.kt` (once per method).
- `Grep` — `resetMoveInProgress()` present in `FileOperationsHandler.kt` (method declaration) and called at least twice in `PlayerManagerInitializer.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 6/6 PASS. navigateNext=0 in move callbacks, removeMovedFile×2, @Suppress×2, resetMoveInProgress×2, MediaFilesCacheManager only in onBeforeMove. Dev log recorded.

---

### Step 02.6 — Dev log entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 02.5

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt" "S0094 Phase 02" "Add onBeforeMove callback; call before IO; fix null exception message in error toast"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt" "S0094 Phase 02" "Implement onBeforeMove (stop+navigate); remove redundant navigation from onMoveSuccess/onMoveToPathSuccess"
> ```

**Verification:**

- `Grep` — two lines containing `S0094 Phase 02` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 1/1 PASS. Two `S0094 Phase 02` entries in CHANGELOG.md.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added (Step 02.6).
- [ ] Catalog regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (public callback interface changed).

---

## Handoff Notes to Next Phase

After Phase 02: move operations stop playback and navigate the user immediately; the background IO does not race with an open stream; the file list is reconciled by path after transfer completes. Phase 03 is independent and can run in parallel.

---

## Rollback Plan

Revert phase commit(s). The `onBeforeMove` interface addition is source-compatible — all callers in the module implement it. No data migration.

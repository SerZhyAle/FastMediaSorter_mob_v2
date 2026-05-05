# Phase 05 — Pre-Delete Stop + Optimistic Navigate

**Strategic spec:** [`../S0094_player-move-currently-playing.md`](../S0094_player-move-currently-playing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 6 / 6
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Before the background IO delete starts: stop playback and free the SFTP/network stream, evict the cache entry, and navigate optimistically to the next file. On successful deletion, reconcile the file list by path only (no second navigation). This mirrors Phase 02 for the delete operation.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`removeDeletedFile` uses path-based lookup via the shared `removeFileFromList`).
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

### Step 05.1 — Backup both files before changes

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
- 2026-05-05 — Verification 2/2 PASS. Backups: temp/FileOperationsHandler_20260505_152225.kt, temp/PlayerManagerInitializer_20260505_152225.kt.

---

### Step 05.2 — Add `onBeforeDelete` to `FileOperationCallback` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a new method to the `FileOperationCallback` interface (inside `FileOperationsHandler`):
>
> ```kotlin
> fun onBeforeDelete(deletedFilePath: String)
> ```
>
> Place it immediately after `onBeforeMove`. This method is called synchronously on the main thread before the background IO delete operation starts.

**Verification:**

- `Grep` — `fun onBeforeDelete(deletedFilePath: String)` present exactly once in `FileOperationsHandler.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 1/1 PASS. `fun onBeforeDelete(deletedFilePath: String)` at line 65.

---

### Step 05.3 — Add debounce flag; call `onBeforeDelete` in `performDelete` before launching coroutine

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> At the class level in `FileOperationsHandler`, add alongside the existing `moveInProgress`:
> ```kotlin
> @Volatile private var deleteInProgress = false
> ```
>
> In `performDelete()`, immediately after `val currentFile = callback.getCurrentFile() ?: return`:
> 1. Add debounce guard:
>    ```kotlin
>    if (deleteInProgress) return
>    deleteInProgress = true
>    ```
> 2. Then add:
>    ```kotlin
>    callback.onBeforeDelete(currentFile.path)
>    ```
>    This must appear **before** `appScope.launch {` so the stream is freed before IO begins.
>
> Also expose a `fun resetDeleteInProgress()` method that sets `deleteInProgress = false`. In the `catch (e: Exception)` block inside `performDelete()`, call `resetDeleteInProgress()` before `callback.onOperationError(...)`.

**Verification:**

- `Grep` — `deleteInProgress = true` present in `FileOperationsHandler.kt`.
- `Grep` — `callback.onBeforeDelete(currentFile.path)` present in `FileOperationsHandler.kt`.
- `Grep` — `fun resetDeleteInProgress()` present in `FileOperationsHandler.kt`.
- `Grep` — `Log\.d(` returns zero hits in `FileOperationsHandler.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 4/4 PASS. deleteInProgress=true, onBeforeDelete, resetDeleteInProgress, deleteInProgress=false in catch/failure branches. Dev log recorded.

---

### Step 05.4 — Implement `onBeforeDelete` in `PlayerManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> In `initFileOps()`, inside the anonymous `FileOperationCallback` object, add the `onBeforeDelete` override immediately after `onBeforeMove`:
>
> ```kotlin
> override fun onBeforeDelete(deletedFilePath: String) {
>     if (deletedFilePath != activity.viewModel.state.value.currentFile?.path) return
>     activity.stopVideoPlayback()
>     activity.viewModel.state.value.resource?.let { resource ->
>         MediaFilesCacheManager.removeFile(resource.id, deletedFilePath)
>     }
>     activity.navigationManager.navigateNextAfterOperation("Pre-delete: stop and optimistic advance")
> }
> ```
>
> The guard prevents disturbing playback if delete is triggered for a file that is not currently loaded.

**Verification:**

- `Grep` — `override fun onBeforeDelete(deletedFilePath: String)` present in `PlayerManagerInitializer.kt`.
- `Grep` — `activity.stopVideoPlayback()` present in the `onBeforeDelete` body (verify it is not in `onBeforeMove` only).
- `Grep` — `navigateNextAfterOperation("Pre-delete` present in `PlayerManagerInitializer.kt`.
- `Grep` — `!= activity.viewModel.state.value.currentFile?.path` present at least twice in `PlayerManagerInitializer.kt` (once per pre-operation guard).

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 4/4 PASS. onBeforeDelete override, navigateNextAfterOperation("Pre-delete"), stopVideoPlayback, path guard×2 (for both onBeforeMove and onBeforeDelete).

---

### Step 05.5 — Simplify `onDeleteSuccess`: remove redundant navigation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> In `initFileOps()`, replace the body of `onDeleteSuccess` as follows:
>
> ```kotlin
> override fun onDeleteSuccess(deletedFilePath: String) {
>     fileOperationsHandler.resetDeleteInProgress()
>     activity.lifecycleManager.trackModifiedFile(deletedFilePath)
>     val hasRemainingFiles = activity.viewModel.removeDeletedFile(deletedFilePath)
>     if (!hasRemainingFiles) activity.finish()
>     // Navigation already performed pre-delete in onBeforeDelete — do not navigate again.
>     // Note: handleDeleteSuccess in PlayerLifecycleManager is retained for
>     // the Android batch-delete / RecoverableSecurityException permission flows,
>     // which do not go through onBeforeDelete.
> }
> ```
>
> Key changes vs. old code:
> - `activity.handleDeleteSuccess(deletedFilePath)` replaced by inline work.
> - `MediaFilesCacheManager.removeFile(...)` removed from this path (now in `onBeforeDelete`).
> - No `navigateNextAfterOperation` call (navigation already performed pre-delete).
> - `resetDeleteInProgress()` releases the debounce lock on success.

**Verification:**

- `Grep` — `activity.handleDeleteSuccess(deletedFilePath)` appears **zero** times inside `onDeleteSuccess` body in `PlayerManagerInitializer.kt`.
- `Grep` — `activity.viewModel.removeDeletedFile(deletedFilePath)` present in `onDeleteSuccess` body.
- `Grep` — `resetDeleteInProgress()` present in `PlayerManagerInitializer.kt`.
- `Grep` — `navigateNextAfterOperation` appears **zero** times inside `onDeleteSuccess` body.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 5/5 PASS. handleDeleteSuccess=0 in onDeleteSuccess callback, removeDeletedFile present, resetDeleteInProgress present, navigateNext=0 in onDeleteSuccess.

---

### Step 05.6 — Dev log entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.5

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt" "S0094 Phase 05" "Add onBeforeDelete callback + deleteInProgress debounce; call before IO in performDelete"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt" "S0094 Phase 05" "Implement onBeforeDelete (stop+navigate); remove redundant navigation from onDeleteSuccess"
> ```

**Verification:**

- `Grep` — two lines containing `S0094 Phase 05` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 1/1 PASS. Two `S0094 Phase 05` entries present in CHANGELOG.md (lines 6326–6327).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entries added (Step 05.6).
- [x] Catalog regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (public callback interface changed).

---

## Handoff Notes to Next Phase

After Phase 05: delete operations stop playback and navigate the user immediately; the background IO does not race with an open stream; the file list is reconciled by path after deletion completes. `handleDeleteSuccess` in `PlayerLifecycleManager` remains functional for the Android permission-based delete flows (batch/RecoverableSecurityException), which are unchanged.

---

## Rollback Plan

Revert phase commit(s). The `onBeforeDelete` interface addition is source-compatible — all callers implement it. No data migration.

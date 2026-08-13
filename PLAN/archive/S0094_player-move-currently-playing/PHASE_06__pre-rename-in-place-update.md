# Phase 06 — Pre-Rename Stop + In-Place List Update

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

Before the background rename IO starts: stop playback and free the SFTP/network stream. On rename success: update the renamed file's path in-place in the playlist (by path, not by index) and restart playback from the new path — the user stays on the same file, now at its new name. This replaces the current full list reload (`reloadAfterRename`) which could silently point to a different file when the sort order changes after rename.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (path-based list operations established).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 713 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/RenameDialog.kt` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 820 |

> `PlayerViewModel.kt` and `PlayerManagerInitializer.kt` exceed 500 lines — backup before edit.

---

## Steps

### Step 06.1 — Backup `PlayerViewModel` and `PlayerManagerInitializer` before changes

**Files:** `PlayerViewModel.kt`, `PlayerManagerInitializer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create timestamped backups in `temp/`:
> ```powershell
> $ts = Get-Date -Format 'yyyyMMdd_HHmmss'
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt" "temp/PlayerViewModel_$ts.kt"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt" "temp/PlayerManagerInitializer_$ts.kt"
> ```

**Verification:**

- `Glob` — `temp/PlayerViewModel_*.kt` returns at least two matches (one from Phase 01, one from this phase).
- `Glob` — `temp/PlayerManagerInitializer_*.kt` returns at least two matches.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 2/2 PASS. Backups: temp/PlayerViewModel_20260505_153105.kt, temp/PlayerManagerInitializer_20260505_153105.kt.

---

### Step 06.2 — Add `updateRenamedFilePath` to `PlayerViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add the following method to `PlayerViewModel`, alongside `removeMovedFile` and `removeDeletedFile`:
>
> ```kotlin
> fun updateRenamedFilePath(oldPath: String, newPath: String): Boolean {
>     val currentState = state.value
>     val updatedFiles = currentState.files.toMutableList()
>     val fileIndex = updatedFiles.indexOfFirst { it.path == oldPath }
>     if (fileIndex == -1) {
>         Timber.w("updateRenamedFilePath: file not found by path '$oldPath'")
>         return false
>     }
>     updatedFiles[fileIndex] = updatedFiles[fileIndex].copy(
>         path = newPath,
>         name = java.io.File(newPath).name
>     )
>     updateState { it.copy(files = updatedFiles) }
>     saveResumeState()
>     Timber.d("File renamed in list at index=$fileIndex: '$oldPath' → '$newPath'")
>     return true
> }
> ```
>
> `currentIndex` is NOT changed — the user stays on the same position in the list, now pointing to the file's new path. The updated state emitted by `updateState` causes the observer to detect `currentFile.path` has changed and re-trigger playback with the new path.

**Verification:**

- `Grep` — `fun updateRenamedFilePath(oldPath: String, newPath: String)` present in `PlayerViewModel.kt`.
- `Grep` — `indexOfFirst { it.path == oldPath }` present in `PlayerViewModel.kt`.
- `Grep` — `Log\.d(` returns zero hits in `PlayerViewModel.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 3/3 PASS. updateRenamedFilePath at line 642, indexOfFirst{path==oldPath} at 645, Log.d=0.

---

### Step 06.3 — Add `onBeforeRename` hook parameter to `RenameDialog`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/RenameDialog.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> In `RenameDialog`'s constructor, add an optional parameter after the existing ones:
>
> ```kotlin
> private val onBeforeRename: ((oldPath: String) -> Unit)? = null,
> ```
>
> Call `onBeforeRename?.invoke(oldPath)` synchronously on the main thread immediately before the coroutine or IO operation that performs the actual rename — i.e., just before the background rename work begins, after the user confirms the dialog. There are two rename code paths in `RenameDialog` (one for network paths, one for local); add the call at the start of each path, before IO begins.

**Verification:**

- `Grep` — `onBeforeRename: ((oldPath: String) -> Unit)?` present in `RenameDialog.kt`.
- `Grep` — `onBeforeRename?.invoke(oldPath)` present in `RenameDialog.kt` (should match at least twice — once per rename code path).

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 2/2 PASS. onBeforeRename param at line 34; invoked at lines 124 (single rename) and 200 (multi rename).

---

### Step 06.4 — Update `PlayerDialogHelper`: pass hook and capture rename result

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> 1. In `PlayerDialogHelper.DialogCallback`, change the signature of `onRenameComplete`:
>    ```kotlin
>    // Old:
>    fun onRenameComplete()
>    // New:
>    fun onRenameComplete(oldPath: String, newPath: String)
>    ```
>
> 2. In `showRenameDialog(currentFile: MediaFile)`, update the `RenameDialog` constructor call to:
>    - Pass `onBeforeRename = { oldPath -> dialogCallback.onBeforeRenameDialog(oldPath) }` — add `onBeforeRenameDialog(oldPath: String)` to `DialogCallback` as well.
>    - Pass `onComplete = { oldPath, newFile -> dialogCallback.onRenameComplete(oldPath, newFile.path) }` (currently both params are `_`).
>
>    `DialogCallback` additions:
>    ```kotlin
>    fun onBeforeRenameDialog(oldPath: String)
>    fun onRenameComplete(oldPath: String, newPath: String)
>    ```

**Verification:**

- `Grep` — `fun onBeforeRenameDialog(oldPath: String)` present in `PlayerDialogHelper.kt`.
- `Grep` — `fun onRenameComplete(oldPath: String, newPath: String)` present in `PlayerDialogHelper.kt`.
- `Grep` — `onBeforeRename = {` present in `PlayerDialogHelper.kt` (passed to `RenameDialog`).
- `Grep` — `newFile.path` present in the `onComplete` lambda in `PlayerDialogHelper.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 4/4 PASS. onBeforeRenameDialog at 155, onRenameComplete(oldPath,newPath) at 156, onBeforeRename={} at 342, newFile.path at 341.

---

### Step 06.5 — Implement `onBeforeRenameDialog` and `onRenameComplete` in `PlayerManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 06.4

**Prompt for developer:**

> In the `DialogCallback` anonymous object inside `initDialogHelper()` (or wherever `DialogCallback` is wired), add:
>
> ```kotlin
> override fun onBeforeRenameDialog(oldPath: String) {
>     if (oldPath != activity.viewModel.state.value.currentFile?.path) return
>     activity.stopVideoPlayback()
>     activity.viewModel.state.value.resource?.let { resource ->
>         MediaFilesCacheManager.removeFile(resource.id, oldPath)
>     }
> }
>
> override fun onRenameComplete(oldPath: String, newPath: String) {
>     val found = activity.viewModel.updateRenamedFilePath(oldPath, newPath)
>     if (!found) {
>         // File was not in the list (edge case) — fall back to full reload.
>         activity.viewModel.reloadAfterRename()
>     }
>     // currentIndex unchanged; state update causes observer to restart playback at new path.
> }
> ```
>
> For rename there is no navigation to next file: the user stays on the renamed file. The ViewModel state update (new `currentFile.path`) is observed by the Activity, which calls `playVideo()` with the new path automatically.
>
> `reloadAfterRename()` is retained as a fallback for the edge case where `updateRenamedFilePath` finds no match (e.g., the file was removed from the list between rename initiation and completion).

**Verification:**

- `Grep` — `override fun onBeforeRenameDialog(oldPath: String)` present in `PlayerManagerInitializer.kt`.
- `Grep` — `override fun onRenameComplete(oldPath: String, newPath: String)` present in `PlayerManagerInitializer.kt`.
- `Grep` — `activity.viewModel.updateRenamedFilePath(oldPath, newPath)` present in `PlayerManagerInitializer.kt`.
- `Grep` — `activity.viewModel.reloadAfterRename()` present exactly once in `PlayerManagerInitializer.kt` (fallback only, not primary path).
- `Grep` — `navigateNextAfterOperation` appears **zero** times in the rename-related overrides.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 5/5 PASS. onBeforeRenameDialog at 213, onRenameComplete(oldPath,newPath) at 220, updateRenamedFilePath at 221, reloadAfterRename×1 (fallback), navigateNext=0 in rename overrides.

---

### Step 06.6 — Dev log entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.5

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt" "S0094 Phase 06" "Add updateRenamedFilePath: in-place path update by oldPath, keeps currentIndex"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/RenameDialog.kt" "S0094 Phase 06" "Add onBeforeRename hook: called before IO starts, stops playback pre-rename"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt" "S0094 Phase 06" "Pass onBeforeRename hook and capture oldPath/newPath in onComplete"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt" "S0094 Phase 06" "Implement onBeforeRenameDialog (stop) and onRenameComplete (in-place update)"
> ```

**Verification:**

- `Grep` — four lines containing `S0094 Phase 06` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 1/1 PASS. Four `S0094 Phase 06` entries in CHANGELOG.md.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entries added (Step 06.6).
- [x] Catalog regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new ViewModel method + changed dialog interface).

---

## Handoff Notes to Next Phase

After Phase 06: renaming the currently playing file stops playback before IO, then restarts it from the new path without navigating away. The file occupies the same index in the playlist with its new name/path. `reloadAfterRename()` is retained as a fallback and for any future callers that do not go through `PlayerManagerInitializer`.

---

## Rollback Plan

Revert phase commit(s). `RenameDialog` constructor change adds an optional parameter — source-compatible with all existing callers that pass `null` (default). No data migration.

# Phase 01 - Unified rename executor

**Strategic spec:** [`../S0417_bugfix-batch-rename-cloud-network.md`](../S0417_bugfix-batch-rename-cloud-network.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-06-14
**Completed:** 2026-06-14

---

## Objective

Route the browser's batch rename and its undo through `FileOperationUseCase` so both work for local,
network, and cloud resources; preserve undo, per-item error reporting, and instant list update.

---

## Prerequisites

- [ ] Strategic §6.1 and §6.2 research items are Resolved (see INDEX research inputs).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | Modified | ≤ 305 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/undo/BrowseUndoManager.kt` | Modified | ≤ 270 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | ≤ 820 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt` | Modified | ≤ 760 |

> No layout edits - the dialog `dialog_rename_multiple.xml` (portrait + landscape) and
> `BrowseRenameFilesAdapter` are kept unchanged per strategic §3.3.

---

## Steps

### Step 01.1 - Make rename undo scheme-agnostic

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/undo/BrowseUndoManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `BrowseUndoManager.UndoCallbacks`, add `suspend fun renameViaFileOperation(currentPath: String, originalName: String): Boolean`.
> Rewrite `undoRenameOperation` so that for each `operation.oldNames` pair `(currentPath, originalName)` it
> calls `callbacks.renameViaFileOperation(currentPath, originalName)` instead of `File(newPath).renameTo(File(oldPath))`;
> keep the trailing `callbacks.reloadFileList()` and the `undo_rename_cancelled` message.
> In `Models.kt`, update the `UndoOperation.oldNames` comment to state the new RENAME semantics:
> `(currentPathAfterRename, originalDisplayName)`.
> Do not use `Log.*`; keep `File.renameTo` out of `undoRenameOperation`.

**Verification:**

- `Grep` - `renameViaFileOperation` matches in `BrowseUndoManager.kt` (interface declaration + call site).
- `Grep` - `renameTo` returns zero hits inside `undoRenameOperation` (other undo operations may still use it).
- `Grep` - `currentPathAfterRename` present in the `Models.kt` `oldNames` comment.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification 3/3 PASS. Files: BrowseUndoManager.kt (interface method + undoRenameOperation via use case), Models.kt (oldNames comment). Debug probe deferred to finalization. Dev log recorded.

---

### Step 01.2 - Implement the use-case-backed reverse rename in the ViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Implement `renameViaFileOperation(currentPath, originalName)` in the inline `UndoCallbacks` object that
> `BrowseViewModel` passes to `BrowseUndoManager`. Build a path-preserving `File` from `currentPath` (override
> `getPath`/`getAbsolutePath` so `smb://`/`sftp://`/`ftp://`/`cloud://` schemes survive), then
> `return fileOperationUseCase.execute(FileOperation.Rename(file, originalName)) is FileOperationResult.Success`.
> Reuse the existing `fileOperationUseCase` property; do not add new constructor parameters or Hilt bindings.

**Verification:**

- `Grep` - `override suspend fun renameViaFileOperation` matches once in `BrowseViewModel.kt`.
- `Grep` - `FileOperation.Rename` present inside that override.
- `Grep` - `Log\.d\(` returns zero hits in `BrowseViewModel.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification 3/3 PASS. Files: BrowseViewModel.kt (renameViaFileOperation override via fileOperationUseCase, path-preserving File). Dev log recorded.

---

### Step 01.3 - Execute batch rename through the use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Rewrite only the Apply handler of `showRenameMultipleDialog` (keep the dialog, `BrowseRenameFilesAdapter`,
> field layout, focus, and keyboard behavior unchanged).
> Run the rename loop inside `callbacks.getLifecycleOwner().lifecycleScope.launch { .. }` so network/cloud
> I/O stays off the UI thread. For each changed file:
> - call `callbacks.getFileOperationUseCase().execute(FileOperation.Rename(file, newName))`;
> - on `FileOperationResult.Success`: increment the counter, read the authoritative new path from
>   `result.copiedFilePaths.firstOrNull()` (fall back to a path-preserving reconstruction only if empty),
>   record the undo pair `(authoritativeNewPath, file.name)`, and update the list pointwise via
>   `callbacks.createMediaFileFromFile(newFile)` + `callbacks.updateFile(oldPath, mediaFile)` guarded by
>   `callbacks.setIgnoringFileChanges(true/false)` exactly as `showRenameSingleDialog` does;
> - on `FileOperationResult.Failure`: append a localized per-item error mapped the same way
>   `RenameDialog.toRenameFailureMessage` maps it (reuse `R.string.file_already_exists`,
>   `R.string.rename_failed_generic`; no new strings).
> Remove the local-only `newFile.exists()` pre-check, the SMB/SFTP/FTP-only `newFile` reconstruction used
> for `renameTo`, the `File.renameTo` call, and the `callbacks.reloadFiles()` call.
> Build the undo `UndoOperation(type = FileOperationType.RENAME, oldNames = pairs)` and call
> `callbacks.saveUndoOperation(..)` only when at least one file was renamed; keep the success and aggregated
> error toasts; dismiss the dialog after the loop.
> Strings pass COMMUNICATION_POLICY §6 checklist.

**Verification:**

- `Grep` - `getFileOperationUseCase().execute` matches inside `showRenameMultipleDialog`.
- `Grep` - `renameTo` returns zero hits in `BrowseDialogHelper.kt`.
- `Grep` - `reloadFiles()` returns zero hits inside `showRenameMultipleDialog`.
- `Grep` - `copiedFilePaths` present inside `showRenameMultipleDialog`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification 4/4 PASS. Files: BrowseDialogHelper.kt (Apply handler rewritten: async use-case loop, authoritative new path, pointwise updateFile, per-item localized errors, undo pairs `(newPath, originalName)`; dropped renameTo/exists-precheck/reloadFiles). Rule 21: removed orphaned `DialogCallbacks.reloadFiles()` + its override in BrowseDialogCallbacksImpl.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` -> BUILD SUCCESSFUL in 33s.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (UndoCallbacks API changed).

---

## Handoff Notes to Next Phase

Both rename and its undo now go through `FileOperationUseCase`. The `UndoOperation.oldNames` pair for
RENAME means `(currentPathAfterRename, originalDisplayName)`. The two `S0417:` debug probes (forward +
undo) are inserted at finalization - as the last code edits before the build, when the ticket flips to
`BlockNeedUserTest` - so the ticket-log gate stays green while the ticket is `In Progress`.

---

## Rollback Plan

Revert the phase commit(s) - no data migration or persisted format changed (`UndoOperation` is in-memory,
expires after 10 s). The dialog layout was never touched.

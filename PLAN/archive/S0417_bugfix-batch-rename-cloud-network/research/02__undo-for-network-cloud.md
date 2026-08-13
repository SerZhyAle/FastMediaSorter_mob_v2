# Research 02 - Undo for network and cloud rename

**Strategic item:** §6.2
**Status:** Resolved

## Question

Does the existing undo path correctly restore original names for cloud/network resources through the
application file-operations layer?

## Findings

- Undo for rename is `BrowseUndoManager.undoRenameOperation`. It iterates `operation.oldNames`
  (`List<Pair<oldPath, newPath>>`) and calls `File(newPath).renameTo(File(oldPath))`. Like the forward
  bug, `renameTo` is local-only - so undo of a network/cloud batch rename silently does nothing.
- `oldNames` for `FileOperationType.RENAME` is a closed loop: produced only by
  `BrowseDialogHelper.showRenameMultipleDialog`, consumed only by `undoRenameOperation`. Every other
  `UndoOperation` construction passes `oldNames = null`. The pair semantics can be redefined safely.
- Path basename is NOT a reliable source of the original display name for cloud. A cloud path ends with
  the provider's internal item id, not the human name (documented in `showRenameSingleDialog`,
  `showRenameMultipleDialog`). For SMB/SFTP/FTP/local the basename IS the human name.
- `FileOperationUseCase.execute` returns the authoritative post-rename path in
  `FileOperationResult.Success.copiedFilePaths[0]` for every handler:
  - `CloudFileOperationHandler.executeRename` returns `cloud://<provider>/<result.data.path>`.
  - `LocalRenameFileOperation` returns the new local/SAF path.
  - SMB/SFTP/FTP handlers return their new remote path.
- `BrowseViewModel` owns `fileOperationUseCase` and constructs `BrowseUndoManager` with an inline
  `UndoCallbacks` object, so a use-case-backed reverse rename can be wired there without new DI.

## Decision

Route undo restoration through `FileOperationUseCase`, and redefine the RENAME undo record so it carries
what a scheme-agnostic reverse rename needs:

- New `oldNames` semantics for RENAME: `List<Pair<currentPathAfterRename, originalDisplayName>>`.
  - `currentPathAfterRename` = the authoritative path captured from the forward operation's
    `Success.copiedFilePaths[0]` (NOT a reconstructed path).
  - `originalDisplayName` = the file's display name captured before the forward rename
    (`file.name`, which the browser already overrides to the real name for network/cloud Files).
- Add `suspend fun renameViaFileOperation(currentPath: String, originalName: String): Boolean` to
  `BrowseUndoManager.UndoCallbacks`; implement in `BrowseViewModel` by executing
  `FileOperation.Rename(pathPreservingFile(currentPath), originalName)` and returning `is Success`.
- Rewrite `undoRenameOperation` to call that callback per pair instead of `File.renameTo`. Keep the
  trailing `reloadFileList()` (undo refresh is acceptable; pointwise restore is out of scope).

## Caveat (device-test target)

Cloud round-trip correctness depends on the provider keeping a stable file id across rename and on the
returned `copiedFilePaths` path re-parsing to that id. This matches the assumption the single-rename
undo (`FileOperationUseCase.undo`) already relies on, so this change reaches parity, not regression.
Strategic criterion 4 (undo after a cloud/network batch rename) must be verified on device.

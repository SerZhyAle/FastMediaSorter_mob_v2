# Research 01 - Reuse common dialog vs patch current executor

**Strategic item:** §6.1
**Status:** Resolved

## Question

Replace the browser's bespoke batch-rename dialog with the shared `RenameDialog` (which already
renames multiple files through the application file-operations layer), or keep the current dialog and
swap only its execution mechanism?

## Findings

- The browser batch-rename dialog is `BrowseDialogHelper.showRenameMultipleDialog`. Its Apply handler
  loops over files and calls `File.renameTo(newFile)` directly. `File.renameTo` only works for local
  filesystem paths, so it fails for `smb://`, `sftp://`, `ftp://`, `cloud://`. This is the bug.
- That same handler already records an undo operation (`UndoOperation(type=RENAME, oldNames=pairs)`
  via `callbacks.saveUndoOperation`) and reports per-item errors, then calls `callbacks.reloadFiles()`.
- The browser single-rename path (`showRenameSingleDialog`) builds a `RenameDialog` and executes via
  `FileOperationUseCase.execute(FileOperation.Rename(...))`. The use case dispatches by path scheme to
  cloud / SMB / SFTP / FTP / local handlers. This is why single rename works for every resource type.
- `FileOperationUseCase.execute` runs on `Dispatchers.IO` already, so the executor is off the UI thread.
- `RenameDialog.renameMultipleFiles` also already routes through the use case, but it does NOT record an
  undo operation and uses its own `dialog_rename.xml` layout (file-count header, different background).

## Decision

Keep the current dialog; replace only the per-file executor.

- Strategic §5 is explicit: "Диалог как UI-элемент сохраняется; меняется только то, как выполняется
  собственно операция над каждым файлом." Owner input §3.3 hardens this into a UI-placement contract:
  existing dialog preserved without changing layout or field composition.
- The "proven path" the owner wants to reuse (§3.1) is the execution layer - `FileOperationUseCase` -
  not the `RenameDialog` UI. Swapping the UI would change the visible dialog and break §3.3.
- Therefore: in `showRenameMultipleDialog`, replace `File.renameTo` with
  `FileOperationUseCase.execute(FileOperation.Rename(file, newName))`, keep the dialog/adapter/undo/error
  scaffolding, and run the loop in `getLifecycleOwner().lifecycleScope`.

## Behavior to preserve (delta vs current handler)

- Undo: keep `saveUndoOperation`, but build pairs from the authoritative result path - see research 02.
- Instant update: switch from `reloadFiles()` (full reload) to per-success `updateFile(oldPath, mediaFile)`
  (mirrors single-rename path), satisfying strategic goal 4 / risk "регрессия мгновенного обновления".
- Per-item errors: keep aggregated error list; map `FileOperationResult.Failure` to localized text the
  same way `RenameDialog.toRenameFailureMessage` does (reuse existing strings, no new strings).
- Drop the local-only `newFile.exists()` pre-check and the SMB/SFTP/FTP-only `newFile` reconstruction
  (it never covered `cloud://`); existence checks live inside the handlers.

# S1325 research 03 - current state of directory operations

Date: 2026-07-31. Read-only research pass over `app_v2`. Source of the conclusions in the strategic spec sections 4, 5 and 7.

## Verdict in one line

The data layer already performs recursive directory create / rename / delete / copy / move for every resource type; the gap is reachability in the browse list plus the missing cross-resource-type path for trees.

## Reachability gap (the owner's symptom)

- The browse list renders folder rows and file rows through the same adapter; each view holder branches on `isDirectory` while binding.
- Selection checkbox is bound as `cbSelect.isVisible = !isFolder` in all three view holders (`MediaFileAdapter.kt:852, 1091, 1272`), and the checked-change listener early-returns for a directory (`MediaFileAdapter.kt:543`).
- Per-row overflow button is bound as `useOverflow = fileOpsInOverflowMenu && !isFolder` (`MediaFileAdapter.kt:813, 1066, 1248`), so a folder row has neither the overflow button nor the direct operation buttons (`shouldHideActions` also includes `isFolder`, `MediaFileAdapter.kt:812`).
- Long press is gated the same way: `if (!file.isDirectory) onFileLongClick(file)` (`MediaFileAdapter.kt:564, 571`), and range selection ignores folders (`MediaFileAdapter.kt:960, 1180`).
- The selection state holder itself is folder-agnostic: `BrowseSelectionManager.selectAll` selects every path in the current list with no `isDirectory` filter, which is exactly why "select all" pulls folders in while per-row selection cannot.
- The desktop path proves the rest of the stack works: right-click context menu is bound unconditionally (`MediaFileAdapter.kt:343-352`) and routes a folder path into the same copy / move / rename / delete dialogs.

## What already exists below the UI

- `UnifiedFileOperationHandler` dispatches `executeCreateDirectory`, `executeDeleteDirectory`, `executeRenameDirectory`, `executeCopyDirectory`, `executeMoveDirectory`.
- Every protocol strategy (local, SMB, SFTP, FTP, cloud) implements the recursive walk and re-creates the destination structure.
- Mixed file + directory transfers already run in one foreground worker: files through the file operation use case, directories through the unified handler.
- Directory-aware confirmation and rename dialogs already exist (folder-specific delete strings, rename-directory branch).
- Delete of selected directories has its own use case and is already wired from the selection.

## Cross-resource-type gap

- Single files transfer across protocols through a download-to-temp then upload path.
- Directories are rejected before any work starts when source and destination protocols differ - the handler returns a failure with `UnsupportedOperationException`, surfaced to the user as a generic error.

## Missing safety and observability

- No guard against a destination located inside the source subtree, in any strategy.
- No symlink / depth guard in the local recursive walk.
- The whole file list of the tree is materialised in memory before the copy starts.
- Directory operations pass a null progress callback, so the transfer notification shows no progress for a tree.
- Cancellation is checked once per top-level directory, not per file inside it.
- Undo records are built only from non-directory sources, so a folder move cannot be undone today (deferred to its own ticket by owner decision, 2026-07-31).

## Local-destination write path

Remote-to-local strategies (SMB, SFTP, FTP, cloud) write through the scoped-storage-aware local destination writer. The local-to-local strategy writes through raw file APIs instead. Whether this breaks a copy into shared external storage on Android 10+ was not reproduced in this pass - it is an open research item in the spec.

## Setting that produces folder rows

"Show subfolders separately" exists both as a global setting and as a per-resource override, and every resource strategy declares the field applicable, so folder rows can appear for any resource type.

## Coverage

Unit tests cover the unified handler's directory dispatch and the local strategy. No tests cover the adapter's folder branching, the selection manager, the operations manager, or the worker's directory path.

# S1325 research 01 - destination picked by the system folder chooser

Date: 2026-07-31. Code-level pass, no device run.

## What the code does today

`BrowseFolderPickerHandler.onFolderPicked` resolves the picked tree URI in this order:

1. `UriPathResolver.getPath(..)` - a real filesystem path, kept only when the file exists and is writable.
2. Otherwise the normalized `content://` tree URI, kept only when the tree is writable and allowed by the restricted-tree policy.

Whichever survives is passed as `destinationPath` into `fileOperationsManager.executeOperationToPath(..)` for the file part, and into `unifiedFileOperationHandler.executeCopyDirectory/executeMoveDirectory(..)` for each directory in the selection.

`UnifiedFileOperationHandler.getProtocolKey` maps anything without an `smb://` / `sftp://` / `ftp://` / `cloud://` prefix to `"local"`, so a `content://` destination is handed to `LocalOperationStrategy`, whose directory methods construct `java.io.File(path)`. `java.io.File` cannot address a document-tree URI, so the copy either fails or writes to a nonsense path.

Note that `LocalOperationStrategy.supportsProtocol` already excludes `content:/` - the routing in `getProtocolKey` does not consult it.

## Decision for S1325

Refuse directory copy/move when the destination is a document-tree URI, before any work starts, with a specific message. Files in the same selection continue to transfer through their existing path, which does understand SAF.

Reasons:

- The single-file SAF write path is a separate mechanism, not reusable per tree entry without its own design.
- A silent failure is what the owner is reporting in the first place; an explicit refusal is the honest minimum.
- Supporting a document-tree destination for whole trees is a self-contained follow-up, not a prerequisite for the reported problem.

Consequence for the plan: the guard lives in the directory dispatch entry point, so both the folder-picker path and the destination-dialog path inherit it.

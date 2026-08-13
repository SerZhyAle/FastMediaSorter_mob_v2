# S1325 research 02 - local write path for tree copy versus single file

Date: 2026-07-31. Code-level pass, no device run.

## Question

Does recursive copy of a local folder write differently from copy of a single local file, and does that create an Android 10+ scoped-storage gap unique to folders?

## Finding

No divergence.

- `LocalOperationStrategy.copyFile` copies through `FileInputStream` / `FileOutputStream` on raw `java.io.File`, creating parent directories with `mkdirs()`.
- `LocalOperationStrategy.copyDirectory` copies each entry through `File.copyTo(destFile, overwrite = true)`, also creating parents with `mkdirs()`.

Both are plain filesystem writes with no MediaStore-aware writer. The scoped-storage-aware local destination writer is injected only into the remote strategies (SMB, SFTP, FTP, cloud), where the destination of a download is local while the source is not - a different direction of travel.

So whatever holds for a single local file on a given device and permission set holds identically for a local folder. There is no folder-specific scoped-storage gap to close in this ticket.

## Decision for S1325

- Keep same-protocol directory copy on the existing per-protocol strategy implementations; do not rewrite the local write path here.
- Cross-resource-type transfer, which has no implementation at all today, is where the new code goes.
- If shared-storage writing turns out to be broken on a device, it is broken for single files too - that is a different ticket, not part of folder parity.

## Related

Permission helpers already present in the local strategy (`isSharedStoragePath`, `hasManageMediaPermission`, `hasAllFilesAccess`, `canDeleteDirectly`) show the app relies on All-files access or MANAGE_MEDIA for shared-storage writes; the move path already refuses a shared-storage move that would leave a phantom duplicate.

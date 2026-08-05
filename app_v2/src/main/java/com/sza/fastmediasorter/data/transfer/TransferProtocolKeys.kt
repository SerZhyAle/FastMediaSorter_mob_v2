package com.sza.fastmediasorter.data.transfer

/**
 * S1378: the one mapping from a path to the key its [FileOperationStrategy] is registered under.
 *
 * It lives here because it used to live in two places - `UnifiedFileOperationHandler` and
 * `DirectoryTreeTransferManager` each carried their own copy of the same `when`. Adding the document
 * tree route updated only the first, so a `content://` destination kept resolving to the local
 * strategy inside the cross-protocol transfer - the exact misrouting the new strategy exists to end,
 * still alive in the second copy. One function means the next protocol cannot half-land the same way.
 */
fun transferProtocolKeyFor(path: String): String = when {
    path.startsWith("smb://") -> "smb"
    path.startsWith("sftp://") -> "sftp"
    path.startsWith("ftp://") -> "ftp"
    path.startsWith("cloud://") -> "cloud"
    // Must precede the local fallback - a document-tree address is not a filesystem path.
    path.startsWith("content:") -> "saf"
    else -> "local"
}

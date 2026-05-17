package com.sza.fastmediasorter.data.transfer.local

/**
 * Raised by [LocalDestinationWriter] when a non-public local destination
 * rejects the write under scoped storage (typically EACCES wrapped in
 * `FileNotFoundException`). Carries the offending absolute path and the
 * original cause for diagnostic logging and for the UI error formatter.
 *
 * Mapped to [com.sza.fastmediasorter.domain.transfer.FileOperationError.LocalDestinationPermissionDenied]
 * by [com.sza.fastmediasorter.core.util.FileOperationErrorFormatter].
 *
 * See spec S0231 §6.2 (non-public destination strategy).
 */
class LocalDestinationPermissionDeniedException(
    val destinationPath: String,
    cause: Throwable
) : Exception("Local destination not writable: $destinationPath", cause)

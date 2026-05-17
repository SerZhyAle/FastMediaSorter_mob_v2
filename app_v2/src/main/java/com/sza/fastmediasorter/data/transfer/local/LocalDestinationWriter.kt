package com.sza.fastmediasorter.data.transfer.local

import com.sza.fastmediasorter.data.transfer.FileExistsException

/**
 * Opens a ready-to-write [LocalSink] for a classified local destination.
 *
 * **Contract:**
 * - Returns `Result.success(sink)` if the sink is ready and the caller may
 *   start writing bytes into [LocalSink.outputStream].
 * - Returns `Result.failure(FileExistsException(...))` when `overwrite` is
 *   `false` and the destination already exists.
 * - Returns `Result.failure(LocalDestinationPermissionDeniedException(...))`
 *   for non-public destinations rejected by the OS (typically EACCES).
 * - Returns `Result.failure(<typed exception>)` for any other unrecoverable
 *   failure (full disk, MediaStore rejection that cannot be retried, etc.).
 *
 * See strategic spec S0231 §5.1.
 */
interface LocalDestinationWriter {

    suspend fun open(destination: LocalDestinationCategory, overwrite: Boolean): Result<LocalSink>
}

/**
 * Re-exported alias for callers that want to react to the existence-failure
 * case via [Result.exceptionOrNull] without importing the transfer package
 * directly. The actual type comes from the established transfer module.
 */
typealias DestinationAlreadyExistsException = FileExistsException

package com.sza.fastmediasorter.data.transfer.strategy

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * Wraps a simple IO operation in withContext(IO) + try/catch, logging the exception under [tag].
 * Use only for operations with no early returns - complex branching methods should use
 * withContext directly to retain their return@withContext labels.
 */
suspend fun <T> safeIo(tag: String, block: suspend CoroutineScope.() -> T): Result<T> =
    withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, tag)
            Result.failure(e)
        }
    }

/**
 * S3939: the verdict of a same-protocol directory copy. The default
 * [com.sza.fastmediasorter.data.transfer.FileOperationStrategy.moveDirectory] deletes the source
 * whenever the copy reports success, so a copy that left any collected file behind must fail and
 * carry what landed - otherwise the move removes files that never reached the destination.
 */
fun directoryCopyVerdict(copied: Int, total: Int, firstFailure: Throwable?): Result<Int> =
    if (copied == total) {
        Result.success(copied)
    } else {
        val cause = firstFailure ?: IOException("${total - copied} of $total files were not copied")
        Result.failure(PartialDirectoryTransferException(copied, cause))
    }

/** S3939: a listing that failed inside a tree walk; the walk must not treat it as an empty folder. */
fun listingFailure(path: String, detail: String?): IOException =
    IOException("Failed to list $path: ${detail.orEmpty()}")

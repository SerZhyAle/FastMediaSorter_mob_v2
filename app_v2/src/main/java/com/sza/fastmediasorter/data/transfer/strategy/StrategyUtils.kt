package com.sza.fastmediasorter.data.transfer.strategy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

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
            Timber.e(e, tag)
            Result.failure(e)
        }
    }

package com.sza.fastmediasorter.core.util

import timber.log.Timber
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

/**
 * The resource an opener is about to return to its caller, held until the caller actually receives it.
 *
 * `withContext` has prompt-cancellation semantics: when the calling coroutine is cancelled while the
 * block runs, the block's value is discarded and `CancellationException` is thrown instead. A stream
 * opened inside that block and returned from it then has no owner - the SMB handle, the FTP or SSH
 * connection, the HTTP connection or the pool permit behind it stay open until the server gives up.
 * [handingOffCloseable] closes whatever was tracked here when the value never reaches the caller.
 */
class CloseableHandOff internal constructor() {
    private val pending = AtomicReference<Closeable?>()

    /** Register [resource] as the value being handed off; call it where the resource is created. */
    fun <T : Closeable> track(resource: T): T {
        pending.set(resource)
        return resource
    }

    internal fun closeUndelivered() {
        val resource = pending.getAndSet(null) ?: return
        runCatching { resource.close() }
            .onFailure { Timber.w(it, "Failed to close a resource its cancelled caller never received") }
    }
}

/**
 * Run an opener whose result carries a [java.io.Closeable] and close that resource when the result is
 * never delivered - the caller was cancelled mid-block, or the block failed after the resource was
 * tracked. Cancellation is neither suppressed nor delayed: the open step stays cancellable.
 *
 * Track the resource inside the innermost block, not around it: a nested `withContext` (a pool's
 * `withConnection`, for one) throws before an outer block ever sees the value.
 */
suspend fun <R> handingOffCloseable(block: suspend (CloseableHandOff) -> R): R {
    val handOff = CloseableHandOff()
    var delivered = false
    try {
        return block(handOff).also { delivered = true }
    } finally {
        if (!delivered) handOff.closeUndelivered()
    }
}

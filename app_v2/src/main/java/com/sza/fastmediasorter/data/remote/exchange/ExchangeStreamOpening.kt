package com.sza.fastmediasorter.data.remote.exchange

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/** The cancellation-safe opening of a blocking exchange stream, shared by the account and administration clients. */
internal object ExchangeStreamOpening {

    private val CANCELLED = Any()
    private val HANDED_OVER = Any()

    /**
     * Runs the blocking [open] so that a cancelled caller closes the socket [open] adopted at once, whatever stage
     * it blocks in, and a result that completes after the cancellation goes to [release] instead of the caller.
     * The closer is a child of its own: the caller's thread is the one blocked, so only another worker can close.
     */
    suspend fun <T> closingOnCancel(release: (T) -> Unit, open: ((Closeable) -> Unit) -> T): T {
        val slot = AtomicReference<Any?>(null)
        val opened = ArrayList<T>(1)
        try {
            coroutineScope {
                val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        claim(slot, CANCELLED)?.let(::closeQuietly)
                    }
                }
                val scope = coroutineContext.job
                try {
                    // The job turns inactive the moment it is cancelled, before the closer is dispatched to run.
                    opened += open { socket ->
                        if (!scope.isActive || !slot.compareAndSet(null, socket)) closeQuietly(socket)
                    }
                    claim(slot, HANDED_OVER)
                } finally {
                    closer.cancel()
                }
            }
            // A cancellation that landed while the stream was opening has closed it already.
            currentCoroutineContext().ensureActive()
            return opened.single()
        } catch (e: CancellationException) {
            opened.forEach(release)
            throw e
        } catch (e: IOException) {
            // A socket closed by cancellation fails its blocking call; that is the cancellation, not the server.
            currentCoroutineContext().ensureActive()
            throw e
        }
    }

    fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            Timber.d(e, "Exchange connect: closing an abandoned socket failed")
        }
    }

    // Moves [slot] to [state] unless it is already final, answering the socket it held, if any.
    private fun claim(slot: AtomicReference<Any?>, state: Any): Closeable? {
        while (true) {
            val current = slot.get()
            if (current === CANCELLED || current === HANDED_OVER) return null
            if (slot.compareAndSet(current, state)) return current as? Closeable
        }
    }
}

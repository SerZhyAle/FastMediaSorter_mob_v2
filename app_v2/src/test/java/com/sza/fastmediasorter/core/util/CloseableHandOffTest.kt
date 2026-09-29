package com.sza.fastmediasorter.core.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** S3831: a stream opened inside withContext is closed when the cancelled caller never receives it. */
class CloseableHandOffTest {

    private class CountingStream : ByteArrayInputStream(ByteArray(1)) {
        var closeCount = 0
            private set

        override fun close() {
            closeCount++
            super.close()
        }
    }

    @Test
    fun `a stream discarded by a cancelled caller is closed`() = runBlocking {
        val stream = CountingStream()
        val entered = CompletableDeferred<Unit>()
        val cancelled = CountDownLatch(1)
        val caller = launch(Dispatchers.Default) {
            handingOffCloseable { handOff ->
                withContext(Dispatchers.IO) {
                    entered.complete(Unit)
                    // Blocking, as the network open is: the block finishes after the cancel lands.
                    cancelled.await(WAIT_SECONDS, TimeUnit.SECONDS)
                    Result.success(handOff.track(stream))
                }
            }
            error("withContext must not deliver a value to a cancelled caller")
        }
        entered.await()
        caller.cancel()
        cancelled.countDown()
        caller.join()

        assertTrue(caller.isCancelled)
        assertEquals(1, stream.closeCount)
    }

    @Test
    fun `a delivered stream is left open for its caller`() = runBlocking {
        val stream = CountingStream()

        val result = handingOffCloseable { handOff ->
            withContext(Dispatchers.IO) { Result.success(handOff.track(stream)) }
        }

        assertSame(stream, result.getOrNull())
        assertEquals(0, stream.closeCount)
    }

    @Test
    fun `a failure after tracking closes the stream and propagates`() = runBlocking {
        val stream = CountingStream()

        val thrown = runCatching {
            handingOffCloseable<Unit> { handOff ->
                handOff.track(stream)
                throw IOException("bookkeeping failed after the open")
            }
        }.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertEquals(1, stream.closeCount)
    }

    @Test
    fun `a failure before tracking closes nothing`() = runBlocking {
        val thrown = runCatching {
            handingOffCloseable<Unit> { throw IOException("connect refused") }
        }.exceptionOrNull()

        assertTrue(thrown is IOException)
    }

    private companion object {
        const val WAIT_SECONDS = 5L
    }
}

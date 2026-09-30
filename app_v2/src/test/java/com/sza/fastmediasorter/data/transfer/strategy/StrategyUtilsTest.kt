package com.sza.fastmediasorter.data.transfer.strategy

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [safeIo]: success wraps in Result.success, thrown exception is captured as
 * Result.failure with the original throwable. Runs on the IO dispatcher inside runTest.
 */
class StrategyUtilsTest {

    @Test
    fun `safeIo returns success with block result`() = runTest {
        val result = safeIo("tag") { 42 }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `safeIo captures thrown exception as failure`() = runTest {
        val boom = IOException("disk error")
        val result = safeIo<Int>("tag") { throw boom }
        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `directoryCopyVerdict succeeds only when every collected file landed`() {
        val result = directoryCopyVerdict(copied = 3, total = 3, firstFailure = null)
        assertEquals(3, result.getOrNull())
    }

    @Test
    fun `directoryCopyVerdict fails with landed count and first cause when a file was lost`() {
        val boom = IOException("write refused")
        val error = directoryCopyVerdict(copied = 2, total = 3, firstFailure = boom).exceptionOrNull()
        assertTrue(error is PartialDirectoryTransferException)
        assertEquals(2, (error as PartialDirectoryTransferException).completed)
        assertEquals(boom, error.cause)
    }

    @Test
    fun `directoryCopyVerdict fails even without a recorded cause`() {
        val error = directoryCopyVerdict(copied = 0, total = 1, firstFailure = null).exceptionOrNull()
        assertTrue(error is PartialDirectoryTransferException)
    }
}

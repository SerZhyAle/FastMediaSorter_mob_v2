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
}

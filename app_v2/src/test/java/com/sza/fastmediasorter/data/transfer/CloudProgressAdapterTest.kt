package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.cloud.TransferProgress
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [adaptCloudProgress]: null-callback short-circuit, interval throttling, and the
 * forwarded byte/total values. Uses a recording [ByteProgressCallback] and the runTest scope so
 * the launched emissions complete deterministically.
 */
class CloudProgressAdapterTest {

    private class RecordingCallback : ByteProgressCallback {
        val transferred = mutableListOf<Long>()
        val totals = mutableListOf<Long>()
        override suspend fun onProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSecond: Long) {
            transferred.add(bytesTransferred)
            totals.add(totalBytes)
        }
    }

    @Test
    fun `returns null when callback is null`() = runTest {
        assertNull(adaptCloudProgress(null, this))
    }

    @Test
    fun `forwards progress once interval threshold is crossed`() = runTest {
        val cb = RecordingCallback()
        val adapter = adaptCloudProgress(cb, this)!!
        val total = 10_000_000L
        val step = ByteProgressCallback.PROGRESS_REPORT_INTERVAL

        // First emission at exactly the interval should be forwarded.
        adapter(TransferProgress(bytesTransferred = step, totalBytes = total))
        // Tiny additional delta below the interval should be throttled (not forwarded).
        adapter(TransferProgress(bytesTransferred = step + 1, totalBytes = total))

        // advance/complete launched coroutines
        testScheduler.advanceUntilIdle()

        assertEquals(1, cb.transferred.size)
        assertEquals(step, cb.transferred.first())
        assertEquals(total, cb.totals.first())
    }

    @Test
    fun `forwards multiple times across successive intervals`() = runTest {
        val cb = RecordingCallback()
        val adapter = adaptCloudProgress(cb, this)!!
        val total = 50_000_000L
        val step = ByteProgressCallback.PROGRESS_REPORT_INTERVAL

        adapter(TransferProgress(step, total))
        adapter(TransferProgress(step * 2, total))
        adapter(TransferProgress(step * 3, total))
        testScheduler.advanceUntilIdle()

        assertEquals(3, cb.transferred.size)
        assertTrue(cb.transferred.contains(step * 3))
    }
}

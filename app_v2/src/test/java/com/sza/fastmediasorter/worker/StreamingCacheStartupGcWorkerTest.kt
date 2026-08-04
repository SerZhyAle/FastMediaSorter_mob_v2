package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingCacheStartupGcWorkerTest {

    @Test
    fun `doWork propagates cancellation from cache cleanup`() = runTest {
        val repository = mockk<StreamingCacheRepository>()
        coEvery { repository.verifyAndPrune() } throws CancellationException("worker stopped")
        val worker = StreamingCacheStartupGcWorker(
            context = mockk<Context>(relaxed = true),
            workerParams = mockk<WorkerParameters> {
                every { inputData } returns Data.EMPTY
            },
            streamingCacheRepository = repository,
        )

        var wasCancelled = false
        try {
            worker.doWork()
        } catch (_: CancellationException) {
            wasCancelled = true
        }

        assertTrue(wasCancelled)
    }
}

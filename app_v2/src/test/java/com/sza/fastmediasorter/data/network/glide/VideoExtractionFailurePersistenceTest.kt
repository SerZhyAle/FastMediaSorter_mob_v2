package com.sza.fastmediasorter.data.network.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * S3766: persistFailure is a read-modify-write of one SharedPreferences StringSet reachable
 * from several Glide worker threads; without the lock the last apply() drops the other
 * threads' entries. Runs against the real startup graph (RobolectricFastMediaSorterApp, wired
 * globally in robolectric.properties), so the object talks to real SharedPreferences;
 * loadAll() is primed once per test because it also writes the schema version that gates reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoExtractionFailurePersistenceTest {

    @Test
    fun `concurrent persistFailure writers do not lose entries`() {
        VideoExtractionFailurePersistence.loadAll()
        val threadCount = 8
        val pathsPerThread = 25
        val pool: ExecutorService = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val done = CountDownLatch(threadCount)
        try {
            repeat(threadCount) { t ->
                pool.submit {
                    ready.countDown()
                    ready.await()
                    repeat(pathsPerThread) { p ->
                        VideoExtractionFailurePersistence.persistFailure("path-$t-$p")
                    }
                    done.countDown()
                }
            }
            assertTrue(done.await(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(threadCount * pathsPerThread, VideoExtractionFailurePersistence.loadAll().size)
    }

    @Test
    fun `re-persisting the same path replaces the entry instead of duplicating it`() {
        VideoExtractionFailurePersistence.loadAll()
        VideoExtractionFailurePersistence.persistFailure("video.mp4")
        VideoExtractionFailurePersistence.persistFailure("video.mp4")
        assertEquals(1, VideoExtractionFailurePersistence.loadAll().size)
    }
}

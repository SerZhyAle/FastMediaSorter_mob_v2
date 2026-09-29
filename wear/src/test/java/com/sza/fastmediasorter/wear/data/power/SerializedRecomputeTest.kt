package com.sza.fastmediasorter.wear.data.power

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class SerializedRecomputeTest {

    @Test
    fun `a recompute that read older inputs cannot publish after one that read newer inputs`() {
        val input = AtomicInteger(STALE)
        val published = CopyOnWriteArrayList<Int>()
        val staleRead = CountDownLatch(1)
        val releaseStale = CountDownLatch(1)
        val recompute = SerializedRecompute(
            compute = {
                val seen = input.get()
                if (seen == STALE) {
                    staleRead.countDown()
                    releaseStale.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
                seen
            },
            publish = { published += it }
        )

        val stale = thread { recompute.run() }
        staleRead.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        input.set(FRESH)
        val fresh = thread { recompute.run() }
        // Room for the fresh recompute to overtake the stale one, which is what the lock must forbid.
        fresh.join(OVERTAKE_WINDOW_MS)
        releaseStale.countDown()
        stale.join()
        fresh.join()

        assertEquals(listOf(STALE, FRESH), published)
    }

    @Test
    fun `every recompute publishes exactly once`() {
        val published = CopyOnWriteArrayList<Int>()
        val recompute = SerializedRecompute(compute = { FRESH }, publish = { published += it })

        val threads = List(THREADS) { thread { recompute.run() } }
        threads.forEach { it.join() }

        assertEquals(THREADS, published.size)
    }

    companion object {
        private const val STALE = 1
        private const val FRESH = 2
        private const val THREADS = 8
        private const val TIMEOUT_SECONDS = 5L
        private const val OVERTAKE_WINDOW_MS = 200L
    }
}

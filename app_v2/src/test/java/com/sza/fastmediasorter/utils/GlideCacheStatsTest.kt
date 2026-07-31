package com.sza.fastmediasorter.utils

import com.bumptech.glide.load.DataSource
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * S1322: a LOCAL load reads the file straight off internal storage and never consults the Glide
 * disk cache, so it must not count as a cache miss. Before this, a locally-browsed folder always
 * tripped the "zero disk cache hits" warning with four bogus explanations, which is exactly what
 * sent a log reader hunting a cache-persistence defect that did not exist. These tests pin the
 * denominator so a later edit cannot quietly put local loads back into it.
 */
class GlideCacheStatsTest {

    private class CapturingTree : Timber.Tree() {
        val warnings = mutableListOf<String>()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == PRIORITY_WARN) warnings += message
        }
    }

    private lateinit var tree: CapturingTree

    @Before
    fun setUp() {
        tree = CapturingTree()
        Timber.plant(tree)
        GlideCacheStats.reset()
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
        GlideCacheStats.reset()
    }

    private fun record(source: DataSource, times: Int) {
        repeat(times) { GlideCacheStats.recordLoad(source) }
    }

    private fun zeroDiskWarningRaised(): Boolean =
        tree.warnings.any { it.contains("Zero disk cache hits") }

    @Test
    fun `local-only browsing does not raise the zero-disk-cache warning`() {
        // The exact shape observed in logs/fastmediasorter_20260731_004038.log: total=19,
        // disk=0, memory=7, network=0, local=12 - and a warning that meant nothing.
        record(DataSource.LOCAL, LOCAL_LOADS)
        record(DataSource.MEMORY_CACHE, MEMORY_HITS)

        GlideCacheStats.logStats()

        assertFalse(
            "local loads cannot consult the disk cache, so they must not trigger the warning",
            zeroDiskWarningRaised(),
        )
    }

    @Test
    fun `cold network browsing with no disk hits still raises the warning`() {
        record(DataSource.REMOTE, NETWORK_LOADS)

        GlideCacheStats.logStats()

        assertTrue(
            "a genuinely cold network session is what the warning exists for",
            zeroDiskWarningRaised(),
        )
    }

    @Test
    fun `network browsing served from memory is healthy and stays quiet`() {
        record(DataSource.REMOTE, HEALTHY_NETWORK_LOADS)
        record(DataSource.MEMORY_CACHE, HEALTHY_MEMORY_HITS)

        GlideCacheStats.logStats()

        assertFalse(
            "a high memory-hit rate means the disk cache was not needed",
            zeroDiskWarningRaised(),
        )
    }

    @Test
    fun `disk hits keep the warning silent`() {
        record(DataSource.DATA_DISK_CACHE, DISK_HITS)
        record(DataSource.REMOTE, NETWORK_LOADS)

        GlideCacheStats.logStats()

        assertFalse("disk cache is demonstrably working", zeroDiskWarningRaised())
    }

    private companion object {
        const val PRIORITY_WARN = 5
        const val LOCAL_LOADS = 12
        const val MEMORY_HITS = 7
        const val NETWORK_LOADS = 15
        const val HEALTHY_NETWORK_LOADS = 5
        const val HEALTHY_MEMORY_HITS = 10
        const val DISK_HITS = 4
    }
}

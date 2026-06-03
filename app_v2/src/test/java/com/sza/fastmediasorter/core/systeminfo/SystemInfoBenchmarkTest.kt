package com.sza.fastmediasorter.core.systeminfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemInfoBenchmarkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `memory throughput is positive`() {
        val mbps = SystemInfoBenchmark.measureMemoryThroughputMbps()

        assertTrue("memory throughput must be positive, was $mbps", mbps > 0.0)
    }

    @Test
    fun `storage throughput is non-negative and leaves no temp file`() {
        val dir = tempFolder.newFolder("bench")

        val (write, read) = SystemInfoBenchmark.measureStorageThroughputMbps(dir)

        assertTrue("write throughput must be non-negative, was $write", write >= 0.0)
        assertTrue("read throughput must be non-negative, was $read", read >= 0.0)
        val leftovers = dir.listFiles()?.filter { it.name.startsWith("sysinfo_bench_") }.orEmpty()
        assertEquals("benchmark temp file must be deleted", emptyList<Any>(), leftovers)
    }
}

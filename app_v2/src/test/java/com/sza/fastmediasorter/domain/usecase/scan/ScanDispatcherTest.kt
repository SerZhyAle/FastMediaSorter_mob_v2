package com.sza.fastmediasorter.domain.usecase.scan

import com.sza.fastmediasorter.domain.model.ResourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanDispatcherTest {

    private fun dispatcher(local: Int = 4, network: Int = 2, cloud: Int = 1): ScanDispatcher {
        val settings = ScanSettings().apply {
            localParallelism = local
            networkParallelism = network
            cloudParallelism = cloud
        }
        return ScanDispatcher(settings)
    }

    @Test
    fun `availablePermits reflects configured limits per category`() {
        val d = dispatcher(local = 4, network = 2, cloud = 1)
        assertEquals(4, d.availablePermits(ResourceType.LOCAL))
        assertEquals(2, d.availablePermits(ResourceType.SMB))
        assertEquals(2, d.availablePermits(ResourceType.SFTP))
        assertEquals(2, d.availablePermits(ResourceType.FTP))
        assertEquals(1, d.availablePermits(ResourceType.CLOUD))
    }

    @Test
    fun `withScanPermit returns block result and releases permit`() = runTest {
        val d = dispatcher(local = 1)
        val result = d.withScanPermit(ResourceType.LOCAL) { 42 }
        assertEquals(42, result)
        // Permit released after block completes.
        assertEquals(1, d.availablePermits(ResourceType.LOCAL))
    }

    @Test
    fun `withScanPermit releases permit even when block throws`() = runTest {
        val d = dispatcher(local = 1)
        runCatching {
            d.withScanPermit(ResourceType.LOCAL) { error("boom") }
        }
        assertEquals(1, d.availablePermits(ResourceType.LOCAL))
    }

    @Test
    fun `reset re-initialises semaphores from current settings`() = runTest {
        val settings = ScanSettings().apply { localParallelism = 2 }
        val d = ScanDispatcher(settings)
        assertEquals(2, d.availablePermits(ResourceType.LOCAL))

        settings.localParallelism = 5
        d.reset()
        assertEquals(5, d.availablePermits(ResourceType.LOCAL))
    }
}

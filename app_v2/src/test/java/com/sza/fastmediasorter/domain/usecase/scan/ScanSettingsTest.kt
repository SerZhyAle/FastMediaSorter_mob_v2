package com.sza.fastmediasorter.domain.usecase.scan

import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanSettingsTest {

    @Test
    fun `defaults match documented values`() {
        val s = ScanSettings()
        assertEquals(ScanSettings.DEFAULT_LOCAL_PARALLELISM, s.localParallelism)
        assertEquals(ScanSettings.DEFAULT_NETWORK_PARALLELISM, s.networkParallelism)
        assertEquals(ScanSettings.DEFAULT_CLOUD_PARALLELISM, s.cloudParallelism)
    }

    @Test
    fun `limitFor maps local network and cloud types`() {
        val s = ScanSettings().apply {
            localParallelism = 5
            networkParallelism = 3
            cloudParallelism = 1
        }
        assertEquals(5, s.limitFor(ResourceType.LOCAL))
        assertEquals(3, s.limitFor(ResourceType.SMB))
        assertEquals(3, s.limitFor(ResourceType.SFTP))
        assertEquals(3, s.limitFor(ResourceType.FTP))
        assertEquals(1, s.limitFor(ResourceType.CLOUD))
    }

    @Test
    fun `parallelism below 1 is rejected`() {
        val s = ScanSettings()
        assertThrows(IllegalArgumentException::class.java) { s.localParallelism = 0 }
        assertThrows(IllegalArgumentException::class.java) { s.networkParallelism = -1 }
        assertThrows(IllegalArgumentException::class.java) { s.cloudParallelism = 0 }
    }

    @Test
    fun `parallelism above max is rejected`() {
        val s = ScanSettings()
        val over = ScanSettings.MAX_PARALLELISM + 1
        assertThrows(IllegalArgumentException::class.java) { s.localParallelism = over }
        assertThrows(IllegalArgumentException::class.java) { s.networkParallelism = over }
        assertThrows(IllegalArgumentException::class.java) { s.cloudParallelism = over }
    }

    @Test
    fun `boundary values 1 and max are accepted`() {
        val s = ScanSettings().apply {
            localParallelism = 1
            networkParallelism = ScanSettings.MAX_PARALLELISM
        }
        assertEquals(1, s.localParallelism)
        assertEquals(ScanSettings.MAX_PARALLELISM, s.networkParallelism)
    }
}

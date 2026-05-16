package com.sza.fastmediasorter.data.remote.sftp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpConnectionPoolTest {

    @Test
    fun `periodic sweep can start once and stop cleanly`() {
        val pool = SftpConnectionPool()

        assertFalse(pool.isPeriodicSweepActive())

        pool.startPeriodicSweepForTest()
        pool.startPeriodicSweepForTest()

        assertTrue(pool.isPeriodicSweepActive())

        pool.stopPeriodicSweepForTest()

        assertFalse(pool.isPeriodicSweepActive())
    }
}
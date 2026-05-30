package com.sza.fastmediasorter.data.network.datasource

import androidx.media3.datasource.DataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BdTsStripDataSourceFactory] - verifies it wraps the upstream factory's data
 * source in a [BdTsStripDataSource]. The Media3 [DataSource.Factory] / [DataSource] are mocked.
 */
class BdTsStripDataSourceFactoryTest {

    @Test
    fun `createDataSource wraps upstream data source`() {
        val upstreamSource = mockk<DataSource>(relaxed = true)
        val upstreamFactory = mockk<DataSource.Factory>()
        every { upstreamFactory.createDataSource() } returns upstreamSource

        val factory = BdTsStripDataSourceFactory(upstreamFactory)
        val created = factory.createDataSource()

        assertTrue(created is BdTsStripDataSource)
        verify(exactly = 1) { upstreamFactory.createDataSource() }
    }
}

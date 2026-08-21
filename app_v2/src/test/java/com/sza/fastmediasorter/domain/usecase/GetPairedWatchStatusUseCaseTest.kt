package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.PairedWatchStatus
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetPairedWatchStatusUseCaseTest {

    private val repository: WearableDataLayerRepository = mockk()
    private val getStatus = GetPairedWatchStatusUseCase(repository)

    @Test
    fun `an empty node list reads as not connected`() = runTest {
        coEvery { repository.getConnectedNodes() } returns emptyList()
        assertEquals(PairedWatchStatus.NotConnected, getStatus())
    }

    @Test
    fun `a named node reads as connected under that name`() = runTest {
        coEvery { repository.getConnectedNodes() } returns listOf(WearNode("node-1", "Galaxy Watch7"))
        assertEquals(PairedWatchStatus.Connected("Galaxy Watch7"), getStatus())
    }

    @Test
    fun `a blank display name reads as not connected rather than naming an empty string`() = runTest {
        // A row reading "  - connected" tells the reader nothing and looks like a defect.
        coEvery { repository.getConnectedNodes() } returns listOf(WearNode("node-1", "   "))
        assertEquals(PairedWatchStatus.NotConnected, getStatus())
    }

    @Test
    fun `the first node wins when several answer`() = runTest {
        coEvery { repository.getConnectedNodes() } returns listOf(
            WearNode("node-1", "Watch A"),
            WearNode("node-2", "Watch B"),
        )
        assertEquals(PairedWatchStatus.Connected("Watch A"), getStatus())
    }
}

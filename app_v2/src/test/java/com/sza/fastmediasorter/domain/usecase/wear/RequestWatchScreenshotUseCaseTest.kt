package com.sza.fastmediasorter.domain.usecase.wear

import com.google.gson.Gson
import com.sza.fastmediasorter.data.wear.WatchFileLandingRegistry
import com.sza.fastmediasorter.domain.model.WatchFileLanding
import com.sza.fastmediasorter.domain.model.WatchScreenshotOutcome
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearScreenshotRequestAck
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearSyncEvents
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * S3920: a request no node accepted can never be answered, so the round trip must end at once
 * instead of spending the whole ack wait on a spinner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestWatchScreenshotUseCaseTest {

    private val repository = mockk<WearableDataLayerRepository>()
    private val landingRegistry = WatchFileLandingRegistry()
    private val useCase = RequestWatchScreenshotUseCase(repository, Gson(), landingRegistry)

    @Test
    fun `every send failing returns no connected watch without waiting`() = runTest {
        coEvery { repository.getConnectedNodes() } returns NODES
        coEvery { repository.sendMessage(any(), any(), any()) } throws IOException("node gone")

        val outcome = useCase()

        assertEquals(WatchScreenshotOutcome.NoConnectedWatch, outcome)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `one accepted send still waits for the answer`() = runTest {
        coEvery { repository.getConnectedNodes() } returns NODES
        coEvery { repository.sendMessage("a", any(), any()) } throws IOException("node gone")
        coEvery { repository.sendMessage("b", any(), any()) } returns Unit

        val outcome = useCase()

        assertEquals(WatchScreenshotOutcome.WatchDidNotAnswer, outcome)
        assertEquals(ACK_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `a captured answer carries the landing recorded for its file`() = runTest {
        coEvery { repository.getConnectedNodes() } returns NODES
        coEvery { repository.sendMessage(any(), any(), any()) } returns Unit
        landingRegistry.record(SHOT, WatchFileLanding(resourceId = 11L, landedAtEpochMillis = 5_000L))

        val pending = async { useCase() }
        runCurrent()
        WearSyncEvents.emitScreenshotAck(WearScreenshotRequestAck(requestId = "", captured = true, fileName = SHOT))

        assertEquals(
            WatchScreenshotOutcome.Captured(SHOT, capturedAtEpochMillis = 5_000L, browseResourceId = 11L),
            pending.await()
        )
    }

    @Test
    fun `a captured answer with no recorded landing still reports the capture without a resource`() = runTest {
        coEvery { repository.getConnectedNodes() } returns NODES
        coEvery { repository.sendMessage(any(), any(), any()) } returns Unit

        val pending = async { useCase() }
        runCurrent()
        WearSyncEvents.emitScreenshotAck(WearScreenshotRequestAck(requestId = "", captured = true, fileName = SHOT))

        val outcome = pending.await() as WatchScreenshotOutcome.Captured
        assertEquals(SHOT, outcome.fileName)
        assertNull(outcome.browseResourceId)
        assertTrue(outcome.capturedAtEpochMillis > 0L)
    }

    private companion object {
        const val SHOT = "watch-screen-20261016-101500.png"
        const val ACK_TIMEOUT_MS = 60_000L
        val NODES = listOf(WearNode("a", "Watch A"), WearNode("b", "Watch B"))
    }
}

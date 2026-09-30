package com.sza.fastmediasorter.domain.usecase.wear

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.PhoneClipboardSendOutcome
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * S3920: a text no node accepted can never be answered, so the round trip must end at once
 * instead of spending the whole ack wait on a spinner.
 *
 * Robolectric because the payload reads Build.MODEL, which the plain JVM stub leaves null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class SendClipboardTextToWatchUseCaseTest {

    private val repository = mockk<WearableDataLayerRepository>()
    private val useCase = SendClipboardTextToWatchUseCase(repository, Gson())

    @Test
    fun `every send failing returns no connected watch without waiting`() = runTest {
        coEvery { repository.getConnectedNodes() } returns NODES
        coEvery { repository.sendMessage(any(), any(), any()) } throws IOException("node gone")

        val outcome = useCase(TEXT)

        assertEquals(PhoneClipboardSendOutcome.NoConnectedWatch, outcome)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `one accepted send still waits for the answer`() = runTest {
        coEvery { repository.getConnectedNodes() } returns NODES
        coEvery { repository.sendMessage("a", any(), any()) } throws IOException("node gone")
        coEvery { repository.sendMessage("b", any(), any()) } returns Unit

        val outcome = useCase(TEXT)

        assertEquals(PhoneClipboardSendOutcome.WatchDidNotAnswer, outcome)
        assertEquals(ACK_TIMEOUT_MS, testScheduler.currentTime)
    }

    private companion object {
        const val ACK_TIMEOUT_MS = 15_000L
        const val TEXT = "hello watch"
        val NODES = listOf(WearNode("a", "Watch A"), WearNode("b", "Watch B"))
    }
}

package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.repository.WearStreamPinsRepository
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearStreamPinDeltaItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S2497: Tests for SendStreamPinsDeltaUseCase verifying sending payload to connected phone.
 */
class SendStreamPinsDeltaUseCaseTest {

    private val context: Context = mockk(relaxed = true)
    private val nodeClient: NodeClient = mockk(relaxed = true)
    private val messageClient: MessageClient = mockk(relaxed = true)
    private val streamPinsRepo: WearStreamPinsRepository = mockk(relaxed = true)
    private val gson: Gson = Gson()

    @Before
    fun setUp() {
        mockkStatic(Wearable::class)
        every { Wearable.getNodeClient(any<Context>()) } returns nodeClient
        every { Wearable.getMessageClient(any<Context>()) } returns messageClient
    }

    @After
    fun tearDown() {
        unmockkStatic(Wearable::class)
    }

    @Test
    fun `sendStreamPinsDeltaUseCase sends pending deltas and clears on success`() = runBlocking {
        val useCase = SendStreamPinsDeltaUseCase(streamPinsRepo, context, gson)

        val pending = listOf(
            WearStreamPinDeltaItem(urlOrIdentity = "https://example.com/s1", isPinned = true, changedAt = 1000L)
        )
        coEvery { streamPinsRepo.getPendingDelta() } returns pending

        val node = mockk<Node> { every { id } returns "phone-node-1" }
        every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(node))
        every { messageClient.sendMessage("phone-node-1", WearDataLayerPaths.STREAM_PINS_DELTA, any()) } returns Tasks.forResult(1)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        verify(exactly = 1) { messageClient.sendMessage("phone-node-1", WearDataLayerPaths.STREAM_PINS_DELTA, any()) }
        coVerify(exactly = 1) { streamPinsRepo.clearPendingDelta() }
    }

    @Test
    fun `sendStreamPinsDeltaUseCase does not clear pending deltas if send fails`() = runBlocking {
        val useCase = SendStreamPinsDeltaUseCase(streamPinsRepo, context, gson)

        val pending = listOf(
            WearStreamPinDeltaItem(urlOrIdentity = "https://example.com/s1", isPinned = true, changedAt = 1000L)
        )
        coEvery { streamPinsRepo.getPendingDelta() } returns pending

        val node = mockk<Node> { every { id } returns "phone-node-1" }
        every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(node))
        every { messageClient.sendMessage("phone-node-1", WearDataLayerPaths.STREAM_PINS_DELTA, any()) } returns Tasks.forException(RuntimeException("Network error"))

        val result = useCase()

        assertTrue(result.isFailure)
        verify(exactly = 1) { messageClient.sendMessage("phone-node-1", WearDataLayerPaths.STREAM_PINS_DELTA, any()) }
        coVerify(exactly = 0) { streamPinsRepo.clearPendingDelta() }
    }

    @Test
    fun `sendStreamPinsDeltaUseCase does nothing when pending is empty`() = runBlocking {
        val useCase = SendStreamPinsDeltaUseCase(streamPinsRepo, context, gson)

        coEvery { streamPinsRepo.getPendingDelta() } returns emptyList()

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        verify(exactly = 0) { messageClient.sendMessage(any(), any(), any()) }
        coVerify(exactly = 0) { streamPinsRepo.clearPendingDelta() }
    }
}


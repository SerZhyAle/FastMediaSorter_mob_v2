package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearSyncEvents
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1799: outcome resolution of the phone-side send - watch unavailable, delivered (stored vs
 * updated), and the correlation rule that a foreign requestId never satisfies the wait.
 */
class SendStreamToWatchUseCaseTest {

    private class FakeWearableRepository(
        var nodes: List<WearNode> = emptyList()
    ) : WearableDataLayerRepository {
        val sentMessages = mutableListOf<Pair<String, ByteArray>>()

        override suspend fun getConnectedNodes(): List<WearNode> = nodes
        override suspend fun putDataItem(path: String, payload: ByteArray) = Unit
        override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) {
            sentMessages.add(path to data)
        }

        override suspend fun putEnvelopeDataItem(path: String, envelope: WearEventEnvelope) = Unit
    }

    private val gson = Gson()
    private val envelopeCodec = WearEventEnvelopeCodec()

    private fun useCase(repository: FakeWearableRepository, timeoutMs: Long) =
        SendStreamToWatchUseCase(repository, gson).apply { ackTimeoutMs = timeoutMs }

    private fun sentRequestId(repository: FakeWearableRepository): String {
        val (path, bytes) = repository.sentMessages.single()
        assertEquals(WearDataLayerPaths.STREAM_TRANSFER, path)
        val envelope = envelopeCodec.decode(bytes)
        return gson.fromJson(
            envelope.data.decodeToString(),
            WearStreamTransferPayload::class.java
        ).requestId
    }

    private suspend fun awaitSend(repository: FakeWearableRepository) =
        withTimeout(SEND_WAIT_MS) {
            while (repository.sentMessages.isEmpty()) delay(POLL_MS)
        }

    @Test
    fun `no connected node resolves WatchUnavailable without sending`() = runBlocking {
        val repository = FakeWearableRepository(nodes = emptyList())
        val outcome = useCase(repository, timeoutMs = SHORT_TIMEOUT_MS)("t", "https://u", "AUDIO")
        assertEquals(SendStreamToWatchUseCase.Outcome.WatchUnavailable, outcome)
        assertTrue(repository.sentMessages.isEmpty())
    }

    @Test
    fun `matching stored ack resolves Delivered`() = runBlocking {
        val repository = FakeWearableRepository(nodes = listOf(WearNode("n1", "Watch")))
        val result = async { useCase(repository, timeoutMs = LONG_TIMEOUT_MS)("t", "https://u", "AUDIO") }
        awaitSend(repository)
        WearSyncEvents.emitStreamTransferAck(
            WearStreamTransferAck(sentRequestId(repository), WearStreamTransferAck.OUTCOME_STORED)
        )
        assertEquals(SendStreamToWatchUseCase.Outcome.Delivered(updated = false), result.await())
    }

    @Test
    fun `matching updated ack resolves Delivered updated`() = runBlocking {
        val repository = FakeWearableRepository(nodes = listOf(WearNode("n1", "Watch")))
        val result = async { useCase(repository, timeoutMs = LONG_TIMEOUT_MS)("t", "https://u", "AUDIO") }
        awaitSend(repository)
        WearSyncEvents.emitStreamTransferAck(
            WearStreamTransferAck(sentRequestId(repository), WearStreamTransferAck.OUTCOME_UPDATED)
        )
        assertEquals(SendStreamToWatchUseCase.Outcome.Delivered(updated = true), result.await())
    }

    @Test
    fun `foreign requestId does not satisfy the wait - NoReply`() = runBlocking {
        val repository = FakeWearableRepository(nodes = listOf(WearNode("n1", "Watch")))
        val result = async { useCase(repository, timeoutMs = SHORT_TIMEOUT_MS)("t", "https://u", "AUDIO") }
        awaitSend(repository)
        WearSyncEvents.emitStreamTransferAck(
            WearStreamTransferAck("some-other-request", WearStreamTransferAck.OUTCOME_STORED)
        )
        assertEquals(SendStreamToWatchUseCase.Outcome.NoReply, result.await())
    }

    @Test
    fun `error ack resolves Error with the watch's message`() = runBlocking {
        val repository = FakeWearableRepository(nodes = listOf(WearNode("n1", "Watch")))
        val result = async { useCase(repository, timeoutMs = LONG_TIMEOUT_MS)("t", "https://u", "AUDIO") }
        awaitSend(repository)
        WearSyncEvents.emitStreamTransferAck(
            WearStreamTransferAck(
                sentRequestId(repository),
                WearStreamTransferAck.OUTCOME_ERROR,
                message = "store broken"
            )
        )
        assertEquals(SendStreamToWatchUseCase.Outcome.Error("store broken"), result.await())
    }

    private companion object {
        const val SHORT_TIMEOUT_MS = 400L
        const val LONG_TIMEOUT_MS = 5_000L
        const val SEND_WAIT_MS = 2_000L
        const val POLL_MS = 10L
    }
}

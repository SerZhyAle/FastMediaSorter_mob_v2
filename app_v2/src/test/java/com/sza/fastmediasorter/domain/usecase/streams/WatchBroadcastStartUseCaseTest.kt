package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastEndpointProbe
import com.sza.fastmediasorter.domain.model.BroadcastStartState
import com.sza.fastmediasorter.domain.repository.BroadcastPendingStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchBroadcastStartUseCaseTest {

    private val probe = mockk<BroadcastEndpointProbe>()
    private val pending = FakePendingStore()

    private class FakePendingStore : BroadcastPendingStore {
        val rows = mutableMapOf<String, Long>()
        override suspend fun untilMs(streamId: String) = rows[streamId]
        override suspend fun put(streamId: String, untilMs: Long) {
            rows[streamId] = untilMs
        }
        override suspend fun clear(streamId: String) {
            rows.remove(streamId)
        }
    }

    @Test
    fun notFoundThenOk_waitsThenStarts() = runTest {
        pending.rows[ROW] = FUTURE_MS
        every { probe.relayStatus(any()) } returnsMany listOf(404, 200)
        val watch = WatchBroadcastStartUseCase(probe, pending, StandardTestDispatcher(testScheduler))

        val states = watch(ROW, ENDPOINTS).toList()

        assertEquals(listOf(BroadcastStartState.Waiting(FUTURE_MS), BroadcastStartState.Started), states)
        assertEquals(emptyMap<String, Long>(), pending.rows)
    }

    @Test
    fun passedExpiry_expiresWithoutAProbe() = runTest {
        pending.rows[ROW] = PAST_MS
        val watch = WatchBroadcastStartUseCase(probe, pending, StandardTestDispatcher(testScheduler))

        val states = watch(ROW, ENDPOINTS).toList()

        assertEquals(listOf(BroadcastStartState.Expired), states)
        verify(exactly = 0) { probe.relayStatus(any()) }
        assertEquals(emptyMap<String, Long>(), pending.rows)
    }

    @Test
    fun noRelayEndpoint_expires() = runTest {
        pending.rows[ROW] = FUTURE_MS
        val watch = WatchBroadcastStartUseCase(probe, pending, StandardTestDispatcher(testScheduler))

        val states = watch(ROW, null).toList()

        assertEquals(listOf(BroadcastStartState.Expired), states)
        verify(exactly = 0) { probe.relayStatus(any()) }
    }

    private companion object {
        const val ROW = "row-1"
        const val FUTURE_MS = 4_070_908_800_000L
        const val PAST_MS = 978_307_200_000L
        val ENDPOINTS = BroadcastEndpointCodec.encode(
            listOf(
                BroadcastEndpointDto(
                    url = "https://exchange.example.org:8443/v2/b/AbCdEfGh12/stream",
                    transport = "RELAY",
                    certFingerprint = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU",
                ),
            ),
        )
    }
}

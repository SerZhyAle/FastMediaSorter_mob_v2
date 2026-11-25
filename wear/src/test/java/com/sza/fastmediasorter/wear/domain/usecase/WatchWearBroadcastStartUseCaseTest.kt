package com.sza.fastmediasorter.wear.domain.usecase

import com.google.gson.JsonParser
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPassStore
import com.sza.fastmediasorter.wear.data.exchange.WearRelayStatusProbe
import com.sza.fastmediasorter.wear.data.exchange.WearStoredBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastStartState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** S4692, LIVE-BROADCAST item S: the watch waits for a pending link's start, and only while somebody looks. */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchWearBroadcastStartUseCaseTest {

    private val passStore: WearBroadcastPassStore = mockk()
    private val probe: WearRelayStatusProbe = mockk()

    @Test
    fun `waiting turns into started when the relay answers 200`() = runTest {
        coEvery { passStore.get(URL) } returns stored(PENDING)
        every { probe.status(any()) } returnsMany listOf(404, 503, 200)

        val states = useCase(this).invoke(URL).toList()

        assertEquals(
            listOf(waiting, waiting, WearBroadcastStartState.Started),
            states,
        )
        verify(exactly = 3) { probe.status(any()) }
    }

    @Test
    fun `a protected relay answering 401 has started`() = runTest {
        coEvery { passStore.get(URL) } returns stored(PENDING)
        every { probe.status(any()) } returns 401

        assertEquals(listOf(WearBroadcastStartState.Started), useCase(this).invoke(URL).toList())
    }

    @Test
    fun `waiting ends expired at until with no probe after it`() = runTest {
        coEvery { passStore.get(URL) } returns stored(PENDING)
        every { probe.status(any()) } returns 404

        val states = useCase(this, startMs = UNTIL_MS - PERIOD_MS - 1).invoke(URL).toList()

        assertEquals(listOf(waiting, waiting, WearBroadcastStartState.Expired), states)
        verify(exactly = 2) { probe.status(any()) }
    }

    @Test
    fun `one probe per period and none once the collector is gone`() = runTest {
        coEvery { passStore.get(URL) } returns stored(PENDING)
        every { probe.status(any()) } returns 404
        val job = launch { useCase(this@runTest).invoke(URL).collect {} }

        runCurrent()
        verify(exactly = 1) { probe.status(any()) }
        advanceTimeBy(PERIOD_MS - 1)
        verify(exactly = 1) { probe.status(any()) }
        advanceTimeBy(1)
        runCurrent()
        verify(exactly = 2) { probe.status(any()) }

        job.cancel()
        advanceTimeBy(PERIOD_MS * 4)
        verify(exactly = 2) { probe.status(any()) }
    }

    @Test
    fun `a descriptor without pending has nothing to wait on`() = runTest {
        coEvery { passStore.get(URL) } returns stored(PLAIN)

        assertEquals(listOf(WearBroadcastStartState.Expired), useCase(this).invoke(URL).toList())
        verify(exactly = 0) { probe.status(any()) }
    }

    @Test
    fun `a channel with no stored descriptor has nothing to wait on`() = runTest {
        coEvery { passStore.get(URL) } returns null

        assertEquals(listOf(WearBroadcastStartState.Expired), useCase(this).invoke(URL).toList())
        verify(exactly = 0) { probe.status(any()) }
    }

    private val waiting = WearBroadcastStartState.Waiting(UNTIL_MS)

    private fun useCase(scope: TestScope, startMs: Long = START_MS): WatchWearBroadcastStartUseCase {
        val scheduler = scope.testScheduler
        return WatchWearBroadcastStartUseCase(
            passStore = passStore,
            probe = probe,
            nowMs = { startMs + scheduler.currentTime },
            ioDispatcher = StandardTestDispatcher(scheduler),
        )
    }

    private fun stored(json: String) =
        WearStoredBroadcast(broadcastId = "b1", descriptor = JsonParser.parseString(json).asJsonObject, pass = null)

    private companion object {
        const val URL = "https://ex.example/v2/b/b1/stream"
        const val PERIOD_MS = WatchWearBroadcastStartUseCase.PROBE_PERIOD_MS
        const val UNTIL_MS = 1_793_556_000_000L
        const val START_MS = UNTIL_MS - 3_600_000L
        const val RELAY = """{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY","mode":"AUDIO_ONLY"}"""
        const val PENDING =
            """{"mode":"AUDIO_ONLY","endpoints":[$RELAY],"pending":{"until":"2026-11-01T18:00:00Z"}}"""
        const val PLAIN = """{"mode":"AUDIO_ONLY","endpoints":[$RELAY]}"""
    }
}

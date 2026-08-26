package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.domain.networkmonitor.HostProbe
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeUnavailability
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TraceRouteUseCaseTest {

    // mockk, not mockito: mockito-kotlin is declared nowhere in this module and 202 of the suite's
    // 204 mocking test files already use mockk, so this file was the outlier, not the rule (S2035).
    private val historyRepository: NetworkMeasurementHistoryRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun `path where every hop answers`() = runTest(testDispatcher) {
        val fakeProbe = object : HostProbe {
            override suspend fun probe(host: String, timeoutMillis: Long, ttl: Int?): HostProbeResult {
                return when (ttl) {
                    1 -> HostProbeResult.HopAnswered("192.168.1.1", 2.0)
                    2 -> HostProbeResult.HopAnswered("10.0.0.1", 10.0)
                    3 -> HostProbeResult.Reached(25.0, "8.8.8.8")
                    else -> HostProbeResult.NotReached
                }
            }
        }
        val useCase = TraceRouteUseCase(fakeProbe, historyRepository, testDispatcher)
        val states = useCase("8.8.8.8", "Wi-Fi", maxHops = 5).toList()

        val finished = states.last() as TraceRouteState.Finished
        assertTrue(finished.reachedTarget)
        assertEquals(3, finished.hops.size)
    }

    @Test
    fun `path with a silent hop in the middle does not truncate the walk`() = runTest(testDispatcher) {
        val fakeProbe = object : HostProbe {
            override suspend fun probe(host: String, timeoutMillis: Long, ttl: Int?): HostProbeResult {
                return when (ttl) {
                    1 -> HostProbeResult.HopAnswered("192.168.1.1", 2.0)
                    2 -> HostProbeResult.NotReached // silent hop
                    3 -> HostProbeResult.Reached(20.0, "8.8.8.8")
                    else -> HostProbeResult.NotReached
                }
            }
        }
        val useCase = TraceRouteUseCase(fakeProbe, historyRepository, testDispatcher)
        val states = useCase("8.8.8.8", "Wi-Fi", maxHops = 5).toList()

        val finished = states.last() as TraceRouteState.Finished
        assertTrue(finished.reachedTarget)
        assertEquals(3, finished.hops.size)
        assertEquals(HostProbeResult.NotReached, finished.hops[1])
    }

    @Test
    fun `path where target answers before cap stops immediately`() = runTest(testDispatcher) {
        val fakeProbe = object : HostProbe {
            override suspend fun probe(host: String, timeoutMillis: Long, ttl: Int?): HostProbeResult {
                return if (ttl == 1) HostProbeResult.Reached(1.5, "192.168.1.1") else HostProbeResult.NotReached
            }
        }
        val useCase = TraceRouteUseCase(fakeProbe, historyRepository, testDispatcher)
        val states = useCase("192.168.1.1", "Wi-Fi", maxHops = 30).toList()

        val finished = states.last() as TraceRouteState.Finished
        assertTrue(finished.reachedTarget)
        assertEquals(1, finished.hops.size)
    }

    @Test
    fun `run where mechanism reports not-measurable at first hop stops ladder`() = runTest(testDispatcher) {
        val fakeProbe = object : HostProbe {
            override suspend fun probe(host: String, timeoutMillis: Long, ttl: Int?): HostProbeResult {
                return HostProbeResult.NotMeasurable(
                    HostProbeUnavailability.MECHANISM_UNAVAILABLE,
                    "Process launch failed"
                )
            }
        }
        val useCase = TraceRouteUseCase(fakeProbe, historyRepository, testDispatcher)
        val states = useCase("8.8.8.8", "Wi-Fi", maxHops = 10).toList()

        val finished = states.last() as TraceRouteState.Finished
        assertFalse(finished.reachedTarget)
        assertEquals(1, finished.hops.size)
        assertTrue(finished.hops[0] is HostProbeResult.NotMeasurable)
    }

    @Test
    fun `cancelled run stops the hop ladder before the cap`() = runTest(testDispatcher) {
        var probeCalls = 0
        val fakeProbe = object : HostProbe {
            override suspend fun probe(host: String, timeoutMillis: Long, ttl: Int?): HostProbeResult {
                probeCalls++
                // The probe must really suspend: without it UnconfinedTestDispatcher walks the whole
                // ladder to Finished inside launch, so cancelAndJoin() would cancel a finished coroutine
                // and the test would pass even if the use case ignored cancellation entirely (S2038).
                delay(HOP_DELAY_MS)
                return HostProbeResult.HopAnswered("10.0.0.$ttl", 5.0)
            }
        }
        val useCase = TraceRouteUseCase(fakeProbe, historyRepository, testDispatcher)
        val collected = mutableListOf<TraceRouteState>()

        val job = launch {
            useCase("8.8.8.8", "Wi-Fi", maxHops = TRACE_ROUTE_CAP).collect { state ->
                collected.add(state)
            }
        }
        advanceTimeBy(HOP_DELAY_MS * HOPS_BEFORE_CANCEL + 1)
        job.cancelAndJoin()

        assertEquals(HOPS_BEFORE_CANCEL, collected.filterIsInstance<TraceRouteState.Hop>().size)
        assertFalse(collected.any { it is TraceRouteState.Finished })
        assertTrue(probeCalls <= MAX_PROBE_CALLS_BEFORE_CANCEL)
    }

    private companion object {
        const val HOP_DELAY_MS = 10L
        const val HOPS_BEFORE_CANCEL = 3

        /** Hop 4 has started probing when the cancel lands, so one extra call is expected, no more. */
        const val MAX_PROBE_CALLS_BEFORE_CANCEL = 4
        const val TRACE_ROUTE_CAP = 30
    }
}

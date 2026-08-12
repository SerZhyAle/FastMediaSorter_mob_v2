package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.data.networkmonitor.ExternalIpDataSource
import com.sza.fastmediasorter.data.networkmonitor.ExternalIpResult
import com.sza.fastmediasorter.data.networkmonitor.RouterProbe
import com.sza.fastmediasorter.data.networkmonitor.RouterWanAddress
import com.sza.fastmediasorter.data.networkmonitor.RouterWanAddressDataSource
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * S1433: off-device coverage of the CGNAT verdict.
 *
 * The verdict is a comparison of two addresses, so it is decided entirely by what the two data sources
 * return - which makes it provable without a router, and worth proving: strategic §7 records that a false
 * CGNAT verdict is a stated risk, and it is invisible on the developer's own network.
 */
class ResolveExternalIpUseCaseTest {

    private val externalIpDataSource = mockk<ExternalIpDataSource>()
    private val routerWanAddressDataSource = mockk<RouterWanAddressDataSource>()
    private val historyRepository = mockk<NetworkMeasurementHistoryRepository>(relaxed = true)

    private fun useCase() = ResolveExternalIpUseCase(
        externalIpDataSource = externalIpDataSource,
        routerWanAddressDataSource = routerWanAddressDataSource,
        historyRepository = historyRepository,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private suspend fun resolvedState(): ExternalIpState.Resolved =
        useCase()(NETWORK_LABEL).toList().filterIsInstance<ExternalIpState.Resolved>().single()

    @Test
    fun `a silent router yields unknown, never a CGNAT claim`() = runTest {
        coEvery { externalIpDataSource.resolve() } returns ExternalIpResult.Resolved(ECHOED_ADDRESS)
        coEvery { routerWanAddressDataSource.resolve() } returns RouterWanAddress.Unanswered

        assertEquals(CgnatVerdict.Unknown, resolvedState().verdict)
    }

    @Test
    fun `matching router and echo addresses mean a direct connection`() = runTest {
        coEvery { externalIpDataSource.resolve() } returns ExternalIpResult.Resolved(ECHOED_ADDRESS)
        coEvery { routerWanAddressDataSource.resolve() } returns
            RouterWanAddress.Known(ECHOED_ADDRESS, RouterProbe.NAT_PMP)

        assertEquals(CgnatVerdict.Direct, resolvedState().verdict)
    }

    @Test
    fun `differing router and echo addresses mean CGNAT is likely`() = runTest {
        coEvery { externalIpDataSource.resolve() } returns ExternalIpResult.Resolved(ECHOED_ADDRESS)
        coEvery { routerWanAddressDataSource.resolve() } returns
            RouterWanAddress.Known(ROUTER_ADDRESS, RouterProbe.UPNP)

        assertEquals(CgnatVerdict.LikelyCgnat, resolvedState().verdict)
    }

    @Test
    fun `no echo answer ends unavailable and never asks the router`() = runTest {
        coEvery { externalIpDataSource.resolve() } returns ExternalIpResult.Unavailable

        val states = useCase()(NETWORK_LABEL).toList()

        assertEquals(ExternalIpState.Unavailable, states.last())
        // With no address to compare against, probing the gateway is traffic that cannot change anything.
        coVerify(exactly = 0) { routerWanAddressDataSource.resolve() }
    }

    @Test
    fun `the recorded history row never carries the address`() = runTest {
        coEvery { externalIpDataSource.resolve() } returns ExternalIpResult.Resolved(ECHOED_ADDRESS)
        coEvery { routerWanAddressDataSource.resolve() } returns
            RouterWanAddress.Known(ROUTER_ADDRESS, RouterProbe.UPNP)
        val recorded = slot<NetworkMeasurement>()
        coEvery { historyRepository.record(capture(recorded)) } returns Unit

        useCase()(NETWORK_LABEL).toList()

        // Strategic §7: the verdict is history-worthy, the address is not.
        assertFalse(recorded.captured.resultText.contains(ECHOED_ADDRESS))
        assertFalse(recorded.captured.resultText.contains(ROUTER_ADDRESS))
    }

    private companion object {
        // Documentation ranges (RFC 5737) - never routable, so a stray call could not reach them.
        const val ECHOED_ADDRESS = "203.0.113.7"
        const val ROUTER_ADDRESS = "198.51.100.4"
        const val NETWORK_LABEL = "Wi-Fi"
    }
}

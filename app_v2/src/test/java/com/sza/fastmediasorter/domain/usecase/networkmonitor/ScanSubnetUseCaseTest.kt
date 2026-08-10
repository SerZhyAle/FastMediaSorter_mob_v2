package com.sza.fastmediasorter.domain.usecase.networkmonitor

import android.content.Context
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import com.sza.fastmediasorter.domain.usecase.DiscoverNetworkResourcesUseCase
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1433: off-device coverage of the subnet scan's bounds.
 *
 * Only the refusal paths are exercised here, and deliberately so: they are the ones that decide whether a
 * scan happens at all, they run before a single packet is sent, and strategic §7 records the unbounded
 * sweep as the risk worth holding a line against - a corporate network reads it as an attack.
 */
class ScanSubnetUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val prober = mockk<DiscoverNetworkResourcesUseCase>(relaxed = true)
    private val historyRepository = mockk<NetworkMeasurementHistoryRepository>(relaxed = true)

    private fun useCase() = ScanSubnetUseCase(
        context = context,
        prober = prober,
        historyRepository = historyRepository,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `a range above the cap is refused rather than silently truncated`() = runTest {
        val states = useCase()(
            SubnetScanTarget.AddressRange(firstAddress = "10.0.0.1", lastAddress = "10.0.255.254"),
            NETWORK_LABEL,
        ).toList()

        val refusal = states.filterIsInstance<SubnetScanState.RangeTooLarge>().single()
        assertEquals(EXPECTED_CAP, refusal.cap)
        assertTrue(refusal.requested > EXPECTED_CAP)
    }

    @Test
    fun `an over-cap range probes nothing at all`() = runTest {
        useCase()(
            SubnetScanTarget.AddressRange(firstAddress = "10.0.0.1", lastAddress = "10.0.255.254"),
            NETWORK_LABEL,
        ).toList()

        // Truncating to the first 1024 addresses would report "no hosts" about a network barely looked at,
        // so the contract is refusal - which means the prober must never be reached.
        verify { prober wasNot Called }
    }

    @Test
    fun `a reversed range is refused as invalid`() = runTest {
        val states = useCase()(
            SubnetScanTarget.AddressRange(firstAddress = "192.168.1.200", lastAddress = "192.168.1.10"),
            NETWORK_LABEL,
        ).toList()

        assertTrue(states.any { it is SubnetScanState.RangeInvalid })
        verify { prober wasNot Called }
    }

    private companion object {
        const val EXPECTED_CAP = 1024
        const val NETWORK_LABEL = "Wi-Fi"
    }
}

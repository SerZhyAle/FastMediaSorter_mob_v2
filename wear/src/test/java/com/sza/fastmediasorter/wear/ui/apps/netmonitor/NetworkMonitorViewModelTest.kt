package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import android.content.Context
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkCapabilities
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkTransport
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkMonitorRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * S3952: the external address is looked up once per link however often the screen re-emits, a link
 * change cancels the older lookup, and a Wi-Fi reading enters the signal window once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkMonitorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val snapshots = MutableSharedFlow<WearNetworkSnapshot>(extraBufferCapacity = BUFFER)
    private val repository: WearNetworkMonitorRepository = mockk()
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.capabilities() } returns WearNetworkCapabilities(
            hasWifi = true,
            hasMobile = true,
            hasBluetooth = false,
            hasLocation = false,
        )
        every { repository.permissionsGranted() } returns true
        every { repository.snapshots() } returns snapshots
        coEvery { repository.probeReachability(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `one link is looked up once even when the lookup answers null`() = runTest(dispatcher) {
        coEvery { repository.resolveExternalIp() } returns null
        val viewModel = observed()

        repeat(SNAPSHOT_COUNT) { tick ->
            snapshots.emit(snapshot(WearNetworkTransport.Wifi, recordedAt = tick.toLong()))
            advanceUntilIdle()
        }
        viewModel.probeConnection()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.resolveExternalIp() }
    }

    @Test
    fun `a link change cancels the older lookup`() = runTest(dispatcher) {
        val wifiAnswer = CompletableDeferred<String?>()
        var calls = 0
        coEvery { repository.resolveExternalIp() } coAnswers {
            calls++
            if (calls == 1) wifiAnswer.await() else CELLULAR_IP
        }
        val viewModel = observed()

        snapshots.emit(snapshot(WearNetworkTransport.Wifi, recordedAt = 1L))
        advanceUntilIdle()
        snapshots.emit(snapshot(WearNetworkTransport.Cellular, recordedAt = 2L))
        advanceUntilIdle()
        wifiAnswer.complete(WIFI_IP)
        advanceUntilIdle()

        assertEquals(CELLULAR_IP, viewModel.uiState.value.externalIp)
    }

    @Test
    fun `a reading enters the signal window once whatever else changes`() = runTest(dispatcher) {
        coEvery { repository.resolveExternalIp() } returns null
        val viewModel = observed()

        snapshots.emit(snapshot(WearNetworkTransport.Wifi, recordedAt = 1L))
        advanceUntilIdle()
        viewModel.probeConnection()
        advanceUntilIdle()
        viewModel.probeConnection()
        advanceUntilIdle()
        assertEquals(listOf(SIGNAL_DBM), viewModel.uiState.value.signalHistory)

        viewModel.restartSignalWindow()
        advanceUntilIdle()
        assertEquals(emptyList<Int>(), viewModel.uiState.value.signalHistory)
    }

    private fun TestScope.observed(): NetworkMonitorViewModel {
        val viewModel = NetworkMonitorViewModel(repository, context)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        return viewModel
    }

    private fun snapshot(transport: WearNetworkTransport, recordedAt: Long) = WearNetworkSnapshot(
        recordedAtMillis = recordedAt,
        activeTransport = transport,
        wifiNetworkName = null,
        wifiSignalDbm = SIGNAL_DBM,
        wifiLinkSpeedMbps = null,
        visibleWifiNetworks = null,
        hasMobileData = null,
        mobileOperator = null,
        isBluetoothEnabled = null,
        hasLocationProvider = null,
        hasInternet = true,
    )

    private companion object {
        const val BUFFER = 8
        const val SNAPSHOT_COUNT = 3
        const val SIGNAL_DBM = -60
        const val WIFI_IP = "203.0.113.1"
        const val CELLULAR_IP = "198.51.100.2"
    }
}

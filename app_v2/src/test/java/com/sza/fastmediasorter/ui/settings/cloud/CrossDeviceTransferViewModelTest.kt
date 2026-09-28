package com.sza.fastmediasorter.ui.settings.cloud

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.usecase.transfer.BuildTransferPayloadUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.CleanExpiredCrossDevicePacketsUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.GetPendingCrossDevicePacketsUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.ReceiveCrossDevicePacketUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.SendCrossDevicePacketUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CrossDeviceTransferViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `late result from cancelled refresh cannot replace newer packets`() = runTest {
        val getPendingPackets = mockk<GetPendingCrossDevicePacketsUseCase>()
        val staleResult = CompletableDeferred<Result<List<CrossDevicePacketManifest>>>()
        val stalePacket = mockk<CrossDevicePacketManifest>()
        val currentPacket = mockk<CrossDevicePacketManifest>()
        var requests = 0
        coEvery { getPendingPackets(any()) } coAnswers {
            requests++
            if (requests == 1) {
                withContext(NonCancellable) { staleResult.await() }
            } else {
                Result.success(listOf(currentPacket))
            }
        }
        val viewModel = CrossDeviceTransferViewModel(
            getPendingPackets = getPendingPackets,
            receivePacket = mockk<ReceiveCrossDevicePacketUseCase>(),
            cleanExpiredPackets = mockk<CleanExpiredCrossDevicePacketsUseCase>(),
            buildTransferPayload = mockk<BuildTransferPayloadUseCase>(),
            sendPacket = mockk<SendCrossDevicePacketUseCase>()
        )

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(currentPacket), viewModel.state.value.packets)

        staleResult.complete(Result.success(listOf(stalePacket)))
        advanceUntilIdle()
        assertEquals(listOf(currentPacket), viewModel.state.value.packets)
        assertFalse(viewModel.state.value.loading)
    }
}

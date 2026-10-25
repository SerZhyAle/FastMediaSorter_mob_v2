package com.sza.fastmediasorter.ui.settings.cloud

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueView
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DriveQueueSnapshots
import com.sza.fastmediasorter.domain.transfer.PollDemand
import com.sza.fastmediasorter.domain.usecase.transfer.BuildTransferPayloadUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.CleanExpiredCrossDevicePacketsUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.DeleteCrossDevicePacketUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.GetPendingCrossDevicePacketsUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.ReceiveCrossDevicePacketUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.SendCrossDevicePacketUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
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
        val identity = mockk<LocalDeviceIdentitySource>()
        coEvery { identity.current() } returns LocalDeviceIdentity("dev", "Phone")
        val staleResult = CompletableDeferred<Result<CrossDeviceQueueView>>()
        val stalePacket = mockk<CrossDevicePacketEntry>()
        val currentPacket = mockk<CrossDevicePacketEntry>()
        var requests = 0
        coEvery { getPendingPackets(any()) } coAnswers {
            requests++
            if (requests == 1) {
                withContext(NonCancellable) { staleResult.await() }
            } else {
                Result.success(CrossDeviceQueueView(listOf(currentPacket), emptyList()))
            }
        }
        val viewModel = CrossDeviceTransferViewModel(
            getPendingPackets = getPendingPackets,
            receivePacket = mockk<ReceiveCrossDevicePacketUseCase>(),
            cleanExpiredPackets = mockk<CleanExpiredCrossDevicePacketsUseCase>(),
            buildTransferPayload = mockk<BuildTransferPayloadUseCase>(),
            sendPacket = mockk<SendCrossDevicePacketUseCase>(),
            deletePacket = mockk<DeleteCrossDevicePacketUseCase>(),
            grantAppFolder = mockk(),
            identity = identity,
            settings = mockk<DriveChannelSettings>(relaxed = true),
            queue = mockk<DriveQueueSnapshots>(relaxed = true)
        )

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(currentPacket), viewModel.state.value.packets)

        staleResult.complete(Result.success(CrossDeviceQueueView(listOf(stalePacket), emptyList())))
        advanceUntilIdle()
        assertEquals(listOf(currentPacket), viewModel.state.value.packets)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `the open screen holds one active demand until it is hidden`() = runTest {
        val queue = mockk<DriveQueueSnapshots>(relaxed = true)
        val demand = mockk<AutoCloseable>(relaxed = true)
        every { queue.acquire(PollDemand.ACTIVE) } returns demand
        every { queue.snapshots } returns MutableSharedFlow()
        val viewModel = viewModel(queue = queue)

        viewModel.onVisible()
        viewModel.onVisible()
        viewModel.onHidden()

        verify(exactly = 1) { queue.acquire(PollDemand.ACTIVE) }
        verify(exactly = 1) { demand.close() }
    }

    @Test
    fun `the channel switch is written to the settings`() = runTest {
        val settings = mockk<DriveChannelSettings>(relaxed = true)
        val viewModel = viewModel(settings = settings)

        viewModel.setChannelEnabled(true)
        viewModel.setBackgroundPoll(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { settings.setEnabled(true) }
        coVerify(exactly = 1) { settings.setBackgroundPoll(false) }
    }

    @Test
    fun `a deleted packet is reported as removed`() = runTest {
        val deletePacket = mockk<DeleteCrossDevicePacketUseCase>()
        val entry = mockk<CrossDevicePacketEntry>(relaxed = true)
        coEvery { deletePacket(entry) } returns Result.success(Unit)
        val viewModel = viewModel(deletePacket = deletePacket)

        viewModel.delete(entry)
        runCurrent()

        assertEquals(CrossDeviceQueueNotice.Deleted, viewModel.state.value.notice)
    }

    private fun viewModel(
        settings: DriveChannelSettings = mockk(relaxed = true),
        queue: DriveQueueSnapshots = mockk(relaxed = true),
        deletePacket: DeleteCrossDevicePacketUseCase = mockk(relaxed = true)
    ): CrossDeviceTransferViewModel {
        val identity = mockk<LocalDeviceIdentitySource>()
        coEvery { identity.current() } returns LocalDeviceIdentity("dev", "Phone")
        val pending = mockk<GetPendingCrossDevicePacketsUseCase>()
        coEvery { pending(any()) } returns Result.success(CrossDeviceQueueView(emptyList(), emptyList()))
        return CrossDeviceTransferViewModel(
            getPendingPackets = pending,
            receivePacket = mockk(),
            cleanExpiredPackets = mockk(),
            buildTransferPayload = mockk(),
            sendPacket = mockk(),
            deletePacket = deletePacket,
            grantAppFolder = mockk(),
            identity = identity,
            settings = settings,
            queue = queue
        )
    }
}

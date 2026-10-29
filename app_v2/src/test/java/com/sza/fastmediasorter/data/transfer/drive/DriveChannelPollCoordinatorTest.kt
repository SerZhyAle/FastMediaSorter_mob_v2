package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.DriveConsentRequired
import com.sza.fastmediasorter.domain.model.transfer.DriveSignInRequired
import com.sza.fastmediasorter.domain.model.transfer.DriveTemporarilyUnavailable
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DrivePollState
import com.sza.fastmediasorter.domain.transfer.PollDemand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4231: one shared poll, its intervals, back-off and shutdown (DEVICE-EXCHANGE item CQ). */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveChannelPollCoordinatorTest {

    private val cycle = mockk<DriveChannelPollCycle>()
    private val enabled = MutableStateFlow(true)
    private val settings = mockk<DriveChannelSettings>(relaxed = true).also {
        coEvery { it.enabled } returns enabled
    }
    private val snapshot = CrossDeviceQueueSnapshot(emptyList(), emptyList(), 0L)

    @Test
    fun `intervals follow the strongest demand`() {
        val active = DriveChannelPollCoordinator.ACTIVE_INTERVAL_MS
        val foreground = DriveChannelPollCoordinator.FOREGROUND_INTERVAL_MS
        assertEquals(active, DriveChannelPollCoordinator.intervalFor(true, true, listOf(PollDemand.ACTIVE)))
        assertEquals(foreground, DriveChannelPollCoordinator.intervalFor(true, true, emptyList()))
        assertNull(DriveChannelPollCoordinator.intervalFor(true, false, emptyList()))
        assertNull(DriveChannelPollCoordinator.intervalFor(false, true, listOf(PollDemand.ACTIVE)))
    }

    @Test
    fun `two consumers share one listing every ten seconds`() = runTest {
        coEvery { cycle.run() } returns Result.success(snapshot)
        val coordinator = DriveChannelPollCoordinator(cycle, settings, { flowOf(false) }, backgroundScope)

        val first = coordinator.acquire(PollDemand.ACTIVE)
        val second = coordinator.acquire(PollDemand.ACTIVE)
        runCurrent()
        advanceTimeBy(TWENTY_FIVE_SECONDS)

        coVerify(exactly = 3) { cycle.run() }
        val polling = DrivePollState.Polling(DriveChannelPollCoordinator.ACTIVE_INTERVAL_MS)
        assertEquals(polling, coordinator.pollState.value)
        first.close()
        second.close()
    }

    @Test
    fun `a transient failure backs off from one second and recovers`() = runTest {
        var calls = 0
        coEvery { cycle.run() } answers {
            calls++
            if (calls == 1) Result.failure(DriveTemporarilyUnavailable(null)) else Result.success(snapshot)
        }
        val coordinator = DriveChannelPollCoordinator(cycle, settings, { flowOf(false) }, backgroundScope)

        coordinator.acquire(PollDemand.ACTIVE)
        runCurrent()
        assertTrue(coordinator.pollState.value is DrivePollState.BackingOff)
        advanceTimeBy(ONE_SECOND + 1)

        assertEquals(2, calls)
        assertTrue(coordinator.pollState.value is DrivePollState.Polling)
    }

    @Test
    fun `turning the channel off stops polling at once`() = runTest {
        coEvery { cycle.run() } returns Result.success(snapshot)
        val coordinator = DriveChannelPollCoordinator(cycle, settings, { flowOf(false) }, backgroundScope)

        coordinator.acquire(PollDemand.ACTIVE)
        runCurrent()
        enabled.value = false
        runCurrent()
        advanceTimeBy(TWENTY_FIVE_SECONDS)

        coVerify(exactly = 1) { cycle.run() }
        assertEquals(DrivePollState.Off, coordinator.pollState.value)
    }

    @Test
    fun `a lost sign-in stops the loop`() = runTest {
        coEvery { cycle.run() } returns Result.failure(DriveSignInRequired())
        val coordinator = DriveChannelPollCoordinator(cycle, settings, { flowOf(true) }, backgroundScope)

        coordinator.start()
        runCurrent()
        advanceTimeBy(TWENTY_FIVE_SECONDS)

        coVerify(exactly = 1) { cycle.run() }
        assertEquals(DrivePollState.SignInRequired, coordinator.pollState.value)
    }

    @Test
    fun `a missing app folder grant stops the loop`() = runTest {
        coEvery { cycle.run() } returns Result.failure(DriveConsentRequired())
        val coordinator = DriveChannelPollCoordinator(cycle, settings, { flowOf(true) }, backgroundScope)

        coordinator.start()
        runCurrent()
        advanceTimeBy(TWENTY_FIVE_SECONDS)

        coVerify(exactly = 1) { cycle.run() }
        assertEquals(DrivePollState.ConsentRequired, coordinator.pollState.value)
    }

    private companion object {
        const val ONE_SECOND = 1_000L
        const val TWENTY_FIVE_SECONDS = 25_000L
    }
}

package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStopwatchSessionRepository
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchUpdate
import com.sza.fastmediasorter.wear.domain.usecase.SyncWearStopwatchOngoingUseCase
import com.sza.fastmediasorter.wear.domain.usecase.UpdateWearStopwatchUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S3555: the view model hands every change to the session use case, asks for the notification
 * permission once per screen session, and re-syncs the indicator on open and after the answer.
 *
 * No test makes the screen visible, so the 50 ms repaint never starts - under virtual time an endless
 * repaint loop would keep `advanceUntilIdle` spinning forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearStopwatchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferencesRepository: WearPreferencesRepository = mockk(relaxed = true)
    private val session: WearStopwatchSessionRepository = mockk()
    private val updateStopwatch: UpdateWearStopwatchUseCase = mockk()
    private val syncOngoing: SyncWearStopwatchOngoingUseCase = mockk(relaxed = true)

    private val single = WearStopwatchState.initial(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferencesRepository.stopwatchParticipantCount } returns MutableStateFlow(1)
        every { preferencesRepository.stopwatchLastResult } returns MutableStateFlow("")
        every { session.session } returns MutableStateFlow(single)
        coEvery { session.current() } returns single
        coEvery { updateStopwatch(any(), any()) } returns WearStopwatchUpdate(single, indicatorBlocked = false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a start goes through the update use case`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onStartOrLap(0)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateStopwatch(any(), any()) }
    }

    @Test
    fun `a blocked indicator asks for the permission once per screen session`() = runTest(dispatcher) {
        coEvery { updateStopwatch(any(), any()) } returns WearStopwatchUpdate(single, indicatorBlocked = true)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onStartOrLap(0)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.askNotificationPermission)

        viewModel.onNotificationPermissionAsked()
        viewModel.onStartOrLap(0)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.askNotificationPermission)
    }

    @Test
    fun `opening the screen syncs the indicator once`() = runTest(dispatcher) {
        viewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncOngoing() }
    }

    @Test
    fun `the permission answer syncs the indicator again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNotificationPermissionResult()
        advanceUntilIdle()

        coVerify(exactly = 2) { syncOngoing() }
    }

    private fun viewModel() = WearStopwatchViewModel(preferencesRepository, session, updateStopwatch, syncOngoing)
}

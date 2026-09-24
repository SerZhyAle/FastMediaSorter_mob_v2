package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * S3529: Unit tests for WearStopwatchViewModel OngoingActivity lifecycle coordination.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearStopwatchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferencesRepository: WearPreferencesRepository = mockk(relaxed = true)
    private val ongoingNotificationManager: WearStopwatchOngoingNotificationManager = mockk(relaxed = true)

    private val participantCountFlow = MutableStateFlow(1)
    private val lastResultFlow = MutableStateFlow("")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferencesRepository.stopwatchParticipantCount } returns participantCountFlow
        every { preferencesRepository.stopwatchLastResult } returns lastResultFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starting stopwatch triggers showOngoing`() = runTest(dispatcher) {
        val viewModel = WearStopwatchViewModel(preferencesRepository, ongoingNotificationManager)
        advanceUntilIdle()

        viewModel.onStartOrLap(0)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.anyRunning)
        verify { ongoingNotificationManager.showOngoing(any()) }
    }

    @Test
    fun `stopping stopwatch triggers hideOngoing`() = runTest(dispatcher) {
        val viewModel = WearStopwatchViewModel(preferencesRepository, ongoingNotificationManager)
        advanceUntilIdle()

        viewModel.onStartOrLap(0)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.anyRunning)

        viewModel.onStopOrReset(0)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.anyRunning)
        verify { ongoingNotificationManager.hideOngoing() }
    }

    @Test
    fun `resetting all triggers hideOngoing`() = runTest(dispatcher) {
        val viewModel = WearStopwatchViewModel(preferencesRepository, ongoingNotificationManager)
        advanceUntilIdle()

        viewModel.onStartAll()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.anyRunning)

        viewModel.onResetAll()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.anyRunning)
        verify { ongoingNotificationManager.hideOngoing() }
    }
}

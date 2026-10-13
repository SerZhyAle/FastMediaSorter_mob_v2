package com.sza.fastmediasorter.ui.tourist

import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.domain.model.tourist.TouristCounterState
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.model.tourist.WatchHealthStats
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveTouristCounterUseCase
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveTouristDashboardUseCase
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveWatchTouristHealthUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * S3011: verifies that the Tourist dashboard ViewModel respects stepsAvailable from state.
 * S4145: the dashboard flow runs only while the state has a collector.
 * S4150: a watch tile is accepted only when the build offers watch health.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TouristInfoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val flow = MutableStateFlow(TouristDashboardState(stepsAvailable = false))
    private val observeTouristDashboardUseCase = mockk<ObserveTouristDashboardUseCase>(relaxed = true).also {
        every { it(any()) } returns flow
    }
    private val watchFlow = MutableStateFlow(WatchHealthStats())
    private val observeWatchHealth = mockk<ObserveWatchTouristHealthUseCase>().also {
        every { it() } returns watchFlow
    }
    private val observeCounter = mockk<ObserveTouristCounterUseCase>().also {
        every { it() } returns MutableStateFlow(TouristCounterState())
    }

    private fun newViewModel() =
        TouristInfoViewModel(observeTouristDashboardUseCase, SavedStateHandle(), observeWatchHealth, observeCounter)

    @Test
    fun `selectTile WATCH_HEART_RATE ignored when the build does not offer watch health`() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.selectTile(TouristTileType.WATCH_HEART_RATE)

        assertEquals(TouristTileType.SPEED, viewModel.state.value.focusedTile)
    }

    @Test
    fun `selectTile WATCH_HEART_RATE accepted when watch health is offered`() = runTest {
        watchFlow.value = WatchHealthStats(offered = true)
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.selectTile(TouristTileType.WATCH_HEART_RATE)

        assertEquals(TouristTileType.WATCH_HEART_RATE, viewModel.state.value.focusedTile)
    }

    @Test
    fun `selectTile STEPS ignored when stepsAvailable is false`() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.selectTile(TouristTileType.STEPS)

        assertEquals(TouristTileType.SPEED, viewModel.state.value.focusedTile)
    }

    @Test
    fun `selectTile STEPS accepted when stepsAvailable is true`() = runTest {
        flow.value = TouristDashboardState(stepsAvailable = true)
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.selectTile(TouristTileType.STEPS)

        assertEquals(TouristTileType.STEPS, viewModel.state.value.focusedTile)
    }

    @Test
    fun `selectTile ALTITUDE accepted unconditionally`() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.selectTile(TouristTileType.ALTITUDE)

        assertEquals(TouristTileType.ALTITUDE, viewModel.state.value.focusedTile)
    }

    @Test
    fun `selectTile PRESSURE ignored without a barometer`() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.selectTile(TouristTileType.PRESSURE)

        assertEquals(TouristTileType.SPEED, viewModel.state.value.focusedTile)
    }

    @Test
    fun `selectTile STOPWATCH accepted unconditionally`() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.selectTile(TouristTileType.STOPWATCH)

        assertEquals(TouristTileType.STOPWATCH, viewModel.state.value.focusedTile)
    }

    @Test
    fun `dashboard flow is not subscribed without a collector`() {
        newViewModel()

        verify(exactly = 0) { observeTouristDashboardUseCase(any()) }
    }
}

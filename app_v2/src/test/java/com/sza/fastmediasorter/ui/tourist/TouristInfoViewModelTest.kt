package com.sza.fastmediasorter.ui.tourist

import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveTouristDashboardUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * S3011: verifies that the Tourist dashboard ViewModel respects stepsAvailable from state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TouristInfoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val flow = MutableStateFlow(TouristDashboardState(stepsAvailable = false))
    private val observeTouristDashboardUseCase = mockk<ObserveTouristDashboardUseCase>(relaxed = true).also {
        every { it(any()) } returns flow
    }

    @Test
    fun `selectTile STEPS ignored when stepsAvailable is false`() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = TouristInfoViewModel(observeTouristDashboardUseCase, savedStateHandle)

        viewModel.selectTile(TouristTileType.STEPS)

        assertEquals(TouristTileType.SPEED, viewModel.state.value.focusedTile)
    }

    @Test
    fun `selectTile STEPS accepted when stepsAvailable is true`() {
        flow.value = TouristDashboardState(stepsAvailable = true)
        val savedStateHandle = SavedStateHandle()
        val viewModel = TouristInfoViewModel(observeTouristDashboardUseCase, savedStateHandle)

        viewModel.selectTile(TouristTileType.STEPS)

        assertEquals(TouristTileType.STEPS, viewModel.state.value.focusedTile)
    }

    @Test
    fun `selectTile ALTITUDE accepted unconditionally`() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = TouristInfoViewModel(observeTouristDashboardUseCase, savedStateHandle)

        viewModel.selectTile(TouristTileType.ALTITUDE)

        assertEquals(TouristTileType.ALTITUDE, viewModel.state.value.focusedTile)
    }
}

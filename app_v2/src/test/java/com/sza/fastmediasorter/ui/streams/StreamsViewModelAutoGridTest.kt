package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * S1154: the reversible VIDEO-filter -> GRID auto-switch in [StreamsViewModel]. Entering the VIDEO
 * filter remembers the current mode and forces GRID; leaving restores it; a deliberate in-filter toggle
 * updates the restore baseline; the auto mode is never persisted as the default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamsViewModelAutoGridTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun viewModel(): StreamsViewModel {
        val observeStreamSources = mockk<ObserveStreamSourcesUseCase>()
        every { observeStreamSources() } returns flowOf(emptyList())
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.getSettings() } returns flowOf(AppSettings())
        val favoritesUseCase = mockk<FavoritesUseCase>(relaxed = true)
        every { favoritesUseCase.observeFavoriteStreamUrls() } returns flowOf(emptySet())
        return StreamsViewModel(
            observeStreamSources = observeStreamSources,
            addStreamSource = mockk(relaxed = true),
            updateStreamSource = mockk(relaxed = true),
            importStreamPlaylist = mockk(relaxed = true),
            importStreamCatalog = mockk(relaxed = true),
            pinStreamSource = mockk(relaxed = true),
            unpinStreamSource = mockk(relaxed = true),
            reorderPinnedStream = mockk(relaxed = true),
            removeStreamSource = mockk(relaxed = true),
            recordStreamPlayOutcome = mockk(relaxed = true),
            getStreamSourceByUrl = mockk(relaxed = true),
            favoritesUseCase = favoritesUseCase,
            settingsRepository = settingsRepository,
            sessionStore = mockk(relaxed = true),
            networkContextAnalyzer = mockk(relaxed = true),
            streamFramePersistentStore = mockk(relaxed = true),
            streamTrackPreferenceUseCase = mockk(relaxed = true),
            streamResumeStateRepository = mockk(relaxed = true),
            applicationScope = CoroutineScope(dispatcherRule.testDispatcher),
            topicLabelProvider = mockk(relaxed = true),
        )
    }

    private val video = StreamsViewModel.MediaKindFilter.VIDEO
    private val all = StreamsViewModel.MediaKindFilter.ALL

    @Test
    fun `filter to VIDEO while in LIST switches to GRID`() = runTest(dispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(DisplayMode.LIST, vm.state.value.displayMode)

        vm.onFilter(mediaKind = video)
        advanceUntilIdle()

        assertEquals(DisplayMode.GRID, vm.state.value.displayMode)
    }

    @Test
    fun `leaving VIDEO restores the pre-video LIST mode`() = runTest(dispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onFilter(mediaKind = video)
        advanceUntilIdle()
        vm.onFilter(mediaKind = all)
        advanceUntilIdle()

        assertEquals(DisplayMode.LIST, vm.state.value.displayMode)
    }

    @Test
    fun `a manual LIST toggle while VIDEO is active survives leaving the filter`() =
        runTest(dispatcherRule.testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.onFilter(mediaKind = video)
            advanceUntilIdle()
            assertEquals(DisplayMode.GRID, vm.state.value.displayMode)

            vm.onToggleDisplayMode() // GRID -> LIST while VIDEO active updates the restore baseline
            advanceUntilIdle()
            assertEquals(DisplayMode.LIST, vm.state.value.displayMode)

            vm.onFilter(mediaKind = all)
            advanceUntilIdle()
            assertEquals(DisplayMode.LIST, vm.state.value.displayMode)
        }

    @Test
    fun `entering VIDEO while already GRID leaves GRID and restores GRID on leaving`() =
        runTest(dispatcherRule.testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.onToggleDisplayMode() // LIST -> GRID while filter is ALL (baseline untouched)
            advanceUntilIdle()
            assertEquals(DisplayMode.GRID, vm.state.value.displayMode)

            vm.onFilter(mediaKind = video)
            advanceUntilIdle()
            assertEquals(DisplayMode.GRID, vm.state.value.displayMode)

            vm.onFilter(mediaKind = all)
            advanceUntilIdle()
            assertEquals(DisplayMode.GRID, vm.state.value.displayMode)
        }
}

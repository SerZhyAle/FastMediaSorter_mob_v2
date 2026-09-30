package com.sza.fastmediasorter.wear.ui.player.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.ui.player.common.PlayerFileOperationsManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * S3893: the now-playing flag used to be cleared on viewModelScope inside onCleared, which lifecycle
 * 2.8 cancels before onCleared runs, so the clear never happened. Clearing through a real
 * ViewModelStore reproduces that order instead of calling onCleared on a live scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelClearTest {

    private val dispatcher = StandardTestDispatcher()
    private val nowPlayingRepository: WearNowPlayingRepository = mockk(relaxed = true)

    // A relaxed StateFlow never returns from collect, which the view model reads as a Nothing value.
    private val fileOperations: PlayerFileOperationsManager = mockk(relaxed = true) {
        every { operationResult } returns MutableStateFlow(null)
    }
    private val preferencesRepository: WearPreferencesRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferencesRepository.videoScaleMode } returns flowOf(VideoScaleMode.FIT)
        every { preferencesRepository.isSlideshowEnabled } returns flowOf(false)
        every { preferencesRepository.isShuffleEnabled } returns flowOf(false)
        every { preferencesRepository.isAnimationsDisabled } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `leaving the screen clears the now-playing flag`() = runTest(dispatcher) {
        val store = ViewModelStore()
        obtain(store)
        advanceUntilIdle()

        store.clear()
        advanceUntilIdle()

        coVerify(exactly = 1) { nowPlayingRepository.clearPlayingFlag() }
    }

    private fun CoroutineScope.obtain(store: ViewModelStore): VideoPlayerViewModel {
        val applicationScope = this
        val factory = viewModelFactory {
            initializer {
                VideoPlayerViewModel(
                    mediaRepository = mockk(relaxed = true),
                    selectedMediaManager = mockk(relaxed = true),
                    playbackSetManager = mockk(relaxed = true),
                    preferencesRepository = preferencesRepository,
                    downloadNetworkFile = mockk(relaxed = true),
                    endPhoneCameraSessionOnStreamError = mockk(relaxed = true),
                    exoPlayer = mockk(relaxed = true),
                    publishPlaybackStateUseCase = mockk(relaxed = true),
                    streamPlaybackSessionFactory = mockk(relaxed = true),
                    toggleFavoriteUseCase = mockk(relaxed = true),
                    toggleStreamPinUseCase = mockk(relaxed = true),
                    nowPlayingRepository = nowPlayingRepository,
                    fileOperations = fileOperations,
                    castManager = mockk(relaxed = true),
                    batteryWarningStore = mockk(relaxed = true),
                    savedStateHandle = SavedStateHandle(),
                    context = mockk(relaxed = true),
                    applicationScope = applicationScope
                )
            }
        }
        return ViewModelProvider(store, factory)[VideoPlayerViewModel::class.java]
    }
}

package com.sza.fastmediasorter.wear.ui.player.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSessionState
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.service.WearPlaybackService
import com.sza.fastmediasorter.wear.ui.player.common.PlayerFileOperationsManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
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
class AudioPlayerViewModelClearTest {

    private val dispatcher = StandardTestDispatcher()
    private val nowPlayingRepository: WearNowPlayingRepository = mockk(relaxed = true)

    // A relaxed StateFlow never returns from collect, which the view model reads as a Nothing value.
    private val fileOperations: PlayerFileOperationsManager = mockk(relaxed = true) {
        every { operationResult } returns MutableStateFlow(null)
    }
    private val preferencesRepository: WearPreferencesRepository = mockk(relaxed = true)
    private val exoPlayer: ExoPlayer = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferencesRepository.backgroundPlaybackEnabled } returns flowOf(true)
        every { preferencesRepository.isShuffleEnabled } returns flowOf(false)
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

    @Test
    fun `a screen that handed the sound to the service leaves the flag to it`() = runTest(dispatcher) {
        every { exoPlayer.isPlaying } returns true
        every { exoPlayer.currentMediaItem } returns MediaItem.Builder().setUri(mockk<Uri>(relaxed = true)).build()
        mockkObject(WearPlaybackService.Companion)
        mockkStatic(ContextCompat::class)
        try {
            every { WearPlaybackService.startIntent(any(), any(), any(), any(), any()) } returns mockk<Intent>()
            every { ContextCompat.startForegroundService(any(), any()) } just runs
            val store = ViewModelStore()
            val viewModel = obtain(store)
            advanceUntilIdle()

            viewModel.onHostStopped()
            store.clear()
            advanceUntilIdle()

            coVerify(exactly = 0) { nowPlayingRepository.clearPlayingFlag() }
        } finally {
            unmockkStatic(ContextCompat::class)
            unmockkObject(WearPlaybackService.Companion)
        }
    }

    private fun CoroutineScope.obtain(store: ViewModelStore): AudioPlayerViewModel {
        val applicationScope = this
        val factory = viewModelFactory {
            initializer {
                AudioPlayerViewModel(
                    mediaRepository = mockk(relaxed = true),
                    selectedMediaManager = mockk(relaxed = true),
                    playbackSetManager = mockk(relaxed = true),
                    downloadNetworkFile = mockk(relaxed = true),
                    exoPlayer = exoPlayer,
                    publishPlaybackStateUseCase = mockk(relaxed = true),
                    context = context,
                    toggleFavoriteUseCase = mockk(relaxed = true),
                    toggleStreamPinUseCase = mockk(relaxed = true),
                    resolveAlbumArt = mockk(relaxed = true),
                    preferencesRepository = preferencesRepository,
                    streamPlaybackSessionFactory = mockk(relaxed = true),
                    nowPlayingRepository = nowPlayingRepository,
                    backgroundSessionState = WearBackgroundSessionState(),
                    fileOperations = fileOperations,
                    castManager = mockk(relaxed = true),
                    applicationScope = applicationScope,
                    savedStateHandle = SavedStateHandle()
                )
            }
        }
        return ViewModelProvider(store, factory)[AudioPlayerViewModel::class.java]
    }
}

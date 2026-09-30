package com.sza.fastmediasorter.wear.ui.player.video

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ToggleFavoriteUseCase
import com.sza.fastmediasorter.wear.ui.player.common.PlayerFileOperationsManager
import io.mockk.coEvery
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
import java.io.IOException

/**
 * S3894: after paging a network set the star has to read and mark the file on screen. The manager
 * keeps answering with the opened file, so a player that asks it first addresses the wrong one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelFavoriteTest {

    private val dispatcher = StandardTestDispatcher()
    private val selectedMediaManager = SelectedMediaManager()
    private val playbackSetManager = PlaybackSetManager()
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase = mockk(relaxed = true)
    private val downloadNetworkFile: DownloadNetworkFileUseCase = mockk()
    private val preferencesRepository: WearPreferencesRepository = mockk(relaxed = true)

    // A relaxed StateFlow never returns from collect, which the view model reads as a Nothing value.
    private val fileOperations: PlayerFileOperationsManager = mockk(relaxed = true) {
        every { operationResult } returns MutableStateFlow(null)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferencesRepository.videoScaleMode } returns flowOf(VideoScaleMode.FIT)
        every { preferencesRepository.isSlideshowEnabled } returns flowOf(false)
        every { preferencesRepository.isShuffleEnabled } returns flowOf(false)
        every { preferencesRepository.isAnimationsDisabled } returns flowOf(false)
        coEvery { downloadNetworkFile(any(), any()) } returns Result.failure(IOException("offline"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `paging a network set reads and marks the paged file`() = runTest(dispatcher) {
        val opened = networkFile(OPENED_ID, OPENED_PATH)
        val paged = networkFile(PAGED_ID, PAGED_PATH)
        playbackSetManager.publish(listOf(opened, paged), startIndex = 0)
        selectedMediaManager.selectFile(file = opened, isNetworkSource = true, sourceId = SOURCE_ID)
        val viewModel = create()
        advanceUntilIdle()

        viewModel.skipToNext()
        advanceUntilIdle()
        viewModel.toggleFavorite()
        advanceUntilIdle()

        coVerify { toggleFavoriteUseCase.isFavorite(SOURCE_ID, PAGED_PATH) }
        coVerify(exactly = 1) {
            toggleFavoriteUseCase.toggle(
                match<WearFavoriteRecord> { it.sourceId == SOURCE_ID && it.filePath == PAGED_PATH },
                any()
            )
        }
        coVerify(exactly = 0) {
            toggleFavoriteUseCase.toggle(match<WearFavoriteRecord> { it.filePath == OPENED_PATH }, any())
        }
    }

    private fun networkFile(id: Long, path: String): WearMediaFile {
        val uri = mockk<Uri> { every { this@mockk.toString() } returns path }
        return WearMediaFile(
            id = id,
            name = path.substringAfterLast('/'),
            uri = uri,
            mimeType = "video/mp4",
            size = 1024L,
            dateModified = 0L
        )
    }

    private fun CoroutineScope.create() = VideoPlayerViewModel(
        mediaRepository = mockk(relaxed = true),
        selectedMediaManager = selectedMediaManager,
        playbackSetManager = playbackSetManager,
        preferencesRepository = preferencesRepository,
        downloadNetworkFile = downloadNetworkFile,
        endPhoneCameraSessionOnStreamError = mockk(relaxed = true),
        exoPlayer = mockk(relaxed = true),
        publishPlaybackStateUseCase = mockk(relaxed = true),
        streamPlaybackSessionFactory = mockk(relaxed = true),
        toggleFavoriteUseCase = toggleFavoriteUseCase,
        toggleStreamPinUseCase = mockk(relaxed = true),
        nowPlayingRepository = mockk(relaxed = true),
        fileOperations = fileOperations,
        castManager = mockk(relaxed = true),
        batteryWarningStore = mockk(relaxed = true),
        savedStateHandle = SavedStateHandle(mapOf("fileId" to OPENED_ID)),
        context = mockk(relaxed = true),
        applicationScope = this
    )

    private companion object {
        const val OPENED_ID = 2001L
        const val PAGED_ID = 2002L
        const val OPENED_PATH = "smb://nas/share/first.mp4"
        const val PAGED_PATH = "smb://nas/share/second.mp4"
        const val SOURCE_ID = "nas-share"
    }
}

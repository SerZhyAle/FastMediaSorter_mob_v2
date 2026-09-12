package com.sza.fastmediasorter.wear.ui.player.image

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ToggleFavoriteUseCase
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCastManager
import com.sza.fastmediasorter.wear.ui.player.common.PlayerFileOperationsManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var preferences: WearPreferencesRepository
    private lateinit var selectedMediaManager: SelectedMediaManager
    private lateinit var playbackSetManager: PlaybackSetManager
    private lateinit var downloadNetworkFile: DownloadNetworkFileUseCase
    private lateinit var favoritesRepository: WearFavoritesRepository
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var fileOperations: PlayerFileOperationsManager
    private lateinit var castManager: PlayerCastManager
    private lateinit var file: WearMediaFile

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferences = mockk(relaxed = true)
        every { preferences.imageScaleMode } returns flowOf(VideoScaleMode.FIT)
        every { preferences.isShuffleEnabled } returns flowOf(false)
        every { preferences.slideshowIntervalSeconds } returns flowOf(3)
        every { preferences.isSlideshowEnabled } returns flowOf(false)
        every { preferences.panelAutoHideSeconds } returns flowOf(5)

        selectedMediaManager = SelectedMediaManager()
        playbackSetManager = PlaybackSetManager()
        downloadNetworkFile = mockk(relaxed = true)
        favoritesRepository = mockk(relaxed = true)
        toggleFavoriteUseCase = mockk(relaxed = true)
        fileOperations = mockk(relaxed = true)
        every { fileOperations.operationResult } returns MutableStateFlow(null)
        castManager = mockk(relaxed = true)
        every { castManager.castState } returns MutableStateFlow(mockk(relaxed = true))

        file = WearMediaFile(
            id = FILE_ID,
            name = FILE_NAME,
            uri = mockk<Uri>(relaxed = true),
            mimeType = "image/png",
            size = 1024L,
            dateModified = 0L
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `file from published set loads correctly`() = runTest {
        playbackSetManager.publish(listOf(file), startIndex = 0)
        val viewModel = createViewModel(FILE_ID)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(FILE_NAME, state.mediaFile?.name)
        assertEquals(0, state.currentIndex)
        assertEquals(1, state.totalCount)
    }

    @Test
    fun `fallback to selectedMediaManager when set is absent`() = runTest {
        selectedMediaManager.selectFile(file = file, isNetworkSource = false)
        val viewModel = createViewModel(FILE_ID)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(FILE_NAME, state.mediaFile?.name)
    }

    @Test
    fun `missing file reports error when not found in set or selectedMedia`() = runTest {
        val viewModel = createViewModel(FILE_ID)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Image not found", state.error)
        assertNull(state.mediaFile)
    }

    @Test
    fun `toggleScaleMode updates preference and ui state`() = runTest {
        playbackSetManager.publish(listOf(file), startIndex = 0)
        val viewModel = createViewModel(FILE_ID)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(VideoScaleMode.FIT, viewModel.uiState.value.scaleMode)
        viewModel.toggleScaleMode()
        assertEquals(VideoScaleMode.CROP_PAN, viewModel.uiState.value.scaleMode)
    }

    private fun createViewModel(id: Long): ImageViewerViewModel {
        return ImageViewerViewModel(
            preferencesRepository = preferences,
            selectedMediaManager = selectedMediaManager,
            playbackSetManager = playbackSetManager,
            downloadNetworkFile = downloadNetworkFile,
            favoritesRepository = favoritesRepository,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            fileOperations = fileOperations,
            castManager = castManager,
            savedStateHandle = SavedStateHandle(mapOf(WearRoutes.ARG_FILE_ID to id))
        )
    }

    private companion object {
        const val FILE_ID = 1001L
        const val FILE_NAME = "photo.png"
    }
}

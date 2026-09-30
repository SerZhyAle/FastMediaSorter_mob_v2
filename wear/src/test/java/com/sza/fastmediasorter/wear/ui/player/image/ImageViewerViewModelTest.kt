package com.sza.fastmediasorter.wear.ui.player.image

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_NETWORK
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
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
import com.sza.fastmediasorter.wear.ui.player.common.PlayerOperationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import java.io.File

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
        unmockkStatic(Uri::class)
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

    /**
     * S3894: the read used the literal `network` source id while the write used the real one, and the
     * write took the opened picture from the manager, so a mark after paging landed on the wrong file.
     */
    @Test
    fun `paging a network set reads and marks the paged picture under its source id`() = runTest {
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns mockk(relaxed = true)
        coEvery { downloadNetworkFile(any(), any()) } returns Result.success(File("cached.png"))
        val opened = networkFile(FILE_ID, OPENED_PATH)
        val paged = networkFile(PAGED_ID, PAGED_PATH)
        playbackSetManager.publish(listOf(opened, paged), startIndex = 0)
        selectedMediaManager.selectFile(file = opened, isNetworkSource = true, sourceId = SOURCE_ID)
        val viewModel = createViewModel(FILE_ID)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToNext()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleFavorite()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { favoritesRepository.isFavorite(SOURCE_ID, OPENED_PATH) }
        coVerify { favoritesRepository.isFavorite(SOURCE_ID, PAGED_PATH) }
        coVerify(exactly = 0) { favoritesRepository.isFavorite(SOURCE_ID_NETWORK, any()) }
        coVerify(exactly = 1) {
            toggleFavoriteUseCase.toggle(
                match<WearFavoriteRecord> { it.sourceId == SOURCE_ID && it.filePath == PAGED_PATH },
                any()
            )
        }
    }

    /**
     * S3899: the advance after a delete re-read the opened selection, so the viewer downloaded the
     * opened picture again instead of the one that followed the deleted file.
     */
    @Test
    fun `deleting inside a network set shows the file that follows it`() = runTest {
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns mockk(relaxed = true)
        coEvery { downloadNetworkFile(any(), any()) } returns Result.success(File("cached.png"))
        val results = MutableStateFlow<PlayerOperationResult?>(null)
        every { fileOperations.operationResult } returns results
        val first = networkFile(FILE_ID, OPENED_PATH)
        val second = networkFile(PAGED_ID, PAGED_PATH)
        val third = networkFile(THIRD_ID, THIRD_PATH)
        playbackSetManager.publish(listOf(first, second, third), startIndex = 0)
        selectedMediaManager.selectFile(file = first, isNetworkSource = true, sourceId = SOURCE_ID)
        val viewModel = createViewModel(FILE_ID)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigateToNext()
        testDispatcher.scheduler.advanceUntilIdle()

        val next = playbackSetManager.removeAndSelectNext(PAGED_ID)
        results.value = PlayerOperationResult.Advance(requireNotNull(next))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { downloadNetworkFile(match { it.file.id == FILE_ID }, any()) }
        coVerify(exactly = 1) {
            downloadNetworkFile(match { it.file.id == THIRD_ID && it.sourceId == SOURCE_ID }, any())
        }
        val state = viewModel.uiState.value
        assertEquals(THIRD_ID, state.mediaFile?.id)
        assertEquals(1, state.currentIndex)
        assertEquals(2, state.totalCount)
    }

    /**
     * S3901: only the local branch built the slideshow controller, so on a network set the start
     * paged once and the timer never ran.
     */
    @Test
    fun `slideshow on a network set keeps advancing by the interval`() = runTest {
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns mockk(relaxed = true)
        coEvery { downloadNetworkFile(any(), any()) } returns Result.success(File("cached.png"))
        val first = networkFile(FILE_ID, OPENED_PATH)
        val second = networkFile(PAGED_ID, PAGED_PATH)
        val third = networkFile(THIRD_ID, THIRD_PATH)
        playbackSetManager.publish(listOf(first, second, third), startIndex = 0)
        selectedMediaManager.selectFile(file = first, isNetworkSource = true, sourceId = SOURCE_ID)
        val viewModel = createViewModel(FILE_ID)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startSlideshow()
        testDispatcher.scheduler.runCurrent()
        testDispatcher.scheduler.advanceTimeBy(INTERVAL_MS + 1)
        testDispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { downloadNetworkFile(match { it.file.id == PAGED_ID }, any()) }
        coVerify(exactly = 1) { downloadNetworkFile(match { it.file.id == THIRD_ID }, any()) }
        assertEquals(THIRD_ID, viewModel.uiState.value.mediaFile?.id)
        viewModel.stopSlideshow()
    }

    private fun networkFile(id: Long, path: String): WearMediaFile {
        val uri = mockk<Uri> { every { this@mockk.toString() } returns path }
        return WearMediaFile(
            id = id,
            name = path.substringAfterLast('/'),
            uri = uri,
            mimeType = "image/png",
            size = 1024L,
            dateModified = 0L
        )
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
        const val PAGED_ID = 1002L
        const val OPENED_PATH = "smb://nas/share/first.png"
        const val PAGED_PATH = "smb://nas/share/second.png"
        const val SOURCE_ID = "nas-share"
        const val THIRD_ID = 1003L
        const val THIRD_PATH = "smb://nas/share/third.png"
        const val INTERVAL_MS = 3_000L
    }
}

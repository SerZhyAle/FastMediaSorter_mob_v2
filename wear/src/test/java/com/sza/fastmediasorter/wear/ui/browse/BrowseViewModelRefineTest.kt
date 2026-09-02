package com.sza.fastmediasorter.wear.ui.browse

import android.net.Uri
import com.sza.fastmediasorter.wear.data.network.WearNetworkDataSources
import com.sza.fastmediasorter.wear.domain.browse.BrowseSortOrder
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearThumbnailRepository
import com.sza.fastmediasorter.wear.domain.usecase.PerformWearFileOperationUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S2136: the refine stage on the browse screen, tested where the compiler cannot see.
 *
 * Two of these are claims no type checks: that clearing a query costs no second trip to the source
 * (strategic 11 criterion 6 - a claim about call counts), and that an emptied result is a different
 * state from an empty resource (goal 6 - two states a compiler considers equally valid).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelRefineTest {

    private val dispatcher = StandardTestDispatcher()
    private val mediaRepository: WearMediaRepository = mockk()
    private val preferences: WearPreferencesRepository = mockk()
    private val networkDataSources: WearNetworkDataSources = mockk(relaxed = true)
    private val networkSourceRepository: NetworkSourceRepository = mockk(relaxed = true)
    private val selectedMedia: SelectedMediaManager = mockk(relaxed = true)
    private val playbackSet: PlaybackSetManager = mockk(relaxed = true)
    private val thumbnails: WearThumbnailRepository = mockk(relaxed = true)
    private val capabilityPolicy: WearFileCapabilityPolicy = mockk(relaxed = true)
    private val performFileOperation: PerformWearFileOperationUseCase = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferences.isAudioEnabled } returns flowOf(true)
        every { preferences.isVideoEnabled } returns flowOf(true)
        every { preferences.isImagesEnabled } returns flowOf(true)
        every { preferences.fileListViewMode } returns flowOf(WearViewMode.LIST)
        // S2199: the ViewModel reads the remembered refine state on construction. Stubbed as "nothing
        // remembered" so these cases keep asserting the refine behaviour itself rather than a restore.
        every { preferences.browseContentTypes } returns flowOf(emptySet())
        every { preferences.browseSortOrder } returns flowOf(BrowseSortOrder.DEFAULT)
        coEvery { preferences.setBrowseContentTypes(any()) } returns Unit
        coEvery { preferences.setBrowseSortOrder(any()) } returns Unit
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
    }

    private fun file(id: Long, name: String, size: Long, date: Long) = WearMediaFile(
        id = id,
        name = name,
        uri = mockk(relaxed = true),
        mimeType = "audio/mpeg",
        size = size,
        dateModified = date,
        duration = 0
    )

    private val fixture = listOf(
        file(1L, "Beta track.mp3", size = 300L, date = 30L),
        file(2L, "alpha song.mp3", size = 100L, date = 10L),
        file(3L, "Gamma tune.mp3", size = 200L, date = 20L)
    )

    private fun viewModel(files: List<WearMediaFile> = fixture): BrowseViewModel {
        every { mediaRepository.getMediaFiles(any()) } returns flowOf(Result.success(files))
        return BrowseViewModel(
            mediaRepository = mediaRepository,
            preferencesRepository = preferences,
            networkDataSources = networkDataSources,
            networkSourceRepository = networkSourceRepository,
            selectedMediaManager = selectedMedia,
            playbackSetManager = playbackSet,
            thumbnailRepository = thumbnails,
            capabilityPolicy = capabilityPolicy,
            performFileOperation = performFileOperation
        ).apply {
            setNavigationArgs(MediaType.MUSIC)
            loadMediaFiles()
        }
    }

    private fun shownNames(state: BrowseUiState): List<String> =
        (state as BrowseUiState.Success).files.map { it.name }

    @Test
    fun `a query narrows the published list`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSearchQuery("alpha")

        assertEquals(listOf("alpha song.mp3"), shownNames(vm.uiState.value))
    }

    @Test
    fun `the query ignores case`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSearchQuery("BETA")

        assertEquals(listOf("Beta track.mp3"), shownNames(vm.uiState.value))
    }

    @Test
    fun `clearing the query restores the list without asking the source again`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setSearchQuery("alpha")

        vm.setSearchQuery("")

        assertEquals(3, (vm.uiState.value as BrowseUiState.Success).files.size)
        verify(exactly = 1) { mediaRepository.getMediaFiles(any()) }
    }

    @Test
    fun `a query matching nothing is NoMatches, not Empty`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSearchQuery("no such file")

        assertTrue(vm.uiState.value is BrowseUiState.NoMatches)
    }

    @Test
    fun `a load returning nothing is Empty, not NoMatches`() = runTest {
        val vm = viewModel(files = emptyList())
        advanceUntilIdle()

        assertTrue(vm.uiState.value is BrowseUiState.Empty)
    }

    @Test
    fun `a sort order reorders the published list`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSortOrder(BrowseSortOrder.NAME_ASC)

        assertEquals(
            listOf("alpha song.mp3", "Beta track.mp3", "Gamma tune.mp3"),
            shownNames(vm.uiState.value)
        )
    }

    @Test
    fun `the size order runs smallest first`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSortOrder(BrowseSortOrder.SIZE_ASC)

        assertEquals(
            listOf("alpha song.mp3", "Gamma tune.mp3", "Beta track.mp3"),
            shownNames(vm.uiState.value)
        )
    }

    @Test
    fun `the default order is the order the source returned`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setSortOrder(BrowseSortOrder.NAME_ASC)

        vm.setSortOrder(BrowseSortOrder.DEFAULT)

        assertEquals(fixture.map { it.name }, shownNames(vm.uiState.value))
    }
}

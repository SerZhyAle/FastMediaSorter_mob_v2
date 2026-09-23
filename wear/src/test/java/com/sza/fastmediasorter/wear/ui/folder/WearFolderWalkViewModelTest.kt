package com.sza.fastmediasorter.wear.ui.folder

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.wear.domain.files.WearFdSecUseCase
import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearFolderEntry
import com.sza.fastmediasorter.wear.domain.model.WearFolderPage
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearFolderLevelRepository
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearNetworkFilePlaybackUseCase
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val PAGE_SIZE = 50

/**
 * S2201: the trail is the whole of this screen's behaviour, and nothing else pins it.
 *
 * The watch module has no instrumented tests - which `WearRoutes` names as the reason a navigation
 * defect on the watch is silent - so a descent that failed to come back up, or a further window that
 * re-read the first one, would reach a device unnoticed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearFolderWalkViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Records every level asked for, so a test can assert which window was requested, not just what came back. */
    private class FakeFolderRepository(
        private val pages: (WearFolderAddress, Int) -> WearFolderPage
    ) : WearFolderLevelRepository {

        val requests = mutableListOf<Pair<WearFolderAddress, Int>>()

        override suspend fun listLevel(address: WearFolderAddress, offset: Int): Result<WearFolderPage> {
            requests += address to offset
            return Result.success(pages(address, offset))
        }
    }

    private fun directory(name: String, path: String) = WearFolderEntry(
        name = name,
        address = WearFolderAddress.AppOwned(path),
        uri = null,
        isDirectory = true,
        mimeType = null,
        sizeBytes = 0L,
        dateModifiedEpochSeconds = 0L
    )

    private fun file(name: String) = WearFolderEntry(
        name = name,
        address = null,
        uri = null,
        isDirectory = false,
        mimeType = "text/plain",
        sizeBytes = 1L,
        dateModifiedEpochSeconds = 0L
    )

    private val selectedMedia = SelectedMediaManager()

    private fun viewModel(
        repository: WearFolderLevelRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = WearFolderWalkViewModel(
        repository = repository,
        preferencesRepository = mockk(relaxed = true),
        savedStateHandle = savedStateHandle,
        selectedMedia = selectedMedia,
        fdSec = WearFdSecUseCase(),
        prepareNetworkFile = PrepareWearNetworkFilePlaybackUseCase(selectedMedia),
        context = mockk(relaxed = true)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `two descents and two ups return to the root, which cannot go up`() = runTest(dispatcher) {
        val repository = FakeFolderRepository { _, _ ->
            WearFolderPage(entries = listOf(directory("inner", "/root/inner")), nextOffset = null)
        }
        val model = viewModel(repository)
        advanceUntilIdle()

        model.openFolder(directory("first", "/root/first"))
        advanceUntilIdle()
        model.openFolder(directory("second", "/root/first/second"))
        advanceUntilIdle()

        assertTrue(model.navigateUp())
        advanceUntilIdle()
        assertTrue(model.navigateUp())
        advanceUntilIdle()

        val state = model.uiState.value as WearFolderWalkUiState.Content
        assertFalse("the root level must not offer a step up", state.canGoUp)
        assertEquals(WearFolderAddress.Root, repository.requests.last().first)
    }

    @Test
    fun `navigating up at the root is not consumed, so the screen exits`() = runTest(dispatcher) {
        val repository = FakeFolderRepository { _, _ ->
            WearFolderPage(entries = listOf(file("note.txt")), nextOffset = null)
        }
        val model = viewModel(repository)
        advanceUntilIdle()

        assertFalse("nothing to step back to - the gesture belongs to the screen", model.navigateUp())
    }

    @Test
    fun `a further window grows the level without re-reading the first one`() = runTest(dispatcher) {
        val repository = FakeFolderRepository { _, offset ->
            val page = List(PAGE_SIZE) { file("file-${offset + it}") }
            WearFolderPage(entries = page, nextOffset = (offset + PAGE_SIZE).takeIf { offset == 0 })
        }
        val model = viewModel(repository)
        advanceUntilIdle()

        val firstWindow = model.uiState.value as WearFolderWalkUiState.Content
        assertEquals(PAGE_SIZE, firstWindow.entries.size)
        assertTrue("a level with a next offset must offer to grow", firstWindow.canLoadMore)

        model.loadMore()
        advanceUntilIdle()

        val grown = model.uiState.value as WearFolderWalkUiState.Content
        assertEquals(PAGE_SIZE * 2, grown.entries.size)
        assertFalse("the level is exhausted, so nothing more is offered", grown.canLoadMore)
        assertEquals(listOf(0, PAGE_SIZE), repository.requests.map { it.second })
    }

    @Test
    fun `a file is not a level, so opening one does not descend`() = runTest(dispatcher) {
        val repository = FakeFolderRepository { _, _ ->
            WearFolderPage(entries = listOf(file("note.txt")), nextOffset = null)
        }
        val model = viewModel(repository)
        advanceUntilIdle()

        model.openFolder(file("note.txt"))
        advanceUntilIdle()

        assertEquals("a file carries no address to list", 1, repository.requests.size)
        assertFalse((model.uiState.value as WearFolderWalkUiState.Content).canGoUp)
    }

    // S3383: a container tapped in the walk must reach the credential screen, never a player.
    @Test
    fun `a container of shared storage is published with the relative path of its level`() = runTest(dispatcher) {
        val repository = FakeFolderRepository { _, _ -> WearFolderPage(entries = emptyList(), nextOffset = null) }
        val model = viewModel(repository)
        advanceUntilIdle()
        model.openFolder(
            directory("Download", "unused").copy(address = WearFolderAddress.MediaStoreFolder("Download/"))
        )
        advanceUntilIdle()

        val id = model.containerIdFor(file("holiday.FD-SEC").copy(uri = mockk<Uri>(relaxed = true)))

        val published = id?.let { selectedMedia.getSelectedFileById(it)?.file }
        assertEquals("holiday.FD-SEC", published?.name)
        assertEquals("Download/", published?.relativePath)
    }

    // S3407: a network container is fetched by the credential screen, so it must arrive there as a
    // network selection that still names the share it came from.
    @Test
    fun `a container of a network walk is published as a network selection of its source`() = runTest(dispatcher) {
        val start = WearFolderAddress.NetworkLevel(sourceId = "nas", path = "/media")
        val model = viewModel(
            FakeFolderRepository { _, _ -> WearFolderPage(entries = emptyList(), nextOffset = null) },
            SavedStateHandle(mapOf(WearRoutes.ARG_FOLDER_TOKEN to start.asToken()))
        )
        advanceUntilIdle()
        val uri = mockk<Uri>(relaxed = true)

        val id = model.containerIdFor(file("holiday.fd-sec").copy(uri = uri))

        val published = id?.let { selectedMedia.getSelectedFileById(it) }
        assertEquals("holiday.fd-sec", published?.file?.name)
        assertTrue("the credential screen fetches only a network selection", published?.isNetworkSource == true)
        assertEquals("nas", published?.sourceId)
        assertEquals(uri.toString(), published?.streamUri)
    }

    @Test
    fun `an ordinary file is left to the player`() = runTest(dispatcher) {
        val model = viewModel(FakeFolderRepository { _, _ -> WearFolderPage(entries = emptyList(), nextOffset = null) })
        advanceUntilIdle()

        assertNull(model.containerIdFor(file("holiday.jpg").copy(uri = mockk<Uri>(relaxed = true))))
        assertNull(selectedMedia.selectedFile.value)
    }
}

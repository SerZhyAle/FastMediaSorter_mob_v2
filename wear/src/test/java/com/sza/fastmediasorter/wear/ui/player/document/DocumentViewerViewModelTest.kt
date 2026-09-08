package com.sza.fastmediasorter.wear.ui.player.document

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.wear.domain.documents.DocumentFontSize
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentContent
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFailure
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ReadDocumentTextUseCase
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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

/**
 * S2532: the reader's mapping from a read result onto screen state.
 *
 * Worth pinning because strategic §2 goal 5 is a claim about four outcomes staying apart, and this
 * mapping is the cheapest place one collapses into another - an empty file routed through the failure
 * branch is indistinguishable on screen from a file that could not be read at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var playbackSetManager: PlaybackSetManager
    private lateinit var selectedMediaManager: SelectedMediaManager
    private lateinit var readDocumentText: ReadDocumentTextUseCase
    private lateinit var preferences: WearPreferencesRepository
    private lateinit var file: WearMediaFile

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playbackSetManager = PlaybackSetManager()
        selectedMediaManager = SelectedMediaManager()
        readDocumentText = mockk()
        // Stored state is Phase 04's subject, not this test's: every case here reads a document that
        // was never opened before, so the store answers with its defaults and nothing else.
        preferences = mockk(relaxed = true)
        every { preferences.documentFontSize } returns flowOf(DocumentFontSize.MEDIUM)
        coEvery { preferences.readingPositionFor(any(), any()) } returns null
        file = WearMediaFile(
            id = FILE_ID,
            name = FILE_NAME,
            uri = mockk<Uri>(relaxed = true),
            mimeType = "text/plain",
            size = 128L,
            dateModified = 0L
        )
        playbackSetManager.publish(listOf(file), startIndex = 0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `text becomes paragraphs and is not marked truncated`() = runTest {
        val state = stateFor(
            WearDocumentContent.Text(text = "First line\n\nSecond line", truncated = false, totalBytes = 22L)
        )

        assertEquals(listOf("First line", "Second line"), state.paragraphs)
        assertFalse(state.truncated)
        assertFalse(state.isLoading)
        assertNull(state.failure)
        assertEquals(FILE_NAME, state.fileName)
    }

    @Test
    fun `truncated text keeps its paragraphs and raises the flag`() = runTest {
        val state = stateFor(
            WearDocumentContent.Text(text = "First line\nSecond line", truncated = true, totalBytes = 21L)
        )

        assertEquals(listOf("First line", "Second line"), state.paragraphs)
        assertTrue(state.truncated)
    }

    @Test
    fun `an empty file is empty rather than failed`() = runTest {
        val state = stateFor(WearDocumentContent.Empty)

        assertTrue(state.isEmpty)
        assertNull(state.failure)
        assertTrue(state.paragraphs.isEmpty())
    }

    @Test
    fun `every failure reason reaches the state unchanged`() = runTest {
        WearDocumentFailure.entries.forEach { reason ->
            val state = stateFor(WearDocumentContent.Failure(reason))

            assertEquals(reason, state.failure)
            assertFalse(state.isEmpty)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `changing the font size leaves the content alone`() = runTest {
        val viewModel = viewModelFor(
            WearDocumentContent.Text(text = "First line", truncated = false, totalBytes = 10L)
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val before = viewModel.uiState.value

        viewModel.onFontSizeChanged(DocumentFontSize.LARGE)
        val after = viewModel.uiState.value

        assertEquals(DocumentFontSize.MEDIUM, before.fontSize)
        assertEquals(DocumentFontSize.LARGE, after.fontSize)
        assertEquals(before.paragraphs, after.paragraphs)
        assertEquals(before.truncated, after.truncated)
    }

    /**
     * A file the published set does not hold and the selection does not name is gone as far as the
     * wearer is concerned, so it must not open on a blank reader.
     */
    @Test
    fun `an unresolvable id reports the file as missing`() = runTest {
        playbackSetManager.clear()
        val viewModel = DocumentViewerViewModel(
            playbackSetManager = playbackSetManager,
            selectedMediaManager = selectedMediaManager,
            readDocumentText = readDocumentText,
            preferences = preferences,
            savedStateHandle = SavedStateHandle(mapOf(WearRoutes.ARG_FILE_ID to FILE_ID))
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(WearDocumentFailure.NOT_FOUND, viewModel.uiState.value.failure)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun stateFor(content: WearDocumentContent): DocumentViewerUiState {
        val viewModel = viewModelFor(content)
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value
    }

    private fun viewModelFor(content: WearDocumentContent): DocumentViewerViewModel {
        coEvery { readDocumentText(any()) } returns content
        return DocumentViewerViewModel(
            playbackSetManager = playbackSetManager,
            selectedMediaManager = selectedMediaManager,
            readDocumentText = readDocumentText,
            preferences = preferences,
            savedStateHandle = SavedStateHandle(mapOf(WearRoutes.ARG_FILE_ID to FILE_ID))
        )
    }

    private companion object {
        const val FILE_ID = 42L
        const val FILE_NAME = "notes.txt"
    }
}

package com.sza.fastmediasorter.wear.ui.favourites

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.PerformWearFileOperationUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ToggleFavoriteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1846: the Favourites screen is the second half of a defect where a named section led nowhere.
 *
 * Each case here stands for a way it could lead nowhere again: an empty list that reads as a failure, a
 * mark made before this ticket vanishing from the list, an unmark that does not reach the phone, or a row
 * that opens a player with nothing behind it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository: WearFavoritesRepository = mockk()
    private val toggleFavorite: ToggleFavoriteUseCase = mockk(relaxed = true)
    private val selectedMedia: SelectedMediaManager = mockk(relaxed = true)
    private val preferences: WearPreferencesRepository = mockk()
    private val capabilityPolicy: WearFileCapabilityPolicy = mockk(relaxed = true)
    private val performFileOperation: PerformWearFileOperationUseCase = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferences.fileListViewMode } returns flowOf(WearViewMode.GRID_2)
        // The view model builds a Uri for the player hand-off, and android.net.Uri is a stub in a unit test.
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
    }

    @Test
    fun `an empty store is an empty state, not a failure`() = runTest {
        coEvery { repository.getFavorites() } returns emptyList()

        val viewModel = build()
        advanceUntilIdle()

        assertTrue("expected Empty", viewModel.uiState.value is FavouritesUiState.Empty)
    }

    @Test
    fun `a record and a pre-record entry are both listed`() = runTest {
        coEvery { repository.getFavorites() } returns listOf(
            record(path = "/a/new.mp3", mimeType = "audio"),
            record(path = "/a/old.mp3", mimeType = null)
        )

        val viewModel = build()
        advanceUntilIdle()

        val content = viewModel.uiState.value as FavouritesUiState.Content
        assertEquals(listOf("new.mp3", "old.mp3"), content.records.map { it.displayName })
        assertNull("the pre-record entry keeps its missing kind", content.records[1].mimeType)
    }

    @Test
    fun `unmarking drops the row at once and reaches the use case`() = runTest {
        val stays = record(path = "/a/stays.mp3", mimeType = "audio")
        val goes = record(path = "/a/goes.mp3", mimeType = "audio")
        coEvery { repository.getFavorites() } returns listOf(stays, goes)

        val viewModel = build()
        advanceUntilIdle()
        viewModel.unmark(goes)

        val content = viewModel.uiState.value as FavouritesUiState.Content
        assertEquals(listOf("stays.mp3"), content.records.map { it.displayName })

        advanceUntilIdle()
        coVerify { toggleFavorite.toggle(goes.sourceId, goes.filePath, wasFavorite = true) }
    }

    @Test
    fun `unmarking the last row leaves the empty state, not an empty list`() = runTest {
        val only = record(path = "/a/only.mp3", mimeType = "audio")
        coEvery { repository.getFavorites() } returns listOf(only)

        val viewModel = build()
        advanceUntilIdle()
        viewModel.unmark(only)

        assertTrue("expected Empty", viewModel.uiState.value is FavouritesUiState.Empty)
    }

    @Test
    fun `opening a record hands the file over before asking for a player`() = runTest {
        val target = record(path = "/a/song.mp3", mimeType = "audio")
        coEvery { repository.getFavorites() } returns listOf(target)

        val viewModel = build()
        advanceUntilIdle()
        viewModel.open(target)

        val request = viewModel.openRequest.value as FavouriteOpenRequest.Ready
        assertEquals("audio", request.mimeType)
        // The hand-off is what makes the id mean anything to the player, so it must happen.
        coVerify { selectedMedia.selectFile(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a record with no kind refuses instead of opening an empty player`() = runTest {
        val legacy = record(path = "/a/mystery", mimeType = null)
        coEvery { repository.getFavorites() } returns listOf(legacy)

        val viewModel = build()
        advanceUntilIdle()
        viewModel.open(legacy)

        assertTrue(
            "expected Unopenable",
            viewModel.openRequest.value is FavouriteOpenRequest.Unopenable
        )
    }

    @Test
    fun `the file list view mode reaches the screen`() = runTest {
        coEvery { repository.getFavorites() } returns emptyList()

        val viewModel = build()
        // The flow is shared WhileSubscribed, so it stays at its initial value until something collects it -
        // exactly as on screen. Asserting without collecting would only prove the default.
        val collected = mutableListOf<WearViewMode>()
        val job = launch { viewModel.fileListViewMode.toList(collected) }
        advanceUntilIdle()
        job.cancel()

        assertEquals(WearViewMode.GRID_2, collected.last())
    }

    private fun build() = FavouritesViewModel(
        repository,
        toggleFavorite,
        selectedMedia,
        capabilityPolicy,
        performFileOperation,
        preferences
    )

    private fun record(path: String, mimeType: String?) = WearFavoriteRecord(
        sourceId = "local",
        filePath = path,
        displayName = path.substringAfterLast('/'),
        mimeType = mimeType
    )
}

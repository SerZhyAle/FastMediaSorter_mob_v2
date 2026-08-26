package com.sza.fastmediasorter.wear.ui.streams

import com.sza.fastmediasorter.wear.data.repository.WearFaviconAtlasStore
import com.sza.fastmediasorter.wear.domain.model.FAVORITE_ITEM_KIND_STREAM
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.usecase.ImportWearStreamCatalogUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1946: the owner reports that the streams search filters nothing, whatever is typed. Reading the
 * code says the opposite - the query recomputes the rendered list - so the premise of the fix is that
 * the break is on the input path, not the filtering path.
 *
 * That premise is worth exactly as much as its proof, and this is the proof a machine can give: with
 * the query handed straight to the ViewModel, the rendered list must shrink. If these ever fail, the
 * ticket's whole line of reasoning is wrong and the search for the defect starts again at the state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamsViewModelSearchTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a query narrows the rendered list to the channels that match by name`() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("jazz")
        advanceUntilIdle()

        val rendered = viewModel.uiState.value.displayChannels.map { it.name }
        assertEquals(listOf("Jazz FM"), rendered)
        assertEquals("the raw catalog is never narrowed", CATALOG_SIZE, viewModel.uiState.value.channels.size)
    }

    @Test
    fun `a query also matches the category, which is what the dialog presets offer`() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        // The dialog's presets are category words and reach this method directly, bypassing the input
        // activity - which is why a preset tap is the on-device discriminator this ticket asks for.
        viewModel.setSearchQuery("News")
        advanceUntilIdle()

        val rendered = viewModel.uiState.value.displayChannels.map { it.name }
        assertEquals(listOf("Morning Report"), rendered)
    }

    @Test
    fun `an empty query restores the whole catalog`() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("jazz")
        advanceUntilIdle()
        viewModel.setSearchQuery("")
        advanceUntilIdle()

        assertEquals(CATALOG_SIZE, viewModel.uiState.value.displayChannels.size)
    }

    @Test
    fun `a refused input path is stated, and a later query clears that statement`() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setSearchInputUnavailable()
        advanceUntilIdle()
        assertTrue("the refusal must be visible, not only logged", viewModel.uiState.value.searchInputUnavailable)

        viewModel.setSearchQuery("jazz")
        advanceUntilIdle()
        assertFalse("a query proves the path answered", viewModel.uiState.value.searchInputUnavailable)
    }

    /**
     * S1954: the acceptance criterion is that a mark survives a catalogue re-import, and an import is
     * exactly what renumbers the rows. Both spellings below address one channel, so a mark stored from
     * the first must still be recognised when the catalogue offers the second under a new id.
     */
    @Test
    fun `a marked channel leads the list after a catalog re-import changed its row id`() = runTest {
        val reimported = listOf(
            channel(id = "77", name = "Morning Report", kind = "AUDIO", category = "News"),
            channel(id = "88", name = "Jazz FM", kind = "AUDIO", category = "Music"),
        ).map { if (it.name == "Jazz FM") it.copy(url = "HTTPS://EXAMPLE.INVALID:443/1/") else it }

        val viewModel = buildViewModel(
            catalog = reimported,
            pinnedUrls = listOf("https://example.invalid/1")
        )
        advanceUntilIdle()

        val rendered = viewModel.uiState.value.displayChannels.map { it.name }
        assertEquals(listOf("Jazz FM", "Morning Report"), rendered)
    }

    @Test
    fun `name sorting stays deterministic inside the pinned and the unpinned group`() = runTest {
        val viewModel = buildViewModel(pinnedUrls = listOf("https://example.invalid/3"))
        advanceUntilIdle()

        viewModel.setSortOrder(StreamSortOrder.NAME_ASC)
        advanceUntilIdle()

        // City Cam is pinned so it leads; the rest keep the requested name order behind it.
        val rendered = viewModel.uiState.value.displayChannels.map { it.name }
        assertEquals(listOf("City Cam", "Jazz FM", "Morning Report"), rendered)
    }

    private fun buildViewModel(
        catalog: List<WearStreamChannel> = CATALOG,
        pinnedUrls: List<String> = emptyList()
    ): StreamsViewModel {
        val repository = mockk<WearStreamChannelRepository>(relaxed = true)
        every { repository.observeChannels() } returns flowOf(catalog)
        val preferences = mockk<WearPreferencesRepository>(relaxed = true)
        every { preferences.viewMode } returns flowOf(WearViewMode.LIST)
        val atlasStore = mockk<WearFaviconAtlasStore>(relaxed = true)
        every { atlasStore.atlasFile() } returns null
        val favorites = mockk<WearFavoritesRepository>(relaxed = true)
        coEvery { favorites.getFavorites() } returns pinnedUrls.map { url ->
            WearFavoriteRecord(
                sourceId = SOURCE_ID_STREAM,
                filePath = normalizeWearStreamUrl(url),
                displayName = url,
                itemKind = FAVORITE_ITEM_KIND_STREAM
            )
        }
        return StreamsViewModel(
            repository = repository,
            importCatalogUseCase = mockk<ImportWearStreamCatalogUseCase>(relaxed = true),
            faviconAtlasStore = atlasStore,
            preferencesRepository = preferences,
            preparePlayback = PrepareWearStreamPlaybackUseCase(
                selectedMediaManager = mockk<SelectedMediaManager>(relaxed = true),
                playbackSetManager = mockk<PlaybackSetManager>(relaxed = true),
            ),
            favoritesRepository = favorites,
        )
    }

    private companion object {
        val CATALOG = listOf(
            channel(id = "1", name = "Jazz FM", kind = "AUDIO", category = "Music"),
            channel(id = "2", name = "Morning Report", kind = "AUDIO", category = "News"),
            channel(id = "3", name = "City Cam", kind = "VIDEO", category = "Webcams"),
        )

        val CATALOG_SIZE = CATALOG.size

        fun channel(id: String, name: String, kind: String, category: String) = WearStreamChannel(
            id = id,
            name = name,
            url = "https://example.invalid/$id",
            mediaKind = kind,
            category = category,
        )
    }
}

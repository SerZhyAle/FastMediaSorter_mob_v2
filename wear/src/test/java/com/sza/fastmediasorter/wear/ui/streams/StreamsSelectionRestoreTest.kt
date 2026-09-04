package com.sza.fastmediasorter.wear.ui.streams

import com.sza.fastmediasorter.wear.data.repository.WearFaviconAtlasStore
import com.sza.fastmediasorter.wear.data.repository.WearPhonePinsRepository
import com.sza.fastmediasorter.wear.data.repository.WearStreamPinsRepository
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamUsageRepository
import com.sza.fastmediasorter.wear.domain.usecase.ImportWearStreamCatalogUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * S2146: the stored filter and sort are seeded back into the screen, per strategic §11 criterion 7.
 *
 * Real time rather than a virtual scheduler, for the reason `StreamsViewModelProjectionTest` records:
 * the projection hops to `Dispatchers.Default`, which a test scheduler does not drive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamsSelectionRestoreTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stored values seed the screen`() = runBlocking {
        val viewModel = buildViewModel(
            storedSort = StreamSortOrder.NAME_DESC.name,
            storedKind = StreamFilterKind.VIDEO_ONLY.name,
            storedTopic = "Rock",
            storedLanguage = "german"
        )
        delay(SETTLE_MS)

        val state = viewModel.uiState.value
        assertEquals(StreamSortOrder.NAME_DESC, state.sortOrder)
        assertEquals(StreamFilterKind.VIDEO_ONLY, state.filterKind)
        assertEquals("Rock", state.selectedTopic)
        assertEquals("german", state.selectedLanguage)
    }

    @Test
    fun `a stored sort name this build no longer knows falls back instead of throwing`() = runBlocking {
        // The one path here that only ever runs on a wearer's watch, after an upgrade renamed or
        // removed a constant. `valueOf` would throw and the screen would not open.
        val viewModel = buildViewModel(storedSort = "SORT_BY_TOPIC_REMOVED_IN_S2146")
        delay(SETTLE_MS)

        assertEquals(StreamSortOrder.MOST_USED, viewModel.uiState.value.sortOrder)
    }

    @Test
    fun `a stored facet absent from the catalogue stays selected`() = runBlocking {
        // Not cleared: a catalogue that failed to download would otherwise erase the wearer's choice.
        val viewModel = buildViewModel(storedTopic = "Rubric That Is Not In This Catalogue")
        delay(SETTLE_MS)

        assertEquals("Rubric That Is Not In This Catalogue", viewModel.uiState.value.selectedTopic)
        assertEquals(emptyList<WearStreamChannel>(), viewModel.uiState.value.displayChannels)
    }

    @Test
    fun `an empty store yields the defaults`() = runBlocking {
        val viewModel = buildViewModel()
        delay(SETTLE_MS)

        val state = viewModel.uiState.value
        assertEquals(StreamSortOrder.MOST_USED, state.sortOrder)
        assertEquals(StreamFilterKind.ALL, state.filterKind)
        assertEquals(null, state.selectedTopic)
        assertEquals(null, state.selectedLanguage)
    }

    private fun buildViewModel(
        storedSort: String? = null,
        storedKind: String? = null,
        storedTopic: String? = null,
        storedLanguage: String? = null
    ): StreamsViewModel {
        val repository = mockk<WearStreamChannelRepository>(relaxed = true)
        every { repository.observeChannels() } returns flowOf(CATALOG)
        val preferences = mockk<WearPreferencesRepository>(relaxed = true)
        every { preferences.viewMode } returns flowOf(WearViewMode.LIST)
        // Stubbed explicitly rather than left relaxed: the restore calls `first()` on each of these,
        // and `first()` on a relaxed mock's flow completes without emitting.
        every { preferences.streamsSortOrderName } returns flowOf(storedSort)
        every { preferences.streamsFilterKindName } returns flowOf(storedKind)
        every { preferences.streamsSelectedTopic } returns flowOf(storedTopic)
        every { preferences.streamsSelectedLanguage } returns flowOf(storedLanguage)
        val atlasStore = mockk<WearFaviconAtlasStore>(relaxed = true)
        every { atlasStore.atlasFile() } returns null
        val streamPinsRepository = mockk<WearStreamPinsRepository>(relaxed = true)
        every { streamPinsRepository.observeWatchPins() } returns MutableStateFlow(emptySet())
        every { streamPinsRepository.getWatchPins() } returns emptySet()
        val phonePins = mockk<WearPhonePinsRepository>()
        every { phonePins.observe() } returns MutableStateFlow(emptySet())
        return StreamsViewModel(
            repository = repository,
            importCatalogUseCase = mockk<ImportWearStreamCatalogUseCase>(relaxed = true),
            faviconAtlasStore = atlasStore,
            preferencesRepository = preferences,
            preparePlayback = PrepareWearStreamPlaybackUseCase(
                selectedMediaManager = mockk<SelectedMediaManager>(relaxed = true),
                playbackSetManager = mockk<PlaybackSetManager>(relaxed = true),
                usageRepository = mockk<WearStreamUsageRepository>(relaxed = true),
                preferencesRepository = mockk<WearPreferencesRepository>(relaxed = true),
            ),
            streamPinsRepository = streamPinsRepository,
            phonePinsRepository = phonePins,
            usageRepository = mockk<WearStreamUsageRepository>(relaxed = true),
        )
    }

    private companion object {
        /** Comfortably past the view model's input pause, so a projection has certainly landed. */
        const val SETTLE_MS = 600L

        val CATALOG = listOf(
            WearStreamChannel(
                id = "1",
                name = "Jazz FM",
                url = "https://example.invalid/1",
                mediaKind = "AUDIO",
                topic = "Jazz & Blues",
                language = "english"
            )
        )
    }
}

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * S2149: this phase changed *when and where* the projection runs, never what it produces, so the tests
 * pin both halves - that a burst of input costs one projection, and that the answer is still the same.
 *
 * Real time rather than a virtual scheduler: the projection deliberately hops to `Dispatchers.Default`,
 * which a test scheduler does not drive, so advancing virtual time would assert against a projection
 * that had not run yet - a green test observing nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamsViewModelProjectionTest {

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the selected sort is visible immediately, before the projection completes`() = runBlocking {
        val viewModel = buildViewModel()

        viewModel.setSortOrder(StreamSortOrder.NAME_ASC)

        // No wait: the chosen value must be readable the moment it is set, or the dialog would close
        // onto a row still showing the previous choice while the catalogue is projected.
        assertEquals(StreamSortOrder.NAME_ASC, viewModel.uiState.value.sortOrder)
    }

    @Test
    fun `the previous list stays on screen while a new projection is pending`() = runBlocking {
        val viewModel = buildViewModel()
        delay(SETTLE_MS)
        assertEquals(CATALOG.size, viewModel.uiState.value.displayChannels.size)

        viewModel.setSearchQuery("nothing matches this")

        // Read inside the input pause: the list must still hold the previous result rather than blink
        // empty, which is the visible risk of moving the projection off the drawing thread.
        assertEquals(CATALOG.size, viewModel.uiState.value.displayChannels.size)
    }

    @Test
    fun `a burst of queries costs one projection, not one per keystroke`() = runBlocking {
        val viewModel = buildViewModel()
        delay(SETTLE_MS)

        val observed = mutableListOf<List<String>>()
        val collector = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val job: Job = collector.launch {
            viewModel.uiState.collect { state ->
                val names = state.displayChannels.map { it.name }
                if (observed.lastOrNull() != names) observed += names
            }
        }
        delay(SETTLE_MS)
        val before = observed.size

        listOf("j", "ja", "jaz", "jazz", "jazz ").forEach { viewModel.setSearchQuery(it) }
        delay(SETTLE_MS)
        job.cancel()
        collector.cancel()

        assertEquals("five keystrokes must land as one new list", before + 1, observed.size)
        assertEquals(listOf("Jazz FM"), observed.last())
    }

    @Test
    fun `the projection result is the same one the inputs describe`() = runBlocking {
        val viewModel = buildViewModel()
        delay(SETTLE_MS)

        viewModel.setFilterKind(StreamFilterKind.VIDEO_ONLY)
        delay(SETTLE_MS)

        assertEquals(
            listOf("City Cam"),
            viewModel.uiState.value.displayChannels.map { it.name }
        )
    }

    private fun buildViewModel(catalog: List<WearStreamChannel> = CATALOG): StreamsViewModel {
        val repository = mockk<WearStreamChannelRepository>(relaxed = true)
        every { repository.observeChannels() } returns flowOf(catalog)
        val preferences = mockk<WearPreferencesRepository>(relaxed = true)
        every { preferences.viewMode } returns flowOf(WearViewMode.LIST)
        // S2146: the restore reads these four with first(), which on an unstubbed relaxed mock
        // completes without emitting - and a throw there would stop the catalogue being observed.
        every { preferences.streamsSortOrderName } returns flowOf(null)
        every { preferences.streamsFilterKindName } returns flowOf(null)
        every { preferences.streamsSelectedTopic } returns flowOf(null)
        every { preferences.streamsSelectedLanguage } returns flowOf(null)
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
            channel(id = "1", name = "Jazz FM", kind = "AUDIO", category = "Music"),
            channel(id = "2", name = "Morning Report", kind = "AUDIO", category = "News"),
            channel(id = "3", name = "City Cam", kind = "VIDEO", category = "Webcams"),
        )

        fun channel(id: String, name: String, kind: String, category: String) = WearStreamChannel(
            id = id,
            name = name,
            url = "https://example.invalid/$id",
            mediaKind = kind,
            category = category,
        )
    }
}

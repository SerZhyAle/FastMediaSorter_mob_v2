package com.sza.fastmediasorter.wear.ui.streams

import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPassStore
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPlaybackResolver
import com.sza.fastmediasorter.wear.data.repository.WearFaviconAtlasStore
import com.sza.fastmediasorter.wear.data.repository.WearPhonePinsRepository
import com.sza.fastmediasorter.wear.data.repository.WearStreamPinsRepository
import com.sza.fastmediasorter.wear.domain.model.CatalogImportResult
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamCollectionRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamUsageRepository
import com.sza.fastmediasorter.wear.domain.usecase.ImportWearStreamCatalogUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
 *
 * S4611: the same builder also pins the first-download guard, because that guard is decided by the first
 * emission of the very catalogue this class feeds the view model. The first emission here lands long
 * before the 150 ms projection pause has elapsed, which is the cold-start timing the guard must survive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamsSelectionRestoreTest {

    private val viewModels = mutableListOf<StreamsViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = runBlocking {
        // Finish background projections before restoring Main, so no work escapes into another test.
        viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `stored values seed the screen`() = runBlocking {
        val viewModel = buildViewModel(
            storedSort = StreamSortOrder.NAME_DESC.name,
            storedKind = StreamFilterKind.VIDEO_ONLY.name,
            storedTopic = "Rock",
            storedLanguage = "english"
        )
        delay(SETTLE_MS)

        val state = viewModel.uiState.value
        assertEquals(StreamSortOrder.NAME_DESC, state.sortOrder)
        assertEquals(StreamFilterKind.VIDEO_ONLY, state.filterKind)
        assertEquals("Rock", state.selectedTopic)
        assertEquals("english", state.selectedLanguage)
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
    fun `a stored language missing from a non-empty catalogue is cleared in state and preference`() = runBlocking {
        // S4133: the vocabulary closing makes this common; an empty list behind an active filter is worse.
        val preferences = mockk<WearPreferencesRepository>(relaxed = true)
        val viewModel = buildViewModel(storedLanguage = "caribbean english", preferencesMock = preferences)
        delay(SETTLE_MS)

        assertEquals(null, viewModel.uiState.value.selectedLanguage)
        coVerify { preferences.setStreamsSelectedLanguage(null) }
    }

    @Test
    fun `a stored language is kept while the catalogue is empty`() = runBlocking {
        val preferences = mockk<WearPreferencesRepository>(relaxed = true)
        val viewModel = buildViewModel(
            storedLanguage = "caribbean english",
            catalogFlow = flowOf(emptyList()),
            preferencesMock = preferences
        )
        delay(SETTLE_MS)

        assertEquals("caribbean english", viewModel.uiState.value.selectedLanguage)
        coVerify(exactly = 0) { preferences.setStreamsSelectedLanguage(null) }
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

    @Test
    fun `an empty first emission starts the download before the first projection has landed`() = runBlocking {
        // The emission is immediate and the projection waits out its input pause, so the screen is still
        // `isLoading` when the guard looks. A guard that reads `isLoading` starts nothing here.
        val importUseCase = stubbedImport(CatalogImportResult.Empty)
        buildViewModel(catalogFlow = flowOf(emptyList()), importUseCase = importUseCase)
        delay(SETTLE_MS)

        coVerify(exactly = 1) { importUseCase() }
    }

    @Test
    fun `a failed download is not repeated by later empty emissions`() = runBlocking {
        val importUseCase = stubbedImport(CatalogImportResult.Failure("offline"))
        val emissions = MutableSharedFlow<List<WearStreamChannel>>(replay = 1)
        emissions.tryEmit(emptyList())
        val viewModel = buildViewModel(catalogFlow = emissions, importUseCase = importUseCase)
        delay(SETTLE_MS)
        emissions.emit(emptyList())
        delay(SETTLE_MS)
        emissions.emit(emptyList())
        delay(SETTLE_MS)

        coVerify(exactly = 1) { importUseCase() }
        assertEquals("offline", viewModel.uiState.value.error)
    }

    @Test
    fun `a catalogue that emptied after a non-empty first emission is not downloaded again`() = runBlocking {
        val importUseCase = stubbedImport(CatalogImportResult.Empty)
        val emissions = MutableSharedFlow<List<WearStreamChannel>>(replay = 1)
        emissions.tryEmit(CATALOG)
        buildViewModel(catalogFlow = emissions, importUseCase = importUseCase)
        delay(SETTLE_MS)
        emissions.emit(emptyList())
        delay(SETTLE_MS)

        coVerify(exactly = 0) { importUseCase() }
    }

    private fun stubbedImport(result: CatalogImportResult): ImportWearStreamCatalogUseCase {
        // Not relaxed: a relaxed mock answers the sealed result with a proxy that matches no branch of the
        // `when` in `refreshCatalog`, which would throw inside the view model's scope.
        val importUseCase = mockk<ImportWearStreamCatalogUseCase>()
        coEvery { importUseCase() } returns result
        return importUseCase
    }

    private fun buildViewModel(
        storedSort: String? = null,
        storedKind: String? = null,
        storedTopic: String? = null,
        storedLanguage: String? = null,
        catalogFlow: Flow<List<WearStreamChannel>> = flowOf(CATALOG),
        importUseCase: ImportWearStreamCatalogUseCase = stubbedImport(CatalogImportResult.Empty),
        preferencesMock: WearPreferencesRepository = mockk(relaxed = true)
    ): StreamsViewModel {
        val repository = mockk<WearStreamChannelRepository>(relaxed = true)
        every { repository.observeChannels() } returns catalogFlow
        val preferences = preferencesMock
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
        // S2669: stubbed rather than left relaxed for the same reason as the preference flows above -
        // the view model collects this one, and a relaxed mock answers a Flow-returning call with null.
        val collections = mockk<WearStreamCollectionRepository>(relaxed = true)
        every { collections.observeCollections() } returns flowOf(emptyList())
        return StreamsViewModel(
            repository = repository,
            importCatalogUseCase = importUseCase,
            faviconAtlasStore = atlasStore,
            preferencesRepository = preferences,
            preparePlayback = PrepareWearStreamPlaybackUseCase(
                selectedMediaManager = mockk<SelectedMediaManager>(relaxed = true),
                playbackSetManager = mockk<PlaybackSetManager>(relaxed = true),
                usageRepository = mockk<WearStreamUsageRepository>(relaxed = true),
                preferencesRepository = mockk<WearPreferencesRepository>(relaxed = true),
                passStore = mockk<WearBroadcastPassStore>(relaxed = true),
                resolver = mockk<WearBroadcastPlaybackResolver>(relaxed = true),
            ),
            streamPinsRepository = streamPinsRepository,
            phonePinsRepository = phonePins,
            usageRepository = mockk<WearStreamUsageRepository>(relaxed = true),
            collectionRepository = collections,
        ).also { viewModels += it }
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

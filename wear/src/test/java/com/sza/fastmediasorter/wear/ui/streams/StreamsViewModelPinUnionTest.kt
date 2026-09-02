package com.sza.fastmediasorter.wear.ui.streams

import com.sza.fastmediasorter.wear.data.repository.WearFaviconAtlasStore
import com.sza.fastmediasorter.wear.data.repository.WearPhonePinsRepository
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
 * S2149: the top group is the union of two sources that must stay distinguishable - marks made on this
 * watch, and pins that arrived from the phone.
 *
 * The withdrawal case is the one that carries strategic goal 2: an unpin on the phone has to take its
 * channel out of the top group without touching a mark the owner placed here. The cross-scheme case is
 * the ticket's stated middle-probability risk - the phone and the catalogue spelling one address with
 * different web schemes, which is how S2039 already made a marked station silently never pin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamsViewModelPinUnionTest {

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a channel pinned only on the phone leads the list`() = runBlocking {
        val viewModel = buildViewModel(phonePins = setOf("web://example.invalid/2"))
        delay(SETTLE_MS)

        assertEquals("Morning Report", renderedNames(viewModel).first())
    }

    @Test
    fun `a channel marked only on this watch still leads the list`() = runBlocking {
        val viewModel = buildViewModel(pinnedUrls = listOf("https://example.invalid/3"))
        delay(SETTLE_MS)

        assertEquals("City Cam", renderedNames(viewModel).first())
    }

    @Test
    fun `a channel named by both sources appears once`() = runBlocking {
        val viewModel = buildViewModel(
            pinnedUrls = listOf("https://example.invalid/1"),
            phonePins = setOf("web://example.invalid/1")
        )
        delay(SETTLE_MS)

        val rendered = renderedNames(viewModel)
        assertEquals(listOf("Jazz FM"), rendered.filter { it == "Jazz FM" })
        assertEquals(CATALOG.size, rendered.size)
    }

    @Test
    fun `emptying the phone set withdraws its channel but leaves the watch's own mark`() = runBlocking {
        val phonePins = MutableStateFlow(setOf("web://example.invalid/2"))
        val viewModel = buildViewModel(
            pinnedUrls = listOf("https://example.invalid/3"),
            phonePinsFlow = phonePins
        )
        delay(SETTLE_MS)
        // S2146: the two pinned channels lead in the order the sort left them, which since this ticket
        // is MOST_USED - and on an empty counter that degrades to name ascending, not to catalog rows.
        assertEquals(
            "both sources lead the list while the phone still pins its channel",
            listOf("City Cam", "Morning Report", "Jazz FM"),
            renderedNames(viewModel)
        )

        phonePins.value = emptySet()
        delay(SETTLE_MS)

        // City Cam is the watch's own mark and stays on top; Morning Report drops back into the sorted
        // remainder behind it, which is what an unpin on the phone has to mean.
        assertEquals(
            listOf("City Cam", "Jazz FM", "Morning Report"),
            renderedNames(viewModel)
        )
    }

    /**
     * The phone files every channel under an identity that folds `http` and `https` into one token, so
     * a catalogue row spelled with the other scheme must still match. Comparing raw addresses here is
     * exactly the failure S2039 recorded.
     */
    @Test
    fun `a phone identity matches a catalog channel spelled with the other web scheme`() = runBlocking {
        val catalog = listOf(
            channel(id = "1", name = "Jazz FM", kind = "AUDIO", category = "Music"),
            channel(id = "2", name = "Morning Report", kind = "AUDIO", category = "News")
                .copy(url = "HTTP://Example.INVALID:80/2/"),
        )

        val viewModel = buildViewModel(catalog = catalog, phonePins = setOf("web://example.invalid/2"))
        delay(SETTLE_MS)

        assertEquals("Morning Report", renderedNames(viewModel).first())
    }

    @Test
    fun `the selected sort orders both groups`() = runBlocking {
        val viewModel = buildViewModel(
            pinnedUrls = listOf("https://example.invalid/3"),
            phonePins = setOf("web://example.invalid/1")
        )
        delay(SETTLE_MS)

        viewModel.setSortOrder(StreamSortOrder.NAME_ASC)
        delay(SETTLE_MS)

        // Jazz FM and City Cam are the top group and keep name order inside it; the rest follow.
        assertEquals(listOf("City Cam", "Jazz FM", "Morning Report"), renderedNames(viewModel))
    }

    private fun renderedNames(viewModel: StreamsViewModel): List<String> =
        viewModel.uiState.value.displayChannels.map { it.name }

    private fun buildViewModel(
        catalog: List<WearStreamChannel> = CATALOG,
        pinnedUrls: List<String> = emptyList(),
        phonePins: Set<String> = emptySet(),
        phonePinsFlow: MutableStateFlow<Set<String>> = MutableStateFlow(phonePins)
    ): StreamsViewModel {
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
        val favorites = mockk<WearFavoritesRepository>(relaxed = true)
        coEvery { favorites.getFavorites() } returns pinnedUrls.map { url ->
            WearFavoriteRecord(
                sourceId = SOURCE_ID_STREAM,
                filePath = normalizeWearStreamUrl(url),
                displayName = url,
                itemKind = FAVORITE_ITEM_KIND_STREAM
            )
        }
        val phonePinsRepository = mockk<WearPhonePinsRepository>()
        every { phonePinsRepository.observe() } returns phonePinsFlow
        return StreamsViewModel(
            repository = repository,
            importCatalogUseCase = mockk<ImportWearStreamCatalogUseCase>(relaxed = true),
            faviconAtlasStore = atlasStore,
            preferencesRepository = preferences,
            preparePlayback = PrepareWearStreamPlaybackUseCase(
                selectedMediaManager = mockk<SelectedMediaManager>(relaxed = true),
                playbackSetManager = mockk<PlaybackSetManager>(relaxed = true),
                usageRepository = mockk<WearStreamUsageRepository>(relaxed = true),
            ),
            favoritesRepository = favorites,
            phonePinsRepository = phonePinsRepository,
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

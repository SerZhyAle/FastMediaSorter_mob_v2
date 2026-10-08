package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.settings.StreamsSessionStore
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamCollectionsUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamPlayOutcomesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * S4133: a saved language or country that the catalog no longer carries is cleared back to "All" and the
 * cleared state is persisted, but only once the catalog has produced facets - an empty catalog proves
 * nothing about a saved value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamsStaleFacetSelectionTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun source(id: String, language: String? = null, country: String? = null) = StreamSourceEntity(
        id = id,
        url = "http://example/$id",
        title = id,
        mediaKind = "AUDIO",
        sourceOrigin = "CATALOG",
        sortIndex = 0,
        addedAt = 0L,
        language = language,
        country = country,
    )

    private fun session(language: String?, country: String?) = StreamsSessionStore.Session(
        lastSort = null,
        lastMediaFilter = null,
        lastCategory = null,
        lastTopic = null,
        lastLanguage = language,
        lastCountry = country,
        lastPinnedOnly = null,
        lastCatalogRefreshAt = 0L,
        lastDisplayMode = null,
        lastScrollPosition = null,
    )

    private fun viewModel(sources: List<StreamSourceEntity>, store: StreamsSessionStore): StreamsViewModel {
        val observeStreamSources = mockk<ObserveStreamSourcesUseCase>()
        every { observeStreamSources() } returns flowOf(sources)
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.getSettings() } returns flowOf(AppSettings())
        val favoritesUseCase = mockk<FavoritesUseCase>(relaxed = true)
        every { favoritesUseCase.observeFavoriteStreamIdentities() } returns flowOf(emptySet())
        val observeStreamPlayOutcomes = mockk<ObserveStreamPlayOutcomesUseCase>()
        every { observeStreamPlayOutcomes() } returns flowOf(emptyMap())
        val observeStreamCollections = mockk<ObserveStreamCollectionsUseCase>()
        every { observeStreamCollections() } returns flowOf(emptyList())
        return StreamsViewModel(
            observeStreamSources = observeStreamSources,
            addStreamSource = mockk(relaxed = true),
            updateStreamSource = mockk(relaxed = true),
            importStreamPlaylist = mockk(relaxed = true),
            importStreamCatalog = mockk(relaxed = true),
            importStreamBroadcast = mockk(relaxed = true),
            pinStreamSource = mockk(relaxed = true),
            unpinStreamSource = mockk(relaxed = true),
            reorderPinnedStream = mockk(relaxed = true),
            removeStreamSource = mockk(relaxed = true),
            recordStreamPlayOutcome = mockk(relaxed = true),
            observeStreamPlayOutcomes = observeStreamPlayOutcomes,
            getStreamSourceByUrl = mockk(relaxed = true),
            favoritesUseCase = favoritesUseCase,
            settingsRepository = settingsRepository,
            sessionStore = store,
            networkContextAnalyzer = mockk(relaxed = true),
            streamFramePersistentStore = mockk(relaxed = true),
            streamTrackPreferenceUseCase = mockk(relaxed = true),
            streamResumeStateRepository = mockk(relaxed = true),
            applicationScope = CoroutineScope(dispatcherRule.testDispatcher),
            topicLabelProvider = mockk(relaxed = true),
            defaultDispatcher = dispatcherRule.testDispatcher,
            clearDownloadedStreams = mockk(relaxed = true),
            mediaCapabilities = mockk(relaxed = true),
            sendStreamToWatchUseCase = mockk(relaxed = true),
            observeStreamCollections = observeStreamCollections,
        )
    }

    private fun storeWith(language: String?, country: String?): StreamsSessionStore {
        val store = mockk<StreamsSessionStore>(relaxed = true)
        coEvery { store.read() } returns session(language, country)
        return store
    }

    @Test
    fun `a saved language and country missing from a non-empty catalog are cleared and persisted`() =
        runTest(dispatcherRule.testDispatcher) {
            val store = storeWith(language = "caribbean english", country = "XX")
            val vm = viewModel(listOf(source("a", language = "english", country = "DE")), store)
            advanceUntilIdle()

            assertNull(vm.state.value.filter.language)
            assertNull(vm.state.value.filter.country)
            coVerify(atLeast = 1) {
                store.writeFilterState(any(), any(), any(), any(), isNull(), isNull(), any())
            }
        }

    @Test
    fun `a saved selection is kept while the catalog is still empty`() =
        runTest(dispatcherRule.testDispatcher) {
            val store = storeWith(language = "caribbean english", country = "XX")
            val vm = viewModel(emptyList(), store)
            advanceUntilIdle()

            assertEquals("caribbean english", vm.state.value.filter.language)
            assertEquals("XX", vm.state.value.filter.country)
            coVerify(exactly = 0) {
                store.writeFilterState(any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `a saved selection the catalog still carries is kept`() =
        runTest(dispatcherRule.testDispatcher) {
            val store = storeWith(language = "German", country = "DE")
            val vm = viewModel(listOf(source("a", language = "english,german", country = "DE")), store)
            advanceUntilIdle()

            assertEquals("German", vm.state.value.filter.language)
            assertEquals("DE", vm.state.value.filter.country)
            coVerify(exactly = 0) {
                store.writeFilterState(any(), any(), any(), any(), any(), any(), any())
            }
        }
}

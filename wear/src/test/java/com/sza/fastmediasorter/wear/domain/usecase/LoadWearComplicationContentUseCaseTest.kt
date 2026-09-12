package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearComplicationContent
import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearNowPlaying
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoadWearComplicationContentUseCaseTest {

    private val mockPrefsRepository: WearPreferencesRepository = mockk()
    private val mockNetworkSourceRepository: NetworkSourceRepository = mockk()
    private val mockFavoritesRepository: WearFavoritesRepository = mockk()
    private val mockNowPlayingRepository: WearNowPlayingRepository = mockk()
    private val mockStreamChannelRepository: WearStreamChannelRepository = mockk()

    private lateinit var resolveLastUsedResourceUseCase: ResolveLastUsedResourceUseCase
    private lateinit var useCase: LoadWearComplicationContentUseCase

    @Before
    fun setUp() {
        // S2499: the resolver reads the channel store too. Empty here, so every case in this file keeps
        // describing a watch with no channels rather than one whose channel store was never asked.
        every { mockStreamChannelRepository.observeChannels() } returns flowOf(emptyList())

        resolveLastUsedResourceUseCase = ResolveLastUsedResourceUseCase(
            mockPrefsRepository,
            mockNetworkSourceRepository,
            mockStreamChannelRepository
        )

        useCase = LoadWearComplicationContentUseCase(
            resolveLastUsedResourceUseCase,
            mockNetworkSourceRepository,
            mockFavoritesRepository,
            mockNowPlayingRepository
        )
    }

    @Test
    fun emptyFavoritesYieldsEmptyContent() = runTest {
        coEvery { mockFavoritesRepository.getFavorites() } returns emptyList()

        val result = useCase(WearComplicationKind.FAVOURITES_COUNT)
        assertEquals(WearComplicationContent.Empty, result)
    }

    @Test
    fun nonEmptyFavoritesYieldsCount() = runTest {
        coEvery { mockFavoritesRepository.getFavorites() } returns listOf(
            WearFavoriteRecord("src1", "/path/one", "Item 1"),
            WearFavoriteRecord("src1", "/path/two", "Item 2")
        )

        val result = useCase(WearComplicationKind.FAVOURITES_COUNT)
        assertTrue(result is WearComplicationContent.Value)
        val value = result as WearComplicationContent.Value
        assertEquals("2", value.shortText)
        assertEquals("2 favourites", value.longText)
    }

    @Test
    fun nowPlayingRecordWithIsPlayingFalseStillYieldsTitle() = runTest {
        every { mockNowPlayingRepository.nowPlaying } returns flowOf(
            WearNowPlaying(
                title = "Track A",
                subtitle = "Artist B",
                isPlaying = false,
                updatedAtEpochMs = 5000L
            )
        )

        val result = useCase(WearComplicationKind.NOW_PLAYING)
        assertTrue(result is WearComplicationContent.Value)
        val value = result as WearComplicationContent.Value
        assertEquals("Track A", value.shortText)
        assertEquals("Track A - Artist B", value.longText)
        assertEquals("Last played: Track A", value.contentDescription)
    }

    @Test
    fun lastUsedFilteredOutByResolverYieldsEmpty() = runTest {
        every { mockPrefsRepository.lastUsedResources } returns flowOf(
            listOf(LastUsedResource("deleted_id", "Deleted Resource"))
        )
        every { mockNetworkSourceRepository.observeSources() } returns flowOf(
            listOf(createNetworkSource("active_id", "Active Resource"))
        )
        coEvery { mockNetworkSourceRepository.getAllSources() } returns listOf(
            createNetworkSource("active_id", "Active Resource")
        )

        val result = useCase(WearComplicationKind.LAST_RESOURCE)
        assertEquals(WearComplicationContent.Empty, result)
    }

    private fun createNetworkSource(id: String, name: String) = NetworkSource(
        id = id,
        type = NetworkSourceType.SMB,
        name = name,
        server = "192.168.0.1",
        username = "user",
        password = "pass"
    )
}

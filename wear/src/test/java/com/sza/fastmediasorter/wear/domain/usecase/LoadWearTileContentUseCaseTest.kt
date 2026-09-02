package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteDeltaItem
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearTileAssignmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoadWearTileContentUseCaseTest {

    private lateinit var tileAssignmentRepository: TileContentFakeTileAssignmentRepository
    private lateinit var networkSourceRepository: TileContentFakeNetworkSourceRepository
    private lateinit var wearStreamChannelRepository: TileContentFakeWearStreamChannelRepository
    private lateinit var wearFavoritesRepository: TileContentFakeWearFavoritesRepository
    private lateinit var useCase: LoadWearTileContentUseCase

    @Before
    fun setUp() {
        tileAssignmentRepository = TileContentFakeTileAssignmentRepository()
        networkSourceRepository = TileContentFakeNetworkSourceRepository()
        wearStreamChannelRepository = TileContentFakeWearStreamChannelRepository()
        wearFavoritesRepository = TileContentFakeWearFavoritesRepository()
        useCase = LoadWearTileContentUseCase(
            tileAssignmentRepository,
            networkSourceRepository,
            wearStreamChannelRepository,
            wearFavoritesRepository
        )
    }

    @Test
    fun resourceUnassignedReturnsUnassigned() = runTest {
        val result = useCase(WearTileKind.RESOURCE)
        assertEquals(WearTileContent.Unassigned(WearTileKind.RESOURCE), result)
    }

    @Test
    fun resourceTargetMissingReturnsTargetMissing() = runTest {
        val ref = WearTileTargetRef.Resource(
            id = "src1",
            type = NetworkSourceType.SMB,
            server = "192.168.1.100",
            port = 445,
            shareName = "media",
            basePath = "/photos"
        )
        tileAssignmentRepository.assign(WearTileKind.RESOURCE, ref)
        val result = useCase(WearTileKind.RESOURCE)
        assertEquals(WearTileContent.TargetMissing(WearTileKind.RESOURCE), result)
    }

    @Test
    fun resourceAssignedReturnsAssigned() = runTest {
        val ref = WearTileTargetRef.Resource(
            id = "src1",
            type = NetworkSourceType.SMB,
            server = "192.168.1.100",
            port = 445,
            shareName = "media",
            basePath = "/photos"
        )
        val source = NetworkSource(
            id = "src1",
            name = "NAS SMB",
            type = NetworkSourceType.SMB,
            server = "192.168.1.100",
            port = 445,
            username = "",
            password = "",
            shareName = "media",
            basePath = "/photos"
        )
        tileAssignmentRepository.assign(WearTileKind.RESOURCE, ref)
        networkSourceRepository.sourceList = listOf(source)

        val result = useCase(WearTileKind.RESOURCE)
        assertTrue(result is WearTileContent.Assigned)
        val assigned = result as WearTileContent.Assigned
        assertEquals("NAS SMB", assigned.title)
        assertEquals(WearLaunchTarget.Open(ref), assigned.launchTarget)
    }

    @Test
    fun streamUnassignedReturnsUnassigned() = runTest {
        val result = useCase(WearTileKind.STREAM)
        assertEquals(WearTileContent.Unassigned(WearTileKind.STREAM), result)
    }

    @Test
    fun streamTargetMissingReturnsTargetMissing() = runTest {
        val ref = WearTileTargetRef.Stream("http://stream.example.com/live")
        tileAssignmentRepository.assign(WearTileKind.STREAM, ref)
        val result = useCase(WearTileKind.STREAM)
        assertEquals(WearTileContent.TargetMissing(WearTileKind.STREAM), result)
    }

    @Test
    fun streamAssignedReturnsAssigned() = runTest {
        val ref = WearTileTargetRef.Stream("http://stream.example.com/live")
        tileAssignmentRepository.assign(WearTileKind.STREAM, ref)
        wearStreamChannelRepository.channels = listOf(
            WearStreamChannel(
                id = "c1",
                name = "Radio Live",
                url = "http://stream.example.com/live",
                mediaKind = "AUDIO"
            )
        )

        val result = useCase(WearTileKind.STREAM)
        assertTrue(result is WearTileContent.Assigned)
        val assigned = result as WearTileContent.Assigned
        assertEquals("Radio Live", assigned.title)
        assertEquals(WearLaunchTarget.Open(ref), assigned.launchTarget)
    }

    @Test
    fun favouritesEmptyReturnsFavouritesEmpty() = runTest {
        val result = useCase(WearTileKind.FAVOURITES)
        assertEquals(WearTileContent.FavouritesEmpty, result)
    }

    @Test
    fun favouritesPopulatedReturnsAssignedWithEntries() = runTest {
        wearFavoritesRepository.favorites = listOf(
            WearFavoriteRecord("local", "/path/file1.mp4", "Video 1", itemKind = null),
            WearFavoriteRecord("stream", "http://radio/live", "Radio Stream", itemKind = "stream")
        )

        val result = useCase(WearTileKind.FAVOURITES)
        assertTrue(result is WearTileContent.Assigned)
        val assigned = result as WearTileContent.Assigned
        assertEquals(listOf("Video 1", "Radio Stream"), assigned.entries)
        assertEquals(WearLaunchTarget.Open(WearTileTargetRef.Favourites), assigned.launchTarget)
    }
}

private class TileContentFakeTileAssignmentRepository : WearTileAssignmentRepository {
    private val assignments = mutableMapOf<WearTileKind, WearTileTargetRef>()

    override suspend fun assignmentFor(kind: WearTileKind): WearTileTargetRef? = assignments[kind]

    override suspend fun assign(kind: WearTileKind, ref: WearTileTargetRef) {
        assignments[kind] = ref
    }
}

@Suppress("EmptyFunctionBlock", "UnusedParameter")
private class TileContentFakeNetworkSourceRepository : NetworkSourceRepository {
    var sourceList = listOf<NetworkSource>()
    override suspend fun getAllSources(): List<NetworkSource> = sourceList
    override fun observeSources(): Flow<List<NetworkSource>> = emptyFlow()
    override suspend fun getSourceById(id: String): NetworkSource? = sourceList.find { it.id == id }
    override suspend fun addSource(source: NetworkSource) {}
    override suspend fun updateSource(source: NetworkSource) {}
    override suspend fun upsertSource(source: NetworkSource) {}
    override suspend fun deleteSource(id: String) {}
    override suspend fun testConnection(source: NetworkSource): Result<Boolean> = Result.success(true)
}

@Suppress("EmptyFunctionBlock", "UnusedParameter")
private class TileContentFakeWearStreamChannelRepository : WearStreamChannelRepository {
    var channels = listOf<WearStreamChannel>()
    override suspend fun getAllChannels(): List<WearStreamChannel> = channels
    override fun observeChannels(): Flow<List<WearStreamChannel>> = emptyFlow()
    override suspend fun saveChannels(channels: List<WearStreamChannel>) {}
    override suspend fun clear() {}
    override suspend fun upsertChannel(channel: WearStreamChannel): Boolean = true
}

@Suppress("EmptyFunctionBlock", "UnusedParameter")
private class TileContentFakeWearFavoritesRepository : WearFavoritesRepository {
    var favorites = listOf<WearFavoriteRecord>()
    override suspend fun addFavorite(sourceId: String, filePath: String) {}
    override suspend fun addFavorite(record: WearFavoriteRecord) {}
    override suspend fun getFavorites(): List<WearFavoriteRecord> = favorites
    override suspend fun removeFavorite(sourceId: String, filePath: String) {}
    override suspend fun isFavorite(sourceId: String, filePath: String): Boolean = false
    override suspend fun getPendingDelta(): List<WearFavoriteDeltaItem> = emptyList()
    override suspend fun clearPendingDelta() {}
}

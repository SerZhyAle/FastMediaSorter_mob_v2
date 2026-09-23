package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.catalog.HomeSectionCatalog
import com.sza.fastmediasorter.wear.domain.catalog.WearAppCatalog
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteDeltaItem
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearSourceTombstonePayload
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.model.destinationFor
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearTileAssignmentRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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

    private lateinit var preferencesRepository: WearPreferencesRepository

    /**
     * S2511: only the label lookup is stubbed, and it answers the resource id as text.
     *
     * That makes an assertion about which label a shortcut carries readable without a real resource table,
     * and it is the whole use this class has for a Context.
     */
    private fun contextAnsweringResourceIds(): Context {
        val context = mockk<Context>()
        every { context.getString(any()) } answers { firstArg<Int>().toString() }
        return context
    }

    private fun useCaseWithStreamsSection(enabled: Boolean): LoadWearTileContentUseCase {
        val preferences = mockk<WearPreferencesRepository>()
        every { preferences.streamsSectionEnabled } returns flowOf(enabled)
        return LoadWearTileContentUseCase(
            contextAnsweringResourceIds(),
            tileAssignmentRepository,
            dagger.Lazy { networkSourceRepository },
            wearStreamChannelRepository,
            wearFavoritesRepository,
            preferences,
            TileContentFakeCapabilities()
        )
    }

    @Before
    fun setUp() {
        tileAssignmentRepository = TileContentFakeTileAssignmentRepository()
        networkSourceRepository = TileContentFakeNetworkSourceRepository()
        wearStreamChannelRepository = TileContentFakeWearStreamChannelRepository()
        wearFavoritesRepository = TileContentFakeWearFavoritesRepository()
        preferencesRepository = mockk<WearPreferencesRepository>().also {
            every { it.streamsSectionEnabled } returns flowOf(true)
        }
        useCase = LoadWearTileContentUseCase(
            contextAnsweringResourceIds(),
            tileAssignmentRepository,
            dagger.Lazy { networkSourceRepository },
            wearStreamChannelRepository,
            wearFavoritesRepository,
            preferencesRepository,
            TileContentFakeCapabilities()
        )
    }

    @Test
    fun `the programs grid carries one shortcut per catalog record in catalog order`() = runTest {
        val content = useCase(WearTileKind.PROGRAMS) as WearTileContent.Shortcuts

        assertEquals(
            WearAppCatalog.apps(TileContentFakeCapabilities())
                .map { WearLaunchTarget.Destination(destinationFor(it.id)) },
            content.entries.map { it.launchTarget }
        )
    }

    @Test
    fun `the sections grid follows the streams preference`() = runTest {
        val withStreams = useCaseWithStreamsSection(true)(WearTileKind.SECTIONS)
            as WearTileContent.Shortcuts
        val withoutStreams = useCaseWithStreamsSection(false)(WearTileKind.SECTIONS)
            as WearTileContent.Shortcuts

        val streams = WearLaunchTarget.Destination(WearDestinationId.STREAMS)
        assertTrue(
            "the Streams row is offered while the owner keeps it switched on",
            withStreams.entries.any { it.launchTarget == streams }
        )
        assertTrue(
            "a tile must not offer a section the owner switched off",
            withoutStreams.entries.none { it.launchTarget == streams }
        )
    }

    @Test
    fun `a sections grid names no dynamic row`() = runTest {
        val content = useCase(WearTileKind.SECTIONS) as WearTileContent.Shortcuts

        // The two last-used rows resolve through playback preparation that has not run when a tile is
        // tapped on a cold start, so they are not addressable from outside the process at all.
        assertTrue(
            "every shortcut must name a destination",
            content.entries.all { it.launchTarget is WearLaunchTarget.Destination }
        )
        assertEquals(
            HomeSectionCatalog.sectionsFor(HomeSectionVisibility(streamsEnabled = true))
                .count { destinationFor(it.id) != null },
            content.entries.size
        )
    }

    @Test
    fun `the favourites title comes from resources`() = runTest {
        wearFavoritesRepository.favorites = listOf(
            WearFavoriteRecord(sourceId = "src1", filePath = "/photos/a.jpg", displayName = "a.jpg")
        )

        val content = useCase(WearTileKind.FAVOURITES) as WearTileContent.Assigned

        // The stub answers a resource id as text, so any hardcoded English literal fails this.
        assertEquals(R.string.wear_tile_favourites_label.toString(), content.title)
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

/**
 * S2457: the programs tile reads the same catalog the Apps screen does, so it needs the same build-time
 * answer. Defaults to the offering build - these tests assert the tile mirrors the catalog, and pinning
 * them to the withholding one would make that mirror hold over a shorter list than the screen ever draws.
 */
private class TileContentFakeCapabilities : WearRestrictedCapabilities {
    override val offersCredentialEntry: Boolean = true
    override val offersBodySensorDiagnostics: Boolean = true

    // S2812: the tile catalog does not read this one; it is answered only because the contract has it.
    override val locksSystemShade: Boolean = false
    override val offersHealthFeatures: Boolean = true

    // S3178: the same offering build, so the tile keeps mirroring the full catalog.
    override val offersMediaAccess: Boolean = true
    override val offersVoiceRecording: Boolean = true
    override val offersRemoteSources: Boolean = true
    override val offersDeviceDiagnostics: Boolean = true
    override val offersNearbyDeviceState: Boolean = true
    override val offersScreenCapture: Boolean = true
    override val offersContentTransfer: Boolean = true
    override val offersExternalEntryPoints: Boolean = true

    // S3362: the same offering build again, so the programs grid keeps mirroring the full catalog.
    override val offersScreenTakeoverPrograms: Boolean = true
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
    override suspend fun deleteSourceWithTombstone(id: String, deletedAt: Long) {}
    override suspend fun getTombstones(): List<WearSourceTombstonePayload> = emptyList()
    override suspend fun recordTombstone(tombstone: WearSourceTombstonePayload) {}
    override suspend fun removeTombstone(id: String) {}
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

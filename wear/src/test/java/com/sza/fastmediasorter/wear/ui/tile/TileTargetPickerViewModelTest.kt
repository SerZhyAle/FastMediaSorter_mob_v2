package com.sza.fastmediasorter.wear.ui.tile

import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearTileAssignmentRepository
import com.sza.fastmediasorter.wear.domain.usecase.RequestWearTileRefreshUseCase
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TileTargetPickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tileAssignmentRepository: PickerFakeTileAssignmentRepository
    private lateinit var networkSourceRepository: PickerFakeNetworkSourceRepository
    private lateinit var wearStreamChannelRepository: PickerFakeWearStreamChannelRepository
    private lateinit var refreshUseCase: RequestWearTileRefreshUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tileAssignmentRepository = PickerFakeTileAssignmentRepository()
        networkSourceRepository = PickerFakeNetworkSourceRepository()
        wearStreamChannelRepository = PickerFakeWearStreamChannelRepository()
        refreshUseCase = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectResourceAssignsAndRequestsRefresh() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(WearRoutes.ARG_TILE_KIND to WearTileKind.RESOURCE.name))
        val source = NetworkSource(
            id = "s1",
            name = "NAS",
            type = NetworkSourceType.SMB,
            server = "192.168.1.1",
            port = 445,
            username = "",
            password = "",
            shareName = "share",
            basePath = "/"
        )
        networkSourceRepository.sourceList = listOf(source)

        val viewModel = TileTargetPickerViewModel(
            savedStateHandle = savedStateHandle,
            networkSourceRepository = networkSourceRepository,
            wearStreamChannelRepository = wearStreamChannelRepository,
            wearTileAssignmentRepository = tileAssignmentRepository,
            requestWearTileRefreshUseCase = refreshUseCase
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.rows.size)
        viewModel.selectResource(source)
        testDispatcher.scheduler.advanceUntilIdle()

        val assigned = tileAssignmentRepository.assignmentFor(WearTileKind.RESOURCE)
        assertTrue(assigned is WearTileTargetRef.Resource)
        val resourceRef = assigned as WearTileTargetRef.Resource
        assertEquals("s1", resourceRef.id)
        assertEquals("192.168.1.1", resourceRef.server)
        verify { refreshUseCase(WearTileKind.RESOURCE) }
    }

    @Test
    fun selectStreamAssignsAndRequestsRefresh() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(WearRoutes.ARG_TILE_KIND to WearTileKind.STREAM.name))
        val channel = WearStreamChannel(
            id = "c1",
            name = "Radio",
            url = "http://stream.test/live",
            mediaKind = "AUDIO"
        )
        wearStreamChannelRepository.channels = listOf(channel)

        val viewModel = TileTargetPickerViewModel(
            savedStateHandle = savedStateHandle,
            networkSourceRepository = networkSourceRepository,
            wearStreamChannelRepository = wearStreamChannelRepository,
            wearTileAssignmentRepository = tileAssignmentRepository,
            requestWearTileRefreshUseCase = refreshUseCase
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.rows.size)
        viewModel.selectStream(channel)
        testDispatcher.scheduler.advanceUntilIdle()

        val assigned = tileAssignmentRepository.assignmentFor(WearTileKind.STREAM)
        assertTrue(assigned is WearTileTargetRef.Stream)
        val streamRef = assigned as WearTileTargetRef.Stream
        assertEquals("http://stream.test/live", streamRef.normalizedUrl)
        verify { refreshUseCase(WearTileKind.STREAM) }
    }

    @Test
    fun favouritesKindFinishesImmediately() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(WearRoutes.ARG_TILE_KIND to WearTileKind.FAVOURITES.name))

        val viewModel = TileTargetPickerViewModel(
            savedStateHandle = savedStateHandle,
            networkSourceRepository = networkSourceRepository,
            wearStreamChannelRepository = wearStreamChannelRepository,
            wearTileAssignmentRepository = tileAssignmentRepository,
            requestWearTileRefreshUseCase = refreshUseCase
        )

        assertEquals(WearTileKind.FAVOURITES, viewModel.kind)
    }
}

private class PickerFakeTileAssignmentRepository : WearTileAssignmentRepository {
    private val assignments = mutableMapOf<WearTileKind, WearTileTargetRef>()
    override suspend fun assignmentFor(kind: WearTileKind): WearTileTargetRef? = assignments[kind]
    override suspend fun assign(kind: WearTileKind, ref: WearTileTargetRef) {
        assignments[kind] = ref
    }
}

@Suppress("EmptyFunctionBlock", "UnusedParameter")
private class PickerFakeNetworkSourceRepository : NetworkSourceRepository {
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
private class PickerFakeWearStreamChannelRepository : WearStreamChannelRepository {
    var channels = listOf<WearStreamChannel>()
    override suspend fun getAllChannels(): List<WearStreamChannel> = channels
    override fun observeChannels(): Flow<List<WearStreamChannel>> = emptyFlow()
    override suspend fun saveChannels(channels: List<WearStreamChannel>) {}
    override suspend fun clear() {}
    override suspend fun upsertChannel(channel: WearStreamChannel): Boolean = true
}

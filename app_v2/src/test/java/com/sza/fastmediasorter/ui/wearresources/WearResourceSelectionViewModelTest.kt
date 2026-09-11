package com.sza.fastmediasorter.ui.wearresources

import com.sza.fastmediasorter.data.repository.WearResourceSelectionRepositoryImpl
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearResourceSelectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var resourceRepository: FakeResourceRepository
    private lateinit var selectionRepository: WearResourceSelectionRepositoryImpl
    private lateinit var viewModel: WearResourceSelectionViewModel

    @Before
    fun setup() {
        resourceRepository = FakeResourceRepository()
        selectionRepository = WearResourceSelectionRepositoryImpl(
            context = RuntimeEnvironment.getApplication(),
            ioDispatcher = mainDispatcherRule.testDispatcher
        )
    }

    private fun createViewModel(): WearResourceSelectionViewModel {
        return WearResourceSelectionViewModel(
            resourceRepository = resourceRepository,
            selectionRepository = selectionRepository,
            applicationScope = TestScope(mainDispatcherRule.testDispatcher)
        )
    }

    @Test
    fun `loads only WATCH_TRANSFERABLE resources and excludes LOCAL and VIRTUAL`() = runTest(mainDispatcherRule.testDispatcher) {
        resourceRepository.flow.value = listOf(
            MediaResource(id = 1, name = "SMB Share", path = "\\\\server\\share", type = ResourceType.SMB),
            MediaResource(id = 2, name = "FTP Server", path = "ftp://server", type = ResourceType.FTP),
            MediaResource(id = 3, name = "SFTP Server", path = "sftp://server", type = ResourceType.SFTP),
            MediaResource(id = 4, name = "Local DCIM", path = "/sdcard/DCIM", type = ResourceType.LOCAL),
            MediaResource(id = 5, name = "HTTP Stream", path = "http://stream", type = ResourceType.HTTP_STREAM)
        )

        viewModel = createViewModel()
        advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(3, state.resources.size)
        assertEquals(setOf(1L, 2L, 3L), state.resources.map { it.id }.toSet())
    }

    @Test
    fun `sanitizes saved selection by stripping non-transferable resource IDs`() = runTest(mainDispatcherRule.testDispatcher) {
        resourceRepository.flow.value = listOf(
            MediaResource(id = 10, name = "SMB Share", path = "\\\\server\\share", type = ResourceType.SMB),
            MediaResource(id = 20, name = "Local Folders", path = "/sdcard/Pictures", type = ResourceType.LOCAL)
        )
        // Pretend legacy saved selection contained both SMB (10) and LOCAL (20)
        selectionRepository.setSelectedIds(setOf(10L, 20L))

        viewModel = createViewModel()
        advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf(10L), state.selectedIds)
        assertEquals(setOf(10L), selectionRepository.getSelectedIds())
    }

    @Test
    fun `selectAll only selects loaded transferable resources`() = runTest(mainDispatcherRule.testDispatcher) {
        resourceRepository.flow.value = listOf(
            MediaResource(id = 101, name = "SMB 1", path = "\\\\server\\1", type = ResourceType.SMB),
            MediaResource(id = 102, name = "SFTP 1", path = "sftp://server/1", type = ResourceType.SFTP),
            MediaResource(id = 103, name = "Local 1", path = "/sdcard/1", type = ResourceType.LOCAL)
        )

        viewModel = createViewModel()
        advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()

        viewModel.selectAll()
        advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf(101L, 102L), state.selectedIds)
    }

    private class FakeResourceRepository : ResourceRepository {
        val flow = MutableStateFlow<List<MediaResource>>(emptyList())

        override fun getAllResources(): Flow<List<MediaResource>> = flow
        override suspend fun getAllResourcesSync(): List<MediaResource> = flow.value
        override suspend fun getResourceById(id: Long): MediaResource? = flow.value.firstOrNull { it.id == id }
        override suspend fun getLocalResourceByPath(path: String): MediaResource? = null
        override fun getResourcesByType(type: ResourceType): Flow<List<MediaResource>> = emptyFlow()
        override fun getDestinations(): Flow<List<MediaResource>> = emptyFlow()
        override suspend fun getFilteredResources(
            filterByType: Set<ResourceType>?,
            filterByMediaType: Set<com.sza.fastmediasorter.domain.model.MediaType>?,
            filterByName: String?,
            sortMode: com.sza.fastmediasorter.domain.model.SortMode
        ): List<MediaResource> = flow.value
        override suspend fun addResource(resource: MediaResource): Long = 0
        override suspend fun updateResource(resource: MediaResource) = Unit
        override suspend fun updateResourceAddress(resourceId: Long, newPath: String) = Unit
        override suspend fun swapResourceDisplayOrders(resource1: MediaResource, resource2: MediaResource) = Unit
        override suspend fun updateResourcesDisplayOrder(resources: List<MediaResource>) = Unit
        override suspend fun deleteResource(resourceId: Long) = Unit
        override suspend fun deleteResourceIfHidden(resourceId: Long) = Unit
        override suspend fun deleteAllResources() = Unit
        override suspend fun testConnection(resource: MediaResource): Result<String> = Result.success("ok")
        override suspend fun updateIcon(resourceId: Long, iconId: String?) = Unit
        override suspend fun updateLastViewedFile(resourceId: Long, path: String?) = Unit
        override suspend fun updateLastScrollPosition(resourceId: Long, position: Int) = Unit
        override suspend fun backfillMissingIcons(
            resolveIcon: (path: String, profileName: String, typeName: String) -> String?
        ): Int = 0
    }
}

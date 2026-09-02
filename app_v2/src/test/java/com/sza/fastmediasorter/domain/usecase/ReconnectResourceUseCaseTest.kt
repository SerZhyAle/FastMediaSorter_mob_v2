package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.FavoritesRemapOutcome
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReconnectResourceUseCaseTest {

    private val resourceRepository = mockk<ResourceRepository>()
    private val favoritesRepository = mockk<FavoritesRepository>()
    private val cachedFileListRepository = mockk<CachedFileListRepository>()
    private lateinit var useCase: ReconnectResourceUseCase

    @Before
    fun setup() {
        useCase = ReconnectResourceUseCase(resourceRepository, favoritesRepository, cachedFileListRepository)
    }

    @Test
    fun `rewrites address, remaps favorites from old path and drops cache in order`() = runTest {
        val resource = createMediaResource(id = 7, path = "/storage/emulated/0/Download")
        coEvery { resourceRepository.getResourceById(7) } returns resource
        coEvery { resourceRepository.updateResourceAddress(7, TREE_URI) } returns Unit
        val prefix = slot<String>()
        coEvery {
            favoritesRepository.remapResourceFavoritesToTree(7, capture(prefix), TREE_URI)
        } returns FavoritesRemapOutcome(total = 3, remapped = 2, keptMissing = 1, untouched = 0)
        coEvery { cachedFileListRepository.deleteCachedFiles(7) } returns Unit

        val result = useCase(7, TREE_URI)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().remappedFavorites)
        assertEquals(1, result.getOrThrow().keptFavorites)
        assertEquals("/storage/emulated/0/Download", prefix.captured)
        coVerifyOrder {
            resourceRepository.updateResourceAddress(7, TREE_URI)
            favoritesRepository.remapResourceFavoritesToTree(7, "/storage/emulated/0/Download", TREE_URI)
            cachedFileListRepository.deleteCachedFiles(7)
        }
    }

    @Test
    fun `fails without writes when path is already a tree address`() = runTest {
        coEvery { resourceRepository.getResourceById(7) } returns
            createMediaResource(id = 7, path = "content://tree/primary:Download")

        val result = useCase(7, TREE_URI)

        assertTrue(result.isFailure)
        coVerify {
            resourceRepository.getResourceById(7)
        }
        confirmVerified(resourceRepository, favoritesRepository, cachedFileListRepository)
    }

    @Test
    fun `fails without writes when resource type is not LOCAL`() = runTest {
        coEvery { resourceRepository.getResourceById(9) } returns
            createMediaResource(id = 9, path = "/storage/emulated/0/Download", type = ResourceType.SFTP)

        val result = useCase(9, TREE_URI)

        assertTrue(result.isFailure)
        coVerify {
            resourceRepository.getResourceById(9)
        }
        confirmVerified(resourceRepository, favoritesRepository, cachedFileListRepository)
    }

    @Test
    fun `fails without writes when resource is missing`() = runTest {
        coEvery { resourceRepository.getResourceById(404) } returns null

        val result = useCase(404, TREE_URI)

        assertTrue(result.isFailure)
        coVerify {
            resourceRepository.getResourceById(404)
        }
        confirmVerified(resourceRepository, favoritesRepository, cachedFileListRepository)
    }

    private companion object {
        const val TREE_URI = "content://com.android.providers.downloads.documents/tree/primary:Download"
    }
}

package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateResourceUseCaseTest {

    @Test
    fun `successful update delegates to repository and returns success`() = runTest {
        val repo = FakeResourceRepository()
        repo.setResources(listOf(createMediaResource(id = 1L, name = "old")))
        val useCase = UpdateResourceUseCase(repo)

        val result = useCase(createMediaResource(id = 1L, name = "new"))

        assertTrue(result.isSuccess)
        assertEquals("new", repo.updatedResources.single().name)
    }

    @Test
    fun `repository exception is wrapped as failure`() = runTest {
        val repo = mockk<ResourceRepository>()
        coEvery { repo.updateResource(any()) } throws RuntimeException("write failed")
        val useCase = UpdateResourceUseCase(repo)

        val result = useCase(createMediaResource(id = 1L))

        assertTrue(result.isFailure)
        assertEquals("write failed", result.exceptionOrNull()?.message)
    }

    // S1001: targeted writes must not clobber statistics columns of the same row.

    @Test
    fun `saveScrollPosition updates only the scroll column`() = runTest {
        val repo = FakeResourceRepository()
        repo.setResources(listOf(createMediaResource(id = 1L, name = "res").copy(fileCount = 42, lastBrowseDate = 123L)))
        val useCase = UpdateResourceUseCase(repo)

        val result = useCase.saveScrollPosition(1L, 17)

        assertTrue(result.isSuccess)
        val updated = repo.getResourceById(1L)!!
        assertEquals(17, updated.lastScrollPosition)
        assertEquals(42, updated.fileCount)
        assertEquals(123L, updated.lastBrowseDate)
    }

    @Test
    fun `saveLastViewedFile updates only the last viewed column`() = runTest {
        val repo = FakeResourceRepository()
        repo.setResources(listOf(createMediaResource(id = 1L, name = "res").copy(fileCount = 42, lastBrowseDate = 123L)))
        val useCase = UpdateResourceUseCase(repo)

        val result = useCase.saveLastViewedFile(1L, "/a/b.jpg")

        assertTrue(result.isSuccess)
        val updated = repo.getResourceById(1L)!!
        assertEquals("/a/b.jpg", updated.lastViewedFile)
        assertEquals(42, updated.fileCount)
        assertEquals(123L, updated.lastBrowseDate)
    }
}

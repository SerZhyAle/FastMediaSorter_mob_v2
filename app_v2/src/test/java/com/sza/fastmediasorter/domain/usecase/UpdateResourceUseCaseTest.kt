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
}

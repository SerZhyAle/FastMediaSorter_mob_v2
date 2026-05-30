package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.testing.createScheduledOperation
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import com.sza.fastmediasorter.testing.fakes.FakeScheduledOperationRepository
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeleteResourceUseCaseTest {

    private val resourceRepository = FakeResourceRepository()
    private val scheduledRepository = FakeScheduledOperationRepository()
    private val scheduler = mockk<WorkManagerScheduler>(relaxUnitFun = true)

    private lateinit var useCase: DeleteResourceUseCase

    @Before
    fun setup() {
        useCase = DeleteResourceUseCase(resourceRepository, scheduledRepository, scheduler)
    }

    @Test
    fun `cancels worker for ops referencing the resource as source or target then deletes`() = runTest {
        scheduledRepository.setOperations(
            listOf(
                createScheduledOperation(id = 10L, sourceResourceId = 5L, targetResourceId = 99L),
                createScheduledOperation(id = 11L, sourceResourceId = 1L, targetResourceId = 5L),
                createScheduledOperation(id = 12L, sourceResourceId = 1L, targetResourceId = 2L),
            )
        )

        val result = useCase(resourceId = 5L)

        assertTrue(result.isSuccess)
        verify { scheduler.cancelOperation(10L) }
        verify { scheduler.cancelOperation(11L) }
        verify(exactly = 0) { scheduler.cancelOperation(12L) }
        assertEquals(listOf(5L), resourceRepository.deletedResourceIds)
    }

    @Test
    fun `no scheduled ops still deletes the resource`() = runTest {
        scheduledRepository.setOperations(emptyList())

        val result = useCase(resourceId = 7L)

        assertTrue(result.isSuccess)
        verify(exactly = 0) { scheduler.cancelOperation(any()) }
        assertEquals(listOf(7L), resourceRepository.deletedResourceIds)
    }

    @Test
    fun `repository failure is wrapped as Result failure`() = runTest {
        val failingRepo = mockk<com.sza.fastmediasorter.domain.repository.ResourceRepository>()
        every { failingRepo.toString() } returns "repo"
        io.mockk.coEvery { failingRepo.deleteResource(any()) } throws IllegalStateException("db down")
        val uc = DeleteResourceUseCase(failingRepo, scheduledRepository, scheduler)
        scheduledRepository.setOperations(emptyList())

        val result = uc(resourceId = 1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}

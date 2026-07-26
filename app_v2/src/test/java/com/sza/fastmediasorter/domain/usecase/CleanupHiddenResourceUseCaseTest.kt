package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1009: orphan cleanup deletes only `isHidden` rows (strict 1:1, no reference counting); a reused
 * visible resource is never removed, and null/unset ids are a no-op.
 */
class CleanupHiddenResourceUseCaseTest {

    private lateinit var repository: FakeResourceRepository
    private lateinit var useCase: CleanupHiddenResourceUseCase

    @Before
    fun setup() {
        repository = FakeResourceRepository()
        useCase = CleanupHiddenResourceUseCase(repository)
    }

    @Test
    fun invoke_deletesHiddenResource() = runTest {
        repository.setResources(listOf(resource(id = 1, hidden = true)))

        useCase(1)

        assertTrue("hidden resource must be deleted", repository.resources.none { it.id == 1L })
    }

    @Test
    fun invoke_preservesVisibleResource() = runTest {
        repository.setResources(listOf(resource(id = 2, hidden = false)))

        useCase(2)

        assertTrue("a visible resource must never be auto-deleted", repository.resources.any { it.id == 2L })
    }

    @Test
    fun invoke_nullId_isNoOp() = runTest {
        repository.setResources(listOf(resource(id = 3, hidden = true)))

        useCase(null)

        assertTrue(repository.resources.any { it.id == 3L })
    }

    private fun resource(id: Long, hidden: Boolean): MediaResource =
        MediaResource(id = id, name = "R$id", path = "/p/$id", type = ResourceType.LOCAL, isHidden = hidden)
}

package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1009: the visible-resource use-case must drop `isHidden` rows from the list surface while FK
 * resolution by id keeps returning them.
 */
class GetResourcesUseCaseTest {

    private lateinit var repository: FakeResourceRepository
    private lateinit var useCase: GetResourcesUseCase

    @Before
    fun setup() {
        repository = FakeResourceRepository()
        useCase = GetResourcesUseCase(repository)
    }

    @Test
    fun invoke_excludesHiddenResources() = runTest {
        repository.setResources(
            listOf(
                resource(id = 1, hidden = false),
                resource(id = 2, hidden = true),
                resource(id = 3, hidden = false),
            ),
        )

        val visible = useCase().first()

        assertEquals(listOf(1L, 3L), visible.map { it.id })
        assertTrue("hidden resources must not surface in the list", visible.none { it.isHidden })
    }

    @Test
    fun getById_stillResolvesHiddenResource() = runTest {
        repository.setResources(listOf(resource(id = 42, hidden = true)))

        val resolved = useCase.getById(42)

        assertNotNull("FK resolution must still return a hidden resource", resolved)
        assertEquals(42L, resolved!!.id)
        assertTrue(resolved.isHidden)
    }

    private fun resource(id: Long, hidden: Boolean): MediaResource =
        MediaResource(
            id = id,
            name = "Resource $id",
            path = "/path/$id",
            type = ResourceType.LOCAL,
            isHidden = hidden,
        )
}

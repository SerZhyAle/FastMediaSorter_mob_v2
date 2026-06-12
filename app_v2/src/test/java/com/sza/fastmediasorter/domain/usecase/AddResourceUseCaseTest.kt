package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddResourceUseCaseTest {

    private val repo = FakeResourceRepository()
    private lateinit var useCase: AddResourceUseCase

    @Before
    fun setup() {
        useCase = AddResourceUseCase(repo)
    }

    @Test
    fun `single add assigns displayOrder equal to normalized count`() = runTest {
        // invoke() densifies existing displayOrders to 0..n-1 before appending, so the new
        // resource gets the count (2), not max(existing)+1.
        repo.setResources(
            listOf(
                createMediaResource(id = 1L, displayOrder = 3),
                createMediaResource(id = 2L, displayOrder = 7),
            )
        )

        val result = useCase(createMediaResource(id = 0L, name = "new"))

        assertTrue(result.isSuccess)
        assertEquals(2, repo.addedResources.single().displayOrder)
    }

    @Test
    fun `single add to empty repository gets displayOrder zero`() = runTest {
        repo.setResources(emptyList())

        useCase(createMediaResource(id = 0L))

        assertEquals(0, repo.addedResources.single().displayOrder)
    }

    @Test
    fun `single add failure is wrapped`() = runTest {
        val failing = mockk<ResourceRepository>()
        every { failing.getAllResources() } returns flowOf(emptyList())
        coEvery { failing.addResource(any()) } throws RuntimeException("insert failed")

        val result = AddResourceUseCase(failing)(createMediaResource())

        assertTrue(result.isFailure)
    }

    @Test
    fun `addMultiple assigns sequential display orders`() = runTest {
        // The single existing resource is densified to displayOrder 0, so the two new
        // resources are appended as 1 and 2 (not 6, 7 from the original sparse order).
        repo.setResources(listOf(createMediaResource(id = 1L, displayOrder = 5)))

        useCase.addMultiple(
            listOf(
                createMediaResource(id = 0L, name = "a"),
                createMediaResource(id = 0L, name = "b"),
            )
        )

        assertEquals(listOf(1, 2), repo.addedResources.map { it.displayOrder })
    }

    @Test
    fun `addMultiple assigns destination order and color to destinations`() = runTest {
        repo.setResources(emptyList())

        val result = useCase.addMultiple(
            listOf(
                createMediaResource(id = 0L, name = "d1", isDestination = true),
                createMediaResource(id = 0L, name = "d2", isDestination = true),
            )
        ).getOrThrow()

        assertEquals(2, result.addedCount)
        assertFalse(result.destinationsFull)
        val orders = repo.addedResources.map { it.destinationOrder }
        assertEquals(listOf(0, 1), orders)
    }

    @Test
    fun `addMultiple caps destinations at MAX and demotes overflow`() = runTest {
        // Pre-fill 9 destinations -> only 1 slot remains.
        repo.setResources(
            (1..9).map { createMediaResource(id = it.toLong(), isDestination = true, destinationOrder = it - 1) }
        )

        val result = useCase.addMultiple(
            listOf(
                createMediaResource(id = 0L, name = "keepDest", isDestination = true),
                createMediaResource(id = 0L, name = "overflow1", isDestination = true),
                createMediaResource(id = 0L, name = "overflow2", isDestination = true),
            )
        ).getOrThrow()

        assertEquals(3, result.addedCount)
        assertTrue(result.destinationsFull)
        assertEquals(2, result.skippedDestinations)
        val demoted = repo.addedResources.filter { it.name.startsWith("overflow") }
        assertTrue(demoted.all { !it.isDestination && it.destinationOrder == null })
        assertTrue(repo.addedResources.first { it.name == "keepDest" }.isDestination)
    }

    @Test
    fun `addMultiple leaves non-destinations untouched on destination fields`() = runTest {
        repo.setResources(emptyList())

        useCase.addMultiple(listOf(createMediaResource(id = 0L, name = "plain", isDestination = false)))

        val added = repo.addedResources.single()
        assertFalse(added.isDestination)
    }
}

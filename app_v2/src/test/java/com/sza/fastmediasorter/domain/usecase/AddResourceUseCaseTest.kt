package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
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

    // S1012 add-or-update (companion import) --------------------------------

    @Test
    fun `addMultiple add-or-update inserts when no existing path matches`() = runTest {
        repo.setResources(
            listOf(createMediaResource(id = 1L, path = "sftp://host/a", type = ResourceType.SFTP, displayOrder = 0))
        )

        val result = useCase.addMultiple(
            listOf(createMediaResource(id = 0L, name = "b", path = "sftp://host/b", type = ResourceType.SFTP)),
            matchExistingByPath = true
        ).getOrThrow()

        assertEquals(1, result.addedCount)
        assertEquals(0, result.updatedCount)
        assertEquals(listOf("b"), result.addedNames)
        assertEquals(1, repo.addedResources.size)
        assertTrue(repo.updatedResources.isEmpty())
    }

    @Test
    fun `addMultiple add-or-update updates matching resource instead of inserting`() = runTest {
        repo.setResources(
            listOf(
                createMediaResource(
                    id = 5L, name = "existing", path = "sftp://host/photos",
                    type = ResourceType.SFTP, displayOrder = 0,
                    supportedMediaTypes = setOf(MediaType.IMAGE)
                )
            )
        )

        val result = useCase.addMultiple(
            listOf(
                createMediaResource(
                    id = 0L, name = "reimport", path = "sftp://host/photos",
                    type = ResourceType.SFTP, supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO)
                )
            ),
            matchExistingByPath = true
        ).getOrThrow()

        assertEquals(0, result.addedCount)
        assertEquals(1, result.updatedCount)
        // Name is preserved (not config-authoritative), so the reported name is the existing one.
        assertEquals(listOf("existing"), result.updatedNames)
        assertTrue(repo.addedResources.isEmpty())
        val updated = repo.updatedResources.single()
        assertEquals(5L, updated.id)
        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO), updated.supportedMediaTypes)
    }

    @Test
    fun `addMultiple add-or-update handles mixed add and update`() = runTest {
        repo.setResources(
            listOf(
                createMediaResource(
                    id = 5L, name = "keep", path = "sftp://host/photos",
                    type = ResourceType.SFTP, displayOrder = 0,
                    supportedMediaTypes = setOf(MediaType.IMAGE)
                )
            )
        )

        val result = useCase.addMultiple(
            listOf(
                createMediaResource(
                    id = 0L, name = "keep-updated", path = "sftp://host/photos",
                    type = ResourceType.SFTP, supportedMediaTypes = setOf(MediaType.VIDEO)
                ),
                createMediaResource(id = 0L, name = "fresh", path = "sftp://host/movies", type = ResourceType.SFTP)
            ),
            matchExistingByPath = true
        ).getOrThrow()

        assertEquals(1, result.addedCount)
        assertEquals(1, result.updatedCount)
        assertEquals(listOf("fresh"), result.addedNames)
        assertEquals(listOf("keep"), result.updatedNames)
    }

    @Test
    fun `addMultiple add-or-update preserves user fields and overwrites config fields`() = runTest {
        val customColor = 0xFFAB1234.toInt()
        repo.setResources(
            listOf(
                createMediaResource(
                    id = 1L, name = "control", path = "sftp://host/other",
                    type = ResourceType.SFTP, displayOrder = 0
                ),
                createMediaResource(
                    id = 5L, name = "My SFTP", path = "sftp://host/photos", type = ResourceType.SFTP,
                    displayOrder = 1, iconId = "ico-01-001", sortMode = SortMode.DATE_DESC,
                    displayMode = DisplayMode.GRID, fileCount = 42, isDestination = true,
                    destinationOrder = 2, destinationColor = customColor,
                    supportedMediaTypes = setOf(MediaType.IMAGE), isReadOnly = true, scanSubdirectories = false
                )
            )
        )

        useCase.addMultiple(
            listOf(
                createMediaResource(
                    id = 0L, name = "Companion Photos", path = "sftp://host/photos", type = ResourceType.SFTP,
                    credentialsId = "cred-xyz", supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO),
                    isReadOnly = false, scanSubdirectories = true
                )
            ),
            matchExistingByPath = true
        ).getOrThrow()

        assertTrue(repo.addedResources.isEmpty())
        val merged = repo.updatedResources.single()
        // Preserved user/runtime fields (the S1001/§7 clobber regression guard).
        assertEquals(5L, merged.id)
        assertEquals("My SFTP", merged.name)
        assertEquals("ico-01-001", merged.iconId)
        assertEquals(SortMode.DATE_DESC, merged.sortMode)
        assertEquals(DisplayMode.GRID, merged.displayMode)
        assertEquals(1, merged.displayOrder)
        assertEquals(42, merged.fileCount)
        assertTrue(merged.isDestination)
        assertEquals(2, merged.destinationOrder)
        assertEquals(customColor, merged.destinationColor)
        // Overwritten config-authoritative fields.
        assertEquals("cred-xyz", merged.credentialsId)
        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO), merged.supportedMediaTypes)
        assertFalse(merged.isReadOnly)
        assertTrue(merged.scanSubdirectories)
    }

    @Test
    fun `addMultiple add-or-update reports zero updated for byte-identical reimport`() = runTest {
        val shared = createMediaResource(
            id = 7L, name = "same", path = "sftp://host/photos", type = ResourceType.SFTP,
            displayOrder = 0, credentialsId = "cred-1", supportedMediaTypes = setOf(MediaType.IMAGE),
            scanSubdirectories = true, isReadOnly = true
        )
        repo.setResources(listOf(shared))

        val result = useCase.addMultiple(
            listOf(shared.copy(id = 0L, name = "ignored-on-match")),
            matchExistingByPath = true
        ).getOrThrow()

        assertEquals(0, result.addedCount)
        assertEquals(0, result.updatedCount)
        assertTrue(repo.addedResources.isEmpty())
        assertTrue(repo.updatedResources.isEmpty())
    }

    @Test
    fun `addMultiple add-or-update matches ignoring port and trailing slash`() = runTest {
        repo.setResources(
            listOf(
                createMediaResource(
                    id = 9L, name = "existing", path = "sftp://host:2222/photos/",
                    type = ResourceType.SFTP, displayOrder = 0, supportedMediaTypes = setOf(MediaType.IMAGE)
                )
            )
        )

        val result = useCase.addMultiple(
            listOf(
                createMediaResource(
                    id = 0L, name = "reimport", path = "sftp://host/photos",
                    type = ResourceType.SFTP, supportedMediaTypes = setOf(MediaType.VIDEO)
                )
            ),
            matchExistingByPath = true
        ).getOrThrow()

        assertEquals(0, result.addedCount)
        assertEquals(1, result.updatedCount)
        assertTrue(repo.addedResources.isEmpty())
        assertEquals("sftp://host/photos", repo.updatedResources.single().path)
    }
}

package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MigrateCameraResourceUseCaseTest {

    private val resourceRepository: ResourceRepository = mockk(relaxed = true)
    private lateinit var useCase: MigrateCameraResourceUseCase

    @Before
    fun setUp() {
        useCase = MigrateCameraResourceUseCase(resourceRepository)
    }

    @Test
    fun `invoke updates old physical camera path to virtual path`() = runTest {
        val oldPhysicalPath = "/storage/emulated/0/DCIM/Camera"
        val existingResource = MediaResource(
            id = 1,
            name = "Camera",
            path = oldPhysicalPath,
            type = ResourceType.LOCAL,
            createdDate = 1000L,
            sortMode = SortMode.NAME_ASC
        )
        coEvery { resourceRepository.getAllResources() } returns flowOf(listOf(existingResource))

        useCase()

        coVerify {
            resourceRepository.updateResource(
                existingResource.copy(
                    path = LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
                    sortMode = SortMode.DATE_DESC
                )
            )
        }
    }

    @Test
    fun `invoke does nothing if old physical camera path not found`() = runTest {
        val existingResource = MediaResource(
            id = 1,
            name = "Downloads",
            path = "/storage/emulated/0/Download",
            type = ResourceType.LOCAL,
            createdDate = 1000L,
            sortMode = SortMode.NAME_ASC
        )
        coEvery { resourceRepository.getAllResources() } returns flowOf(listOf(existingResource))

        useCase()

        coVerify(exactly = 0) {
            resourceRepository.updateResource(any())
        }
    }

    @Test
    fun `invoke does nothing if virtual path already exists instead of old physical path`() = runTest {
        val existingResource = MediaResource(
            id = 1,
            name = "Camera Photos",
            path = LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
            type = ResourceType.LOCAL,
            createdDate = 1000L,
            sortMode = SortMode.DATE_DESC
        )
        coEvery { resourceRepository.getAllResources() } returns flowOf(listOf(existingResource))

        useCase()

        coVerify(exactly = 0) {
            resourceRepository.updateResource(any())
        }
    }
}

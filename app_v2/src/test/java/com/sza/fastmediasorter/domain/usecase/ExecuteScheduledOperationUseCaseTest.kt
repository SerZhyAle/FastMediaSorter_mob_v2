package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.createScheduledOperation
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import com.sza.fastmediasorter.testing.fakes.FakeScheduledOperationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExecuteScheduledOperationUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scheduledRepo = FakeScheduledOperationRepository()
    private val resourceRepo = FakeResourceRepository()
    private val getMediaFiles = mockk<GetMediaFilesUseCase>()
    private val fileOperationUseCase = mockk<FileOperationUseCase>()
    private val appendLog = mockk<AppendToScheduledLogUseCase>(relaxed = true)

    private lateinit var useCase: ExecuteScheduledOperationUseCase
    private lateinit var targetDir: File

    @Before
    fun setup() {
        targetDir = tempFolder.newFolder("dest")
        useCase = ExecuteScheduledOperationUseCase(
            scheduledRepo, resourceRepo, getMediaFiles, fileOperationUseCase, appendLog,
        )
    }

    private fun stubFiles(files: List<MediaFile>) {
        every {
            getMediaFiles(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns flowOf(files)
    }

    @Test
    fun `missing operation returns error result`() = runTest {
        val result = useCase(123L)

        assertFalse(result.isSuccess)
        assertEquals(123L, result.operationId)
    }

    @Test
    fun `disabled operation returns error result`() = runTest {
        scheduledRepo.setOperations(listOf(createScheduledOperation(id = 1L, isEnabled = false)))

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertTrue(result.errors.first().contains("disabled"))
    }

    @Test
    fun `missing source resource returns error`() = runTest {
        scheduledRepo.setOperations(listOf(createScheduledOperation(id = 1L, sourceResourceId = 7L, targetResourceId = 8L)))
        resourceRepo.setResources(emptyList())

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertTrue(result.errors.first().contains("Source resource not found"))
    }

    @Test
    fun `missing target resource for copy returns error`() = runTest {
        scheduledRepo.setOperations(
            listOf(createScheduledOperation(id = 1L, sourceResourceId = 1L, targetResourceId = 2L, operationType = ScheduledOpType.COPY))
        )
        resourceRepo.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL)))

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertTrue(result.errors.first().contains("Target resource not found"))
    }

    @Test
    fun `empty file set yields success with zero processed`() = runTest {
        scheduledRepo.setOperations(
            listOf(createScheduledOperation(id = 1L, sourceResourceId = 1L, targetResourceId = 2L, operationType = ScheduledOpType.COPY))
        )
        resourceRepo.setResources(
            listOf(
                createMediaResource(id = 1L, type = ResourceType.LOCAL, path = "/src"),
                createMediaResource(id = 2L, type = ResourceType.LOCAL, path = targetDir.absolutePath),
            )
        )
        stubFiles(emptyList())

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(0, result.filesProcessed)
    }

    @Test
    fun `local copy target missing directory fails reachability`() = runTest {
        scheduledRepo.setOperations(
            listOf(createScheduledOperation(id = 1L, sourceResourceId = 1L, targetResourceId = 2L, operationType = ScheduledOpType.COPY))
        )
        resourceRepo.setResources(
            listOf(
                createMediaResource(id = 1L, type = ResourceType.LOCAL, path = "/src"),
                createMediaResource(id = 2L, type = ResourceType.LOCAL, path = "/does/not/exist"),
            )
        )
        stubFiles(listOf(createMediaFile(name = "a.jpg", path = "/src/a.jpg", type = MediaType.IMAGE)))

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertTrue(result.errors.first().contains("does not exist"))
    }

    @Test
    fun `copy success increments processed count`() = runTest {
        scheduledRepo.setOperations(
            listOf(createScheduledOperation(id = 1L, sourceResourceId = 1L, targetResourceId = 2L, operationType = ScheduledOpType.COPY, fileTypeMask = FileTypeFlags.IMAGES, timeFilter = TimeFilter.ALL))
        )
        resourceRepo.setResources(
            listOf(
                createMediaResource(id = 1L, type = ResourceType.LOCAL, path = "/src"),
                createMediaResource(id = 2L, type = ResourceType.LOCAL, path = targetDir.absolutePath),
            )
        )
        stubFiles(
            listOf(
                createMediaFile(name = "a.jpg", path = "/src/a.jpg", type = MediaType.IMAGE),
                createMediaFile(name = "b.jpg", path = "/src/b.jpg", type = MediaType.IMAGE),
            )
        )
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(2, result.filesProcessed)
    }

    @Test
    fun `copy failure is recorded as error`() = runTest {
        scheduledRepo.setOperations(
            listOf(createScheduledOperation(id = 1L, sourceResourceId = 1L, targetResourceId = 2L, operationType = ScheduledOpType.COPY, fileTypeMask = FileTypeFlags.IMAGES))
        )
        resourceRepo.setResources(
            listOf(
                createMediaResource(id = 1L, type = ResourceType.LOCAL, path = "/src"),
                createMediaResource(id = 2L, type = ResourceType.LOCAL, path = targetDir.absolutePath),
            )
        )
        stubFiles(listOf(createMediaFile(name = "a.jpg", path = "/src/a.jpg", type = MediaType.IMAGE)))
        coEvery { fileOperationUseCase.execute(any()) } returns FileOperationResult.Failure("disk full")

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertTrue(result.errors.contains("disk full"))
    }

    @Test
    fun `move deletes source after successful copy`() = runTest {
        scheduledRepo.setOperations(
            listOf(createScheduledOperation(id = 1L, sourceResourceId = 1L, targetResourceId = 2L, operationType = ScheduledOpType.MOVE, fileTypeMask = FileTypeFlags.IMAGES))
        )
        resourceRepo.setResources(
            listOf(
                createMediaResource(id = 1L, type = ResourceType.LOCAL, path = "/src"),
                createMediaResource(id = 2L, type = ResourceType.LOCAL, path = targetDir.absolutePath),
            )
        )
        stubFiles(listOf(createMediaFile(name = "a.jpg", path = "/src/a.jpg", type = MediaType.IMAGE)))
        coEvery { fileOperationUseCase.execute(any<FileOperation.Copy>()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))
        coEvery { fileOperationUseCase.execute(any<FileOperation.Delete>()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(1, result.filesProcessed)
        coVerify { fileOperationUseCase.execute(any<FileOperation.Delete>()) }
    }

    @Test
    fun `delete operation needs no target and processes files`() = runTest {
        scheduledRepo.setOperations(
            listOf(createScheduledOperation(id = 1L, sourceResourceId = 1L, targetResourceId = null, operationType = ScheduledOpType.DELETE, fileTypeMask = FileTypeFlags.IMAGES))
        )
        resourceRepo.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL, path = "/src")))
        stubFiles(listOf(createMediaFile(name = "a.jpg", path = "/src/a.jpg", type = MediaType.IMAGE)))
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(1, result.filesProcessed)
    }

    @Test
    fun `type mask filters out non-matching files`() = runTest {
        scheduledRepo.setOperations(
            listOf(createScheduledOperation(id = 1L, sourceResourceId = 1L, targetResourceId = null, operationType = ScheduledOpType.DELETE, fileTypeMask = FileTypeFlags.IMAGES))
        )
        resourceRepo.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL, path = "/src")))
        stubFiles(
            listOf(
                createMediaFile(name = "a.jpg", path = "/src/a.jpg", type = MediaType.IMAGE),
                createMediaFile(name = "v.mp4", path = "/src/v.mp4", type = MediaType.VIDEO), // filtered out
            )
        )
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val result = useCase(1L)

        // Only the image should be processed; video is filtered by the IMAGES mask.
        assertEquals(1, result.filesProcessed)
    }
}

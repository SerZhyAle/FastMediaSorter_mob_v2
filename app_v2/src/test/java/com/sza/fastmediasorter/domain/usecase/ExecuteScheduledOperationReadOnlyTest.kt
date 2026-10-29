package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.mutation.InMemoryMutationJournal
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunReason
import com.sza.fastmediasorter.domain.mutation.MutationRecorder
import com.sza.fastmediasorter.domain.path.PathNormalizer
import com.sza.fastmediasorter.domain.usecase.transfer.FetchScheduledChangesViaDriveUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S4260: the engine honours a resource's read-only policy itself. The schedule dialog hides the write
 * actions of a read-only source, but a schedule saved earlier, or created through another path, still
 * reaches the executor. The scan seam is deliberately not stubbed in the refusal tests - a refused run
 * must never scan, let alone transfer.
 */
class ExecuteScheduledOperationReadOnlyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scheduledRepo = FakeScheduledOperationRepository()
    private val resourceRepo = FakeResourceRepository()
    private val getMediaFiles = mockk<GetMediaFilesUseCase>()
    private val fileOperationUseCase = mockk<FileOperationUseCase>()

    private lateinit var useCase: ExecuteScheduledOperationUseCase
    private lateinit var targetDir: File

    private val pathNormalizer = object : PathNormalizer {
        override fun canonical(rawPath: String, resourceType: ResourceType): String = rawPath
    }

    @Before
    fun setup() {
        targetDir = tempFolder.newFolder("dest")
        useCase = ExecuteScheduledOperationUseCase(
            scheduledRepo,
            resourceRepo,
            getMediaFiles,
            fileOperationUseCase,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            MutationRecorder(InMemoryMutationJournal(), pathNormalizer),
            ArchiveFilesUseCase(mockk(relaxed = true), mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk<FetchScheduledChangesViaDriveUseCase>(),
            mockk(relaxed = true),
        )
    }

    private fun local(id: Long, path: String, readOnly: Boolean): MediaResource = createMediaResource(
        id = id,
        name = "Locked",
        type = ResourceType.LOCAL,
        path = path,
        isReadOnly = readOnly,
    )

    private fun seed(operationType: ScheduledOpType, sourceReadOnly: Boolean, targetReadOnly: Boolean) {
        val source = local(id = 1L, path = "/src", readOnly = sourceReadOnly)
        val target = local(id = 2L, path = targetDir.absolutePath, readOnly = targetReadOnly)
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = source.id,
                    targetResourceId = target.id,
                    operationType = operationType,
                    fileTypeMask = FileTypeFlags.IMAGES,
                )
            )
        )
        resourceRepo.setResources(listOf(source, target))
    }

    /** A refused run is a skip that names the read-only side and reaches no transfer. */
    private suspend fun assertRefused(role: String) {
        val result = useCase(1L)

        assertTrue(result.isSkipped)
        assertEquals(ScheduledRunOutcome.SKIPPED, result.outcome)
        assertEquals(0, result.filesProcessed)
        assertTrue(result.skippedReason!!.startsWith("$role 'Locked' is read-only"))
        val expected = if (role == "Source") {
            ScheduledRunReason.SOURCE_READ_ONLY
        } else {
            ScheduledRunReason.TARGET_READ_ONLY
        }
        assertEquals(expected, result.reason)
        coVerify(exactly = 0) { fileOperationUseCase.execute(any()) }
    }

    @Test
    fun `move out of a read-only source is skipped and removes nothing`() = runTest {
        seed(ScheduledOpType.MOVE, sourceReadOnly = true, targetReadOnly = false)

        assertRefused("Source")
    }

    @Test
    fun `delete in a read-only source is skipped and removes nothing`() = runTest {
        seed(ScheduledOpType.DELETE, sourceReadOnly = true, targetReadOnly = false)

        assertRefused("Source")
    }

    @Test
    fun `copy into a read-only target is skipped and writes nothing`() = runTest {
        seed(ScheduledOpType.COPY, sourceReadOnly = false, targetReadOnly = true)

        assertRefused("Target")
    }

    @Test
    fun `move into a read-only target is skipped and leaves the source alone`() = runTest {
        seed(ScheduledOpType.MOVE, sourceReadOnly = false, targetReadOnly = true)

        assertRefused("Target")
    }

    @Test
    fun `archive into a read-only target is skipped and creates no zip`() = runTest {
        seed(ScheduledOpType.ARCHIVE, sourceReadOnly = false, targetReadOnly = true)

        assertRefused("Target")
        assertTrue(targetDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `copy out of a read-only source still transfers because the source is only read`() = runTest {
        seed(ScheduledOpType.COPY, sourceReadOnly = true, targetReadOnly = false)
        every {
            getMediaFiles(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns flowOf(listOf(createMediaFile(name = "a.jpg", path = "/src/a.jpg", type = MediaType.IMAGE)))
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(1, result.filesProcessed)
    }
}

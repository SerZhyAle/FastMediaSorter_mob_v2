package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.mutation.InMemoryMutationJournal
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.mutation.Mutation
import com.sza.fastmediasorter.domain.mutation.MutationRecorder
import com.sza.fastmediasorter.domain.path.PathNormalizer
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

    /**
     * S3376: the real in-memory journal rather than a mock, so a test asserts the entry the reconciler
     * would actually read back instead of asserting that a method was called.
     */
    private val journal = InMemoryMutationJournal()

    /** Identity normalizer - the canonical form is not what these tests are about. */
    private val pathNormalizer = object : PathNormalizer {
        override fun canonical(rawPath: String, resourceType: ResourceType): String = rawPath
    }

    @Before
    fun setup() {
        targetDir = tempFolder.newFolder("dest")
        useCase = ExecuteScheduledOperationUseCase(
            scheduledRepo, resourceRepo, getMediaFiles, fileOperationUseCase, appendLog,
            // checkLocalFolderWritable: only consulted for content:// targets, which these tests
            // do not use (they point at a real temp folder), so a relaxed mock is enough.
            mockk(relaxed = true),
            mockk(relaxed = true),
            // statsSink: these tests assert the operation's file outcome, not the counters it feeds,
            // so a relaxed mock records the events and asserts nothing about them.
            mockk(relaxed = true),
            MutationRecorder(journal, pathNormalizer),
        )
    }

    /** Every unapplied entry filed under [resourceId], in seq order. */
    private fun recorded(resourceId: Long): List<Mutation> =
        journal.pendingFor(resourceId, 0L).map { it.mutation }

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
    fun `move executes native move operation`() = runTest {
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
        coEvery { fileOperationUseCase.execute(any<FileOperation.Move>()) } returns
            FileOperationResult.Success(1, FileOperation.Move(emptyList(), targetDir, overwrite = false))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(1, result.filesProcessed)
        coVerify(exactly = 1) {
            fileOperationUseCase.execute(match<FileOperation.Move> {
                it.sources.single().path.replace('\\', '/').endsWith("/src/a.jpg") &&
                    it.destination == targetDir &&
                    !it.overwrite
            })
        }
        // S1204: ofType, not any<FileOperation.Delete>. The type argument on any<T> is erased, so
        // that matcher accepts the Move call above and turns this line into "execute was never
        // called" - which contradicts the exactly = 1 verification directly above it.
        coVerify(exactly = 0) { fileOperationUseCase.execute(ofType<FileOperation.Delete>()) }
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

    // ── S3376: mutation journal registration ──────────────────────────────────
    //
    // The scheduler is a background actor: nothing calls reloadFiles() when a schedule fires while
    // Browse is open on the affected resource, and a remote resource is outside the local
    // FileObserver's reach, so the journal is the only channel that reaches the surface. What these
    // tests defend is the binding to the success branch - a record on a skip, a failure or a
    // permission wall would tell the reconciler to drop a row whose file is still on disk.

    private fun singleImageOperation(opType: ScheduledOpType, withTarget: Boolean) {
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = if (withTarget) 2L else null,
                    operationType = opType,
                    fileTypeMask = FileTypeFlags.IMAGES
                )
            )
        )
        val resources = mutableListOf(
            createMediaResource(id = 1L, type = ResourceType.LOCAL, path = "/src")
        )
        if (withTarget) {
            resources += createMediaResource(id = 2L, type = ResourceType.LOCAL, path = targetDir.absolutePath)
        }
        resourceRepo.setResources(resources)
        stubFiles(listOf(createMediaFile(name = "a.jpg", path = "/src/a.jpg", type = MediaType.IMAGE)))
    }

    @Test
    fun `scheduled delete records one Delete mutation under the source resource`() = runTest {
        singleImageOperation(ScheduledOpType.DELETE, withTarget = false)
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        useCase(1L)

        val delete = recorded(1L).single() as Mutation.Delete
        assertEquals(1L, delete.resourceId)
        assertEquals("/src/a.jpg", delete.canonicalPath)
    }

    @Test
    fun `scheduled move records one Move mutation naming both resources`() = runTest {
        singleImageOperation(ScheduledOpType.MOVE, withTarget = true)
        coEvery { fileOperationUseCase.execute(any<FileOperation.Move>()) } returns
            FileOperationResult.Success(1, FileOperation.Move(emptyList(), targetDir, overwrite = false))

        useCase(1L)

        val move = recorded(1L).single() as Mutation.Move
        assertEquals(1L, move.srcResourceId)
        assertEquals(2L, move.dstResourceId)
        assertEquals("/src/a.jpg", move.oldCanonicalPath)
        assertTrue(move.newCanonicalPath.endsWith("a.jpg"))
    }

    @Test
    fun `scheduled copy records nothing`() = runTest {
        singleImageOperation(ScheduledOpType.COPY, withTarget = true)
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        useCase(1L)

        assertTrue(recorded(1L).isEmpty())
        assertTrue(recorded(2L).isEmpty())
    }

    @Test
    fun `skipped file records nothing`() = runTest {
        singleImageOperation(ScheduledOpType.DELETE, withTarget = false)
        // skippedCount > 0 takes handleFileResult's SKIP branch, which never calls incrementSuccess.
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()), skippedCount = 1)

        val result = useCase(1L)

        assertEquals(0, result.filesProcessed)
        assertTrue(recorded(1L).isEmpty())
    }

    @Test
    fun `failed delete records nothing`() = runTest {
        singleImageOperation(ScheduledOpType.DELETE, withTarget = false)
        coEvery { fileOperationUseCase.execute(any()) } returns FileOperationResult.Failure("disk full")

        useCase(1L)

        assertTrue(recorded(1L).isEmpty())
    }

    @Test
    fun `permission wall records nothing`() = runTest {
        singleImageOperation(ScheduledOpType.DELETE, withTarget = false)
        // AuthenticationRequired rather than PermissionRequired: both take handleFileResult's
        // setPermissionStop branch, and this one carries no Android PendingIntent to mock.
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.AuthenticationRequired("Dropbox", "token expired")

        val result = useCase(1L)

        assertTrue(result.permissionRequired)
        assertTrue(recorded(1L).isEmpty())
    }
}

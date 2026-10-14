package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.mutation.InMemoryMutationJournal
import com.sza.fastmediasorter.data.network.exceptions.NetworkPinRefusedException
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
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
import kotlinx.coroutines.flow.flow
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

    // S4232: relaxed - the run-start temp cleanup is asserted by coVerify on the call, and a
    // relaxed mock keeps every other test's constructor wiring free of per-test stubbing.
    private val cleanup = mockk<CleanupOrphanedTempFilesUseCase>(relaxed = true)

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
            // The real archiver: a local-to-local run never touches Context (only error strings and
            // content:// reads do), so these tests assert the ZIP that actually lands on disk.
            ArchiveFilesUseCase(mockk(relaxed = true), mockk(relaxed = true)),
            cleanup,
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
        scheduledRepo.setOperations(
            listOf(createScheduledOperation(id = 1L, sourceResourceId = 7L, targetResourceId = 8L))
        )
        resourceRepo.setResources(emptyList())

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertTrue(result.errors.first().contains("Source resource not found"))
    }

    @Test
    fun `missing target resource for copy returns error`() = runTest {
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = 2L,
                    operationType = ScheduledOpType.COPY
                )
            )
        )
        resourceRepo.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL)))

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertTrue(result.errors.first().contains("Target resource not found"))
    }

    @Test
    fun `empty file set yields success with zero processed`() = runTest {
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = 2L,
                    operationType = ScheduledOpType.COPY
                )
            )
        )
        resourceRepo.setResources(
            listOf(
                createMediaResource(
                    id = 1L,
                    type = ResourceType.LOCAL,
                    path = tempFolder.newFolder("src").absolutePath
                ),
                createMediaResource(id = 2L, type = ResourceType.LOCAL, path = targetDir.absolutePath),
            )
        )
        stubFiles(emptyList())

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(0, result.filesProcessed)
    }

    /** One copy/move schedule between [source] and [target] with the given window state. */
    private fun seedSchedule(
        source: MediaResource,
        target: MediaResource,
        operationType: ScheduledOpType = ScheduledOpType.COPY,
        timeFilter: TimeFilter = TimeFilter.ALL,
        window: WindowState = WindowState(),
    ) {
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = source.id,
                    targetResourceId = target.id,
                    operationType = operationType,
                    fileTypeMask = FileTypeFlags.IMAGES,
                    timeFilter = timeFilter,
                    lastRunAt = window.lastRunAt,
                    lastRunStatus = window.lastRunStatus,
                ).copy(lastSuccessAt = window.lastSuccessAt)
            )
        )
        resourceRepo.setResources(listOf(source, target))
    }

    /** What the stored row says about earlier runs; the SINCE_LAST marker is derived from it. */
    private data class WindowState(
        val lastRunAt: Long? = null,
        val lastRunStatus: String? = null,
        val lastSuccessAt: Long? = null,
    )

    private fun localResource(id: Long, path: String) =
        createMediaResource(id = id, type = ResourceType.LOCAL, path = path)

    private fun imageNamed(name: String, createdDate: Long = 0L) =
        createMediaFile(name = name, path = "/src/$name", type = MediaType.IMAGE, createdDate = createdDate)

    @Test
    fun `local source folder that lists empty because it is gone is skipped not a success`() = runTest {
        seedSchedule(localResource(1L, "/does/not/exist"), localResource(2L, targetDir.absolutePath))
        stubFiles(emptyList())

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertTrue(result.isSkipped)
        assertEquals(ScheduledRunOutcome.SKIPPED, result.outcome)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `unreachable network source is skipped before any scan`() = runTest {
        val nas = createMediaResource(id = 1L, name = "NAS", type = ResourceType.SMB, path = "smb://nas/photos")
        seedSchedule(nas, localResource(2L, targetDir.absolutePath))
        resourceRepo.testConnectionResult = Result.failure(java.io.IOException("no route to host"))
        // getMediaFiles is deliberately not stubbed: a scan of the offline share would fail the test.

        val result = useCase(1L)

        assertTrue(result.isSkipped)
        assertEquals(0, result.filesProcessed)
        assertTrue(result.skippedReason!!.contains("Source 'NAS' is unreachable"))
        assertTrue(result.statusString.startsWith("SKIPPED"))
    }

    @Test
    fun `unreachable network target is skipped and transfers nothing`() = runTest {
        val pc = createMediaResource(id = 2L, name = "Home PC", type = ResourceType.SFTP, path = "sftp://pc/in")
        seedSchedule(localResource(1L, "/src"), pc, operationType = ScheduledOpType.MOVE)
        resourceRepo.testConnectionResult = Result.failure(java.io.IOException("timeout"))
        stubFiles(listOf(imageNamed("a.jpg")))

        val result = useCase(1L)

        assertTrue(result.isSkipped)
        assertTrue(result.skippedReason!!.contains("Target 'Home PC' is unreachable"))
        coVerify(exactly = 0) { fileOperationUseCase.execute(any()) }
    }

    @Test
    fun `since last reads the success marker and not the last attempt`() = runTest {
        // The attempt at 2000 failed after the success at 1000, so a file created at 1500 was never transferred.
        seedSchedule(
            localResource(1L, "/src"),
            localResource(2L, targetDir.absolutePath),
            timeFilter = TimeFilter.SINCE_LAST,
            window = WindowState(lastRunAt = 2_000L, lastRunStatus = "ERROR: unreachable", lastSuccessAt = 1_000L),
        )
        stubFiles(listOf(imageNamed("old.jpg", createdDate = 500L), imageNamed("missed.jpg", createdDate = 1_500L)))
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(1, result.filesProcessed)
    }

    @Test
    fun `since last keeps the marker of a row written before last success existed`() = runTest {
        seedSchedule(
            localResource(1L, tempFolder.newFolder("legacy").absolutePath),
            localResource(2L, targetDir.absolutePath),
            timeFilter = TimeFilter.SINCE_LAST,
            window = WindowState(lastRunAt = 2_000L, lastRunStatus = "OK", lastSuccessAt = null),
        )
        stubFiles(listOf(imageNamed("old.jpg", createdDate = 1_500L)))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(0, result.filesProcessed)
        coVerify(exactly = 0) { fileOperationUseCase.execute(any()) }
    }

    @Test
    fun `partial result with a failed file is an error not a success`() = runTest {
        seedSchedule(
            localResource(1L, "/src"),
            localResource(2L, targetDir.absolutePath),
            operationType = ScheduledOpType.MOVE,
        )
        stubFiles(listOf(imageNamed("a.jpg")))
        coEvery { fileOperationUseCase.execute(any()) } returns FileOperationResult.PartialSuccess(
            processedCount = 0,
            failedCount = 1,
            errors = listOf("copy verify failed"),
        )

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertEquals(ScheduledRunOutcome.ERROR, result.outcome)
        assertEquals(0, result.filesProcessed)
        assertTrue(result.errors.contains("copy verify failed"))
    }

    @Test
    fun `local copy target missing directory fails reachability`() = runTest {
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = 2L,
                    operationType = ScheduledOpType.COPY
                )
            )
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
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = 2L,
                    operationType = ScheduledOpType.COPY,
                    fileTypeMask = FileTypeFlags.IMAGES,
                    timeFilter = TimeFilter.ALL
                )
            )
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
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = 2L,
                    operationType = ScheduledOpType.COPY,
                    fileTypeMask = FileTypeFlags.IMAGES
                )
            )
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
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = 2L,
                    operationType = ScheduledOpType.MOVE,
                    fileTypeMask = FileTypeFlags.IMAGES
                )
            )
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
            fileOperationUseCase.execute(
                match<FileOperation.Move> {
                    it.sources.single().path.replace('\\', '/').endsWith("/src/a.jpg") &&
                        it.destination == targetDir &&
                        !it.overwrite
                }
            )
        }
        // S1204: ofType, not any<FileOperation.Delete>. The type argument on any<T> is erased, so
        // that matcher accepts the Move call above and turns this line into "execute was never
        // called" - which contradicts the exactly = 1 verification directly above it.
        coVerify(exactly = 0) { fileOperationUseCase.execute(ofType<FileOperation.Delete>()) }
    }

    @Test
    fun `delete operation needs no target and processes files`() = runTest {
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = null,
                    operationType = ScheduledOpType.DELETE,
                    fileTypeMask = FileTypeFlags.IMAGES
                )
            )
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
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = null,
                    operationType = ScheduledOpType.DELETE,
                    fileTypeMask = FileTypeFlags.IMAGES
                )
            )
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

    // -- S4173: ARCHIVE --------------------------------------------------------

    private fun archiveOperation(sourceType: ResourceType, sourcePath: String, targetType: ResourceType) {
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = 2L,
                    operationType = ScheduledOpType.ARCHIVE,
                    fileTypeMask = FileTypeFlags.IMAGES
                )
            )
        )
        resourceRepo.setResources(
            listOf(
                createMediaResource(id = 1L, name = "Camera", type = sourceType, path = sourcePath),
                createMediaResource(id = 2L, type = targetType, path = targetDir.absolutePath),
            )
        )
    }

    @Test
    fun `archive packs local files into one zip and keeps the sources`() = runTest {
        val srcDir = tempFolder.newFolder("src")
        val sources = listOf("a.jpg", "b.jpg").map { File(srcDir, it).apply { writeText(it) } }
        archiveOperation(ResourceType.LOCAL, srcDir.absolutePath, ResourceType.LOCAL)
        stubFiles(sources.map { createMediaFile(name = it.name, path = it.absolutePath, type = MediaType.IMAGE) })

        val result = useCase(1L)

        assertTrue(result.errors.toString(), result.isSuccess)
        assertEquals(2, result.filesProcessed)
        val zips = targetDir.listFiles { f -> f.name.endsWith(".zip") }.orEmpty()
        assertEquals(1, zips.size)
        assertTrue(zips.single().name.startsWith("Camera_"))
        assertTrue(sources.all { it.exists() })
        coVerify(exactly = 0) { fileOperationUseCase.execute(any()) }
        assertTrue(recorded(1L).isEmpty())
    }

    @Test
    fun `archive rejects a network source and creates no zip`() = runTest {
        archiveOperation(ResourceType.SMB, "smb://host/share", ResourceType.LOCAL)

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertEquals("Archive source must be a local resource", result.errors.single())
        assertTrue(targetDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `archive rejects a network target`() = runTest {
        archiveOperation(ResourceType.LOCAL, "/src", ResourceType.SMB)

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertEquals("Archive target must be a local folder", result.errors.single())
    }

    // -- S4232: unattended open over exchange resources -----------------------

    @Test
    fun `a refused protected folder PIN is a stable error, not a skip`() = runTest {
        singleImageOperation(ScheduledOpType.COPY, withTarget = true)
        // The scan seam throws where a PIN-protected exchange root refuses the stored PIN; the
        // executor must surface one readable line, not the raw exception text.
        every {
            getMediaFiles(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns flow { throw NetworkPinRefusedException() }

        val result = useCase(1L)

        assertFalse(result.isSuccess)
        assertFalse(result.isSkipped)
        assertEquals(ScheduledRunOutcome.ERROR, result.outcome)
        assertEquals(
            "Protected folder PIN was rejected - re-enter the PIN on the resource screen",
            result.errors.single(),
        )
    }

    @Test
    fun `scheduled copy passes the operation overwrite rule to the file operation`() = runTest {
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = 2L,
                    operationType = ScheduledOpType.COPY,
                    fileTypeMask = FileTypeFlags.IMAGES,
                    overwrite = true,
                )
            )
        )
        resourceRepo.setResources(
            listOf(
                createMediaResource(id = 1L, type = ResourceType.LOCAL, path = "/src"),
                createMediaResource(id = 2L, type = ResourceType.LOCAL, path = targetDir.absolutePath),
            )
        )
        stubFiles(listOf(createMediaFile(name = "a.jpg", path = "/src/a.jpg", type = MediaType.IMAGE)))
        coEvery { fileOperationUseCase.execute(any<FileOperation.Copy>()) } returns
            FileOperationResult.Success(1, FileOperation.Copy(emptyList(), targetDir, overwrite = true))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        // B23.3: the remote target's skip/replace/rename behaviour follows the operation's own rule,
        // so the executor must not second-guess the flag on its way to the file operation.
        coVerify(exactly = 1) {
            fileOperationUseCase.execute(
                match<FileOperation.Copy> {
                    it.sources.single().path.replace('\\', '/').endsWith("/src/a.jpg") &&
                        it.destination == targetDir &&
                        it.overwrite
                }
            )
        }
    }

    @Test
    fun `copy run cleans orphaned temp files in the target before copying`() = runTest {
        singleImageOperation(ScheduledOpType.COPY, withTarget = true)
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { cleanup(targetDir.absolutePath) }
        coVerify(exactly = 1) { fileOperationUseCase.execute(any()) }
    }

    @Test
    fun `delete run without target never cleans temp files`() = runTest {
        singleImageOperation(ScheduledOpType.DELETE, withTarget = false)
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        useCase(1L)

        coVerify(exactly = 0) { cleanup(any()) }
    }
}

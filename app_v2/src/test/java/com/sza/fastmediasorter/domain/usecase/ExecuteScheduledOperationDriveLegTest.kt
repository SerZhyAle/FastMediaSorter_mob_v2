package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.mutation.InMemoryMutationJournal
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledFileConditions
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.mutation.MutationRecorder
import com.sza.fastmediasorter.domain.path.PathNormalizer
import com.sza.fastmediasorter.domain.transfer.DriveProducerTarget
import com.sza.fastmediasorter.domain.usecase.transfer.FetchScheduledChangesViaDriveUseCase
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.createScheduledOperation
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import com.sza.fastmediasorter.testing.fakes.FakeScheduledOperationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * S4232: a scheduled copy whose source is another own device reached on no live path takes the record's
 * Google Drive path; the schedule itself carries no Drive setting. The scan seam is deliberately not
 * stubbed in any test here - an unreachable source must never be scanned.
 */
class ExecuteScheduledOperationDriveLegTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scheduledRepo = FakeScheduledOperationRepository()
    private val resourceRepo = FakeResourceRepository()
    private val fileOperationUseCase = mockk<FileOperationUseCase>()
    private val driveLeg = mockk<FetchScheduledChangesViaDriveUseCase>()

    private lateinit var useCase: ExecuteScheduledOperationUseCase
    private lateinit var targetDir: File

    private val pathNormalizer = object : PathNormalizer {
        override fun canonical(rawPath: String, resourceType: ResourceType): String = rawPath
    }

    private val producer = DriveProducerTarget(
        deviceId = "dev-2",
        deviceName = "Tablet",
        lastSeenMs = null,
        resourceId = "res-1",
        rootPath = "DCIM",
        pinProtected = false
    )

    @Before
    fun setup() {
        targetDir = tempFolder.newFolder("dest")
        useCase = ExecuteScheduledOperationUseCase(
            scheduledRepo,
            resourceRepo,
            mockk(),
            fileOperationUseCase,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            MutationRecorder(InMemoryMutationJournal(), pathNormalizer),
            ArchiveFilesUseCase(mockk(relaxed = true), mockk(relaxed = true)),
            mockk(relaxed = true),
            driveLeg,
            mockk(relaxed = true),
        )
        resourceRepo.testConnectionResult = Result.failure(IOException("no route to host"))
        coEvery { driveLeg.producerFor(any()) } returns producer
    }

    private fun exchangeSource(): MediaResource = createMediaResource(
        id = 1L,
        name = "Tablet DCIM",
        type = ResourceType.SFTP,
        path = "sftp://tablet:2222/DCIM"
    ).copy(exchangeResourceId = "res-1")

    private fun seed(
        operationType: ScheduledOpType = ScheduledOpType.COPY,
        timeFilter: TimeFilter = TimeFilter.ALL,
        conditions: ScheduledFileConditions = ScheduledFileConditions(),
    ) {
        val target = createMediaResource(id = 2L, type = ResourceType.LOCAL, path = targetDir.absolutePath)
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = 2L,
                    operationType = operationType,
                    fileTypeMask = FileTypeFlags.IMAGES,
                    timeFilter = timeFilter,
                ).copy(lastSuccessAt = MARKER, fileConditions = conditions)
            )
        )
        resourceRepo.setResources(listOf(exchangeSource(), target))
    }

    @Test
    fun `changes fetched via Google Drive move into the target under the operation filters`() = runTest {
        seed(timeFilter = TimeFilter.SINCE_LAST)
        val incoming = tempFolder.newFolder("incoming")
        val photo = File(incoming, "a.jpg").apply { writeText("x") }
        val notes = File(incoming, "notes.txt").apply { writeText("y") }
        coEvery { driveLeg(producer, MARKER) } returns
            FetchScheduledChangesViaDriveUseCase.Outcome.Fetched(listOf(photo.path, notes.path))
        coEvery { fileOperationUseCase.execute(any()) } returns
            FileOperationResult.Success(1, FileOperation.Move(emptyList(), targetDir, overwrite = false))

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        assertEquals(1, result.filesProcessed)
        // The images-only mask keeps the text file out, and no incoming copy outlives the run.
        coVerify(exactly = 1) {
            fileOperationUseCase.execute(FileOperation.Move(listOf(photo.absoluteFile), targetDir, overwrite = false))
        }
        assertFalse(photo.exists())
        assertFalse(notes.exists())
    }

    @Test
    fun `a Google Drive leg with no answer skips with both reasons`() = runTest {
        seed()
        coEvery { driveLeg(producer, null) } returns
            FetchScheduledChangesViaDriveUseCase.Outcome.Unavailable("via Google Drive: 'Tablet' did not answer")

        val result = useCase(1L)

        assertTrue(result.isSkipped)
        assertTrue(result.skippedReason!!.contains("Source 'Tablet DCIM' is unreachable"))
        assertTrue(result.skippedReason!!.contains("'Tablet' did not answer"))
        coVerify(exactly = 0) { fileOperationUseCase.execute(any()) }
    }

    @Test
    fun `a record without a Drive path skips on the live-path reason alone`() = runTest {
        seed()
        coEvery { driveLeg.producerFor(any()) } returns null

        val result = useCase(1L)

        assertTrue(result.isSkipped)
        assertFalse(result.skippedReason!!.contains("Google Drive"))
    }

    @Test
    fun `move never asks Google Drive because the source could not be removed`() = runTest {
        seed(operationType = ScheduledOpType.MOVE)

        val result = useCase(1L)

        assertTrue(result.isSkipped)
        coVerify(exactly = 0) { driveLeg.producerFor(any()) }
    }

    @Test
    fun `an age condition keeps the skip because fetched files carry their arrival time`() = runTest {
        seed(conditions = ScheduledFileConditions(minAgeHours = 2))

        val result = useCase(1L)

        assertTrue(result.isSkipped)
        coVerify(exactly = 0) { driveLeg.producerFor(any()) }
    }

    private companion object {
        const val MARKER = 5_000L
    }
}

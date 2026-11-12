package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.mutation.InMemoryMutationJournal
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunReason
import com.sza.fastmediasorter.domain.mutation.MutationRecorder
import com.sza.fastmediasorter.domain.path.PathNormalizer
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.createScheduledOperation
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import com.sza.fastmediasorter.testing.fakes.FakeScheduledOperationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** S4522: a FILL_FREE_SPACE schedule reaches the filler and never the file pipeline. */
class ExecuteScheduledFillDispatchTest {

    private val scheduledRepo = FakeScheduledOperationRepository()
    private val resourceRepo = FakeResourceRepository()
    private val getMediaFiles = mockk<GetMediaFilesUseCase>()
    private val fileOperationUseCase = mockk<FileOperationUseCase>()
    private val runFill = mockk<RunScheduledFillUseCase>()

    private lateinit var useCase: ExecuteScheduledOperationUseCase

    private val pathNormalizer = object : PathNormalizer {
        override fun canonical(rawPath: String, resourceType: ResourceType): String = rawPath
    }

    @Before
    fun setup() {
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
            mockk(),
            mockk(relaxed = true),
            runFill,
        )
        scheduledRepo.setOperations(
            listOf(
                createScheduledOperation(
                    id = 1L,
                    sourceResourceId = 1L,
                    targetResourceId = null,
                    operationType = ScheduledOpType.FILL_FREE_SPACE,
                )
            )
        )
    }

    @Test
    fun `fill run hands the source to the filler and lists no files`() = runTest {
        val source = createMediaResource(id = 1L, type = ResourceType.LOCAL)
        resourceRepo.setResources(listOf(source))
        val filled = ScheduledExecutionResult(1L, 0, emptyList())
        coEvery { runFill(1L, source) } returns filled

        val result = useCase(1L)

        assertEquals(filled, result)
        coVerify(exactly = 0) { fileOperationUseCase.execute(any()) }
    }

    @Test
    fun `fill run on a read-only source is skipped before the filler`() = runTest {
        resourceRepo.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL, isReadOnly = true)))

        val result = useCase(1L)

        assertEquals(ScheduledRunOutcome.SKIPPED, result.outcome)
        assertEquals(ScheduledRunReason.SOURCE_READ_ONLY, result.reason)
        coVerify(exactly = 0) { runFill(any(), any()) }
    }

    @Test
    fun `an exception out of the filler is reported, not thrown`() = runTest {
        resourceRepo.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL)))
        coEvery { runFill(any(), any()) } throws IllegalStateException("journal unreadable")

        val result = useCase(1L)

        assertEquals(ScheduledRunOutcome.ERROR, result.outcome)
        assertEquals(ScheduledRunReason.FILL_WRITE_FAILED, result.reason)
    }
}

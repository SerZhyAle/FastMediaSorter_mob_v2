package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.filler.FILLER_RESERVE_BYTES
import com.sza.fastmediasorter.domain.filler.FillerEngine
import com.sza.fastmediasorter.domain.filler.FillerPlace
import com.sza.fastmediasorter.domain.filler.FillerResult
import com.sza.fastmediasorter.domain.filler.FillerSession
import com.sza.fastmediasorter.domain.filler.FillerStorage
import com.sza.fastmediasorter.domain.filler.FillerTarget
import com.sza.fastmediasorter.domain.filler.FillerTargetResolver
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunReason
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S4522: how each storage filler verdict lands in the schedule's history. */
class RunScheduledFillUseCaseTest {

    private val engine = mockk<FillerEngine>()
    private val resolver = mockk<FillerTargetResolver>()
    private val storage = mockk<FillerStorage>()
    private val useCase = RunScheduledFillUseCase(engine, resolver, storage)

    private val source = createMediaResource(id = 1L, name = "Card", type = ResourceType.LOCAL, path = TREE)
    private val target = FillerTarget(TREE, "1234-5678")
    private val place = FillerPlace(target, "SD card", "Card")

    private suspend fun run(result: FillerResult): ScheduledExecutionResult {
        coEvery { resolver.place(TREE) } returns place
        coEvery { engine.pendingSessions() } returns emptyList()
        coEvery { engine.run(target, FILLER_RESERVE_BYTES, any()) } returns result
        return useCase(1L, source)
    }

    @Test
    fun `a finished and cleaned fill is a success`() = runTest {
        val result = run(FillerResult.Finished(writtenBytes = 10L, cleanupComplete = true))

        assertEquals(ScheduledRunOutcome.OK, result.outcome)
        assertNull(result.reason)
    }

    @Test
    fun `a fill whose cleanup did not finish is never a success`() = runTest {
        val finished = run(FillerResult.Finished(writtenBytes = 10L, cleanupComplete = false))
        val failed = run(FillerResult.Failed(cleanupComplete = false))

        assertEquals(ScheduledRunOutcome.ERROR, finished.outcome)
        assertEquals(ScheduledRunReason.FILL_CLEANUP_PENDING, finished.reason)
        assertEquals(ScheduledRunReason.FILL_CLEANUP_PENDING, failed.reason)
    }

    @Test
    fun `each refusal maps to its own reason`() = runTest {
        assertEquals(ScheduledRunReason.FILL_BUSY, run(FillerResult.Busy).reason)
        assertEquals(ScheduledRunOutcome.SKIPPED, run(FillerResult.Busy).outcome)
        assertEquals(ScheduledRunReason.FILL_NO_FREE_SPACE, run(FillerResult.InsufficientSpace).reason)
        assertEquals(ScheduledRunReason.FILL_STORAGE_UNAVAILABLE, run(FillerResult.InvalidTarget).reason)
        assertEquals(ScheduledRunOutcome.ERROR, run(FillerResult.InvalidTarget).outcome)
        assertEquals(ScheduledRunReason.FILL_MEASUREMENT_UNAVAILABLE, run(FillerResult.MeasurementUnavailable).reason)
        assertEquals(ScheduledRunReason.FILL_CLEANUP_PENDING, run(FillerResult.PendingCleanup).reason)
        assertEquals(ScheduledRunReason.FILL_WRITE_FAILED, run(FillerResult.Failed(cleanupComplete = true)).reason)
    }

    @Test
    fun `a folder off the local volumes is refused before the filler runs`() = runTest {
        coEvery { resolver.place(TREE) } returns null

        val result = useCase(1L, source)

        assertEquals(ScheduledRunReason.FILL_NOT_LOCAL, result.reason)
        coVerify(exactly = 0) { engine.run(any(), any(), any()) }
    }

    @Test
    fun `an earlier session is cleaned first, and one that stays blocks the run`() = runTest {
        val earlier = FillerSession("old", target)
        coEvery { resolver.place(TREE) } returns place
        coEvery { engine.pendingSessions() } returns listOf(earlier)
        coEvery { engine.retryCleanup(earlier) } returns false
        coEvery { engine.run(target, FILLER_RESERVE_BYTES, any()) } returns FillerResult.PendingCleanup

        val blocked = useCase(1L, source)

        assertEquals(ScheduledRunReason.FILL_CLEANUP_PENDING, blocked.reason)
        coVerify(exactly = 1) { engine.retryCleanup(earlier) }

        coEvery { engine.retryCleanup(earlier) } returns true
        coEvery { engine.run(target, FILLER_RESERVE_BYTES, any()) } returns
            FillerResult.Finished(writtenBytes = 1L, cleanupComplete = true)

        assertEquals(ScheduledRunOutcome.OK, useCase(1L, source).outcome)
    }

    @Test
    fun `only a writable local place is offered for saving`() = runTest {
        coEvery { resolver.place(TREE) } returns place
        coEvery { storage.validate(target) } returns false

        assertNull(useCase.usablePlace(TREE))

        coEvery { storage.validate(target) } returns true

        assertEquals(place, useCase.usablePlace(TREE))
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/1234-5678%3ACard"
    }
}

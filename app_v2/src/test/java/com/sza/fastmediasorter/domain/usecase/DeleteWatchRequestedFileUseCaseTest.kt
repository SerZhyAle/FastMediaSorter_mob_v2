package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.mutation.InMemoryMutationJournal
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearPhoneResourceDeleteOutcome
import com.sza.fastmediasorter.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.domain.mutation.Mutation
import com.sza.fastmediasorter.domain.mutation.MutationRecorder
import com.sza.fastmediasorter.domain.path.PathNormalizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S3359: the four answers this phone may give the watch, and the one of them that removes a file.
 *
 * Every case that is not [WearPhoneResourceDeleteOutcome.DELETED] is asserted twice - on the answer and
 * on the delete never being attempted - because the outcome alone cannot tell "kept" from "deleted and
 * then reported as kept", which is the failure the whole ticket is guarding against.
 */
class DeleteWatchRequestedFileUseCaseTest {

    private val openChannel: OpenPhoneResourceChannelUseCase = mockk()
    private val strategy: LocalOperationStrategy = mockk(relaxed = true)

    /** The real journal, so a test reads back the entry the reconciler would see (S3376). */
    private val journal = InMemoryMutationJournal()

    private val useCase = DeleteWatchRequestedFileUseCase(
        openChannel,
        strategy,
        MutationRecorder(
            journal,
            object : PathNormalizer {
                override fun canonical(rawPath: String, resourceType: ResourceType): String = rawPath
            }
        )
    )

    private val file = File("/storage/emulated/0/DCIM/Camera/clip.mp4")

    /** Every unapplied entry filed under resource 7, which is the id the tokens below encode. */
    private fun recorded(): List<Mutation> = journal.pendingFor(7L, 0L).map { it.mutation }

    private fun approve(sizeBytes: Long) {
        coEvery { openChannel(any(), any()) } returns PhoneResourceChannel.Approved(
            name = file.name,
            sizeBytes = sizeBytes,
            mediaType = MediaType.VIDEO,
            file = file
        )
    }

    @Test
    fun `an unresolvable token is answered not found and deletes nothing`() = runTest {
        coEvery { openChannel(any(), any()) } returns
            PhoneResourceChannel.Rejected(WearPhoneResourceResponseStatus.NOT_FOUND)

        val outcome = useCase(token = "7:DCIM/Camera/clip.mp4", expectedSizeBytes = 2_048L)

        assertEquals(WearPhoneResourceDeleteOutcome.NOT_FOUND, outcome)
        coVerify(exactly = 0) { strategy.deleteFile(any()) }
        assertTrue(recorded().isEmpty())
    }

    @Test
    fun `a file of another length is answered size mismatch and deletes nothing`() = runTest {
        approve(sizeBytes = 4_096L)

        val outcome = useCase(token = "7:DCIM/Camera/clip.mp4", expectedSizeBytes = 2_048L)

        assertEquals(WearPhoneResourceDeleteOutcome.SIZE_MISMATCH, outcome)
        coVerify(exactly = 0) { strategy.deleteFile(any()) }
        assertTrue(recorded().isEmpty())
    }

    @Test
    fun `a file needing the system dialog is answered copied only and deletes nothing`() = runTest {
        approve(sizeBytes = 2_048L)
        every { strategy.requiresDeleteConsent(file.absolutePath) } returns true

        val outcome = useCase(token = "7:DCIM/Camera/clip.mp4", expectedSizeBytes = 2_048L)

        assertEquals(WearPhoneResourceDeleteOutcome.COPIED_ONLY, outcome)
        coVerify(exactly = 0) { strategy.deleteFile(any()) }
        assertTrue(recorded().isEmpty())
    }

    @Test
    fun `a removable file of the expected length is deleted`() = runTest {
        approve(sizeBytes = 2_048L)
        every { strategy.requiresDeleteConsent(file.absolutePath) } returns false
        coEvery { strategy.deleteFile(file.absolutePath) } returns Result.success(Unit)

        val outcome = useCase(token = "7:DCIM/Camera/clip.mp4", expectedSizeBytes = 2_048L)

        assertEquals(WearPhoneResourceDeleteOutcome.DELETED, outcome)
        coVerify(exactly = 1) { strategy.deleteFile(file.absolutePath) }
    }

    @Test
    fun `a deleted file is recorded in the journal under the token's resource`() = runTest {
        approve(sizeBytes = 2_048L)
        every { strategy.requiresDeleteConsent(file.absolutePath) } returns false
        coEvery { strategy.deleteFile(file.absolutePath) } returns Result.success(Unit)

        useCase(token = "7:DCIM/Camera/clip.mp4", expectedSizeBytes = 2_048L)

        val delete = recorded().single() as Mutation.Delete
        assertEquals(7L, delete.resourceId)
        assertEquals(file.absolutePath, delete.canonicalPath)
    }

    @Test
    fun `a delete that fails is answered copied only rather than deleted`() = runTest {
        approve(sizeBytes = 2_048L)
        every { strategy.requiresDeleteConsent(file.absolutePath) } returns false
        coEvery { strategy.deleteFile(file.absolutePath) } returns
            Result.failure(IllegalStateException("read-only volume"))

        val outcome = useCase(token = "7:DCIM/Camera/clip.mp4", expectedSizeBytes = 2_048L)

        assertEquals(WearPhoneResourceDeleteOutcome.COPIED_ONLY, outcome)
        // The file is still on the phone, so an entry here would tell Browse to drop a row it must keep.
        assertTrue(recorded().isEmpty())
    }
}

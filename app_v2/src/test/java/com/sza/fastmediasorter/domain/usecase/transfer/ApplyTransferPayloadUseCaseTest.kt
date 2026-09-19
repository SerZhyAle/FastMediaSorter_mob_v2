package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.IncompatibleTransferFile
import com.sza.fastmediasorter.domain.model.transfer.PreviewedKindNotAppliedHere
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.model.transfer.TransferReport
import com.sza.fastmediasorter.domain.usecase.ApplyBackupPayloadUseCase
import com.sza.fastmediasorter.domain.usecase.BackupPayload
import com.sza.fastmediasorter.domain.usecase.streams.ImportPinnedStreamsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1565 strategic §11 criterion 5: bytes of another kind, bytes from a newer build and unparseable
 * bytes must each refuse before any applier runs, so local data survives a bad file untouched.
 */
class ApplyTransferPayloadUseCaseTest {

    private val applyBackupPayload = mockk<ApplyBackupPayloadUseCase>(relaxed = true)
    private val importPinnedStreams = mockk<ImportPinnedStreamsUseCase>(relaxed = true)

    private val useCase = ApplyTransferPayloadUseCase(applyBackupPayload, importPinnedStreams)

    @Test
    fun `bytes declaring another kind refuse and apply nothing`() = runTest {
        val foreign = """{"version":1,"kind":"SETTINGS","exportedAt":0,"entries":[]}"""

        val result = useCase(TransferDataKind.PINNED_STREAMS, foreign.toByteArray())

        assertTrue(result.exceptionOrNull() is IncompatibleTransferFile)
        coVerify(exactly = 0) { importPinnedStreams(any()) }
        coVerify(exactly = 0) { applyBackupPayload(any()) }
    }

    @Test
    fun `a version above the current one refuses and applies nothing`() = runTest {
        val tooNew = """{"version":${BackupPayload.CURRENT_VERSION + 1}}"""

        val result = useCase(TransferDataKind.SETTINGS, tooNew.toByteArray())

        assertTrue(result.exceptionOrNull() is IncompatibleTransferFile)
        coVerify(exactly = 0) { applyBackupPayload(any()) }
    }

    @Test
    fun `unparseable bytes refuse and apply nothing`() = runTest {
        val result = useCase(TransferDataKind.SETTINGS, "not json at all".toByteArray())

        assertTrue(result.exceptionOrNull() is IncompatibleTransferFile)
        coVerify(exactly = 0) { applyBackupPayload(any()) }
    }

    @Test
    fun `a well-formed settings payload reaches its own applier exactly once`() = runTest {
        coEvery { applyBackupPayload(any()) } returns ApplyBackupPayloadUseCase.RestoreSummary(
            settingsRestored = true,
            resourcesAdded = 2,
            resourcesUpdated = 1,
            resourcesNeedingAuth = 3,
            credentialsRestored = 0,
            favoritesAdded = 0,
            favoritesSkipped = 0,
            scheduledOpsAdded = 0,
            webSessionsRestored = 0
        )

        val result = useCase(
            TransferDataKind.SETTINGS,
            """{"version":${BackupPayload.CURRENT_VERSION}}""".toByteArray()
        )

        assertEquals(
            TransferReport(TransferDataKind.SETTINGS, created = 2, updated = 1, skipped = 3),
            result.getOrNull()
        )
        coVerify(exactly = 1) { applyBackupPayload(any()) }
        coVerify(exactly = 0) { importPinnedStreams(any()) }
    }

    @Test
    fun `a well-formed pinned-streams payload reaches its own applier exactly once`() = runTest {
        val report = TransferReport(TransferDataKind.PINNED_STREAMS, 1, 0, 0)
        coEvery { importPinnedStreams(any()) } returns Result.success(report)

        val bytes = """{"version":1,"kind":"PINNED_STREAMS","exportedAt":0,"entries":[]}"""

        val result = useCase(TransferDataKind.PINNED_STREAMS, bytes.toByteArray())

        assertEquals(report, result.getOrNull())
        coVerify(exactly = 1) { importPinnedStreams(any()) }
        coVerify(exactly = 0) { applyBackupPayload(any()) }
    }

    @Test
    fun `a previewed kind refuses here rather than applying silently`() = runTest {
        val result = useCase(TransferDataKind.FAVORITES, "{}".toByteArray())

        assertTrue(result.exceptionOrNull() is PreviewedKindNotAppliedHere)
        coVerify(exactly = 0) { applyBackupPayload(any()) }
    }
}

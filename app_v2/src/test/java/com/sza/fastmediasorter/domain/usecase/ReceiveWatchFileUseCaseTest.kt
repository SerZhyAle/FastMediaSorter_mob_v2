package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.local.LocalSink
import com.sza.fastmediasorter.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * S1861: the size ceiling on the phone half of the bridge (strategic spec 3.2).
 *
 * Both halves of the rule are covered - the declaration is refused before a sink is opened, and the
 * arriving bytes are counted anyway - because the declaration is written by the other device and a
 * ceiling that trusted it would be no ceiling at all.
 */
class ReceiveWatchFileUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val resourceRepository = mockk<ResourceRepository>(relaxed = true)
    private val classifier = mockk<LocalDestinationClassifier>(relaxed = true)
    private val writer = mockk<LocalDestinationWriter>(relaxed = true)

    private val useCase = ReceiveWatchFileUseCase(context, resourceRepository, classifier, writer)

    private fun sink(): LocalSink = mockk<LocalSink>(relaxed = true).also {
        every { it.outputStream } returns ByteArrayOutputStream()
        coEvery { it.commit() } returns Result.success("/sd/dest/a.jpg")
    }

    @Test
    fun `a declaration over the ceiling is refused without opening a destination`() = runTest {
        val result = useCase("huge.mp4", WEAR_FILE_TRANSFER_MAX_BYTES + 1, ByteArrayInputStream(ByteArray(4)))

        assertEquals(WearFileReceiveOutcome.REFUSED_TOO_LARGE, result.outcome)
        coVerify(exactly = 0) { writer.open(any(), any()) }
    }

    @Test
    fun `bytes outrunning the declared size abort the write and discard the partial file`() = runTest {
        val target = sink()
        coEvery { writer.open(any(), any()) } returns Result.success(target)
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()

        // Declares 8 bytes, sends 64: the understated declaration must not become a free pass.
        val result = useCase("liar.bin", 8L, ByteArrayInputStream(ByteArray(64)))

        assertEquals(WearFileReceiveOutcome.REFUSED_TOO_LARGE, result.outcome)
        coVerify { target.abort() }
        coVerify(exactly = 0) { target.commit() }
    }

    @Test
    fun `a file inside its declared size is committed`() = runTest {
        val target = sink()
        coEvery { writer.open(any(), any()) } returns Result.success(target)
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()

        val result = useCase("small.jpg", 64L, ByteArrayInputStream(ByteArray(64)))

        assertEquals(WearFileReceiveOutcome.SAVED, result.outcome)
        assertEquals("/sd/dest/a.jpg", result.savedPath)
        coVerify(exactly = 0) { target.abort() }
    }

    @Test
    fun `no writable destination is reported rather than silently dropping the file`() = runTest {
        coEvery { writer.open(any(), any()) } returns Result.failure(IllegalStateException("no sink"))
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()

        val result = useCase("a.jpg", 16L, ByteArrayInputStream(ByteArray(16)))

        assertEquals(WearFileReceiveOutcome.NO_DESTINATION, result.outcome)
    }
}

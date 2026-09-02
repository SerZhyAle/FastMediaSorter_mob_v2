package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.sza.fastmediasorter.data.local.staging.StagedKind
import com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.local.LocalSink
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
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
import java.nio.file.Files

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
    private val stagingDirectoryProvider = mockk<StagingDirectoryProvider>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    private val useCase = ReceiveWatchFileUseCase(
        context,
        resourceRepository,
        classifier,
        writer,
        stagingDirectoryProvider,
        workManager
    )

    private fun sink(): LocalSink = mockk<LocalSink>(relaxed = true).also {
        every { it.outputStream } returns ByteArrayOutputStream()
        coEvery { it.commit() } returns Result.success("/sd/dest/a.jpg")
    }

    private fun createResource(
        id: Long = 1L,
        name: String = "Test",
        path: String = "/sd/dest",
        type: ResourceType = ResourceType.LOCAL,
        isDestination: Boolean = true,
        isWritable: Boolean = true,
        destinationOrder: Int? = 1
    ): MediaResource = MediaResource(
        id = id,
        name = name,
        path = path,
        type = type,
        isDestination = isDestination,
        isWritable = isWritable,
        destinationOrder = destinationOrder
    )

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

    @Test
    fun `an SMB destination stages bytes and returns QUEUED_FOR_UPLOAD without invoking destinationWriter`() = runTest {
        val tempDir = Files.createTempDirectory("staging_test").toFile()
        tempDir.deleteOnExit()
        every { stagingDirectoryProvider.directoryFor(StagedKind.WATCH_RECEIVED) } returns tempDir

        val smbRes = createResource(id = 42L, name = "SMB", path = "smb://192.168.1.1/share", type = ResourceType.SMB)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(smbRes)

        val result = useCase("video.mp4", 64L, ByteArrayInputStream(ByteArray(64)))

        assertEquals(WearFileReceiveOutcome.QUEUED_FOR_UPLOAD, result.outcome)
        coVerify(exactly = 0) { writer.open(any(), any()) }
        coVerify { workManager.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun `an overrun on the staging path returns REFUSED_TOO_LARGE and leaves no file behind`() = runTest {
        val tempDir = Files.createTempDirectory("staging_overrun").toFile()
        tempDir.deleteOnExit()
        every { stagingDirectoryProvider.directoryFor(StagedKind.WATCH_RECEIVED) } returns tempDir

        val smbRes = createResource(id = 42L, name = "SMB", path = "smb://192.168.1.1/share", type = ResourceType.SMB)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(smbRes)

        val result = useCase("liar.bin", 8L, ByteArrayInputStream(ByteArray(64)))

        assertEquals(WearFileReceiveOutcome.REFUSED_TOO_LARGE, result.outcome)
        assertEquals(0, tempDir.listFiles()?.size ?: 0)
    }

    @Test
    fun `a LOCAL destination resolves to Local`() = runTest {
        val target = sink()
        coEvery { writer.open(any(), any()) } returns Result.success(target)
        val localRes = createResource(id = 10L, name = "Local", path = "/sdcard/Pictures", type = ResourceType.LOCAL)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(localRes)

        val result = useCase("photo.jpg", 64L, ByteArrayInputStream(ByteArray(64)))

        assertEquals(WearFileReceiveOutcome.SAVED, result.outcome)
        coVerify { classifier.classify("/sdcard/Pictures/photo.jpg") }
    }

    @Test
    fun `lower destinationOrder wins over higher one across differing types`() = runTest {
        val target = sink()
        coEvery { writer.open(any(), any()) } returns Result.success(target)
        val smbRes =
            createResource(id = 1L, name = "SMB", path = "smb://share", type = ResourceType.SMB, destinationOrder = 2)
        val localRes =
            createResource(
                id = 2L,
                name = "Local",
                path = "/sdcard/Downloads",
                type = ResourceType.LOCAL,
                destinationOrder = 1
            )
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(smbRes, localRes)

        val result = useCase("doc.pdf", 64L, ByteArrayInputStream(ByteArray(64)))

        assertEquals(WearFileReceiveOutcome.SAVED, result.outcome)
        coVerify { classifier.classify("/sdcard/Downloads/doc.pdf") }
    }

    @Test
    fun `non writable or non destination resource is skipped`() = runTest {
        val target = sink()
        coEvery { writer.open(any(), any()) } returns Result.success(target)
        val nonDest = createResource(id = 1L, isDestination = false, path = "/sdcard/Ignored1")
        val nonWritable = createResource(id = 2L, isWritable = false, path = "/sdcard/Ignored2")
        val validLocal = createResource(id = 3L, path = "/sdcard/Valid")
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(nonDest, nonWritable, validLocal)

        val result = useCase("file.txt", 64L, ByteArrayInputStream(ByteArray(64)))

        assertEquals(WearFileReceiveOutcome.SAVED, result.outcome)
        coVerify { classifier.classify("/sdcard/Valid/file.txt") }
    }

    @Test
    fun `content URI and virtual path are both rejected`() = runTest {
        val target = sink()
        coEvery { writer.open(any(), any()) } returns Result.success(target)
        val contentRes = createResource(id = 1L, path = "content://media/external/files/1")
        val virtualRes = createResource(id = 2L, path = "virtual://folder")
        val validLocal = createResource(id = 3L, path = "/sdcard/Real")
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(contentRes, virtualRes, validLocal)

        val result = useCase("data.bin", 64L, ByteArrayInputStream(ByteArray(64)))

        assertEquals(WearFileReceiveOutcome.SAVED, result.outcome)
        coVerify { classifier.classify("/sdcard/Real/data.bin") }
    }
}

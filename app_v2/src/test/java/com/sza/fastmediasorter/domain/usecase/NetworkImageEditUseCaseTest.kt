package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM coverage for [NetworkImageEditUseCase]: protocol validation, download/edit/upload flow, and
 * temp-file cleanup. The protocol handlers and image-transform use cases are mocked; the mocked SMB
 * download writes the temp file so the existence check passes.
 */
class NetworkImageEditUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val rotate = mockk<RotateImageUseCase>()
    private val flip = mockk<FlipImageUseCase>()
    private val filter = mockk<ApplyImageFilterUseCase>()
    private val adjust = mockk<AdjustImageUseCase>()
    private val smbHandler = mockk<SmbFileOperationHandler>()
    private val sftpHandler = mockk<SftpFileOperationHandler>()
    private val ftpHandler = mockk<FtpFileOperationHandler>()
    private lateinit var cacheDir: File
    private lateinit var useCase: NetworkImageEditUseCase

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        every { context.cacheDir } returns cacheDir
        useCase = NetworkImageEditUseCase(context, rotate, flip, filter, adjust, smbHandler, sftpHandler, ftpHandler)
    }

    /**
     * Makes the mocked SMB copy write the temp file only on the download leg (destination is the
     * local cache dir "network_image_edit"); the upload leg just reports success.
     */
    private fun stubSmbDownloadWritesTemp(fileName: String) {
        val opSlot = slot<FileOperation.Copy>()
        coEvery { smbHandler.executeCopy(capture(opSlot), any()) } answers {
            val dest = opSlot.captured.destination
            if (dest.name == "network_image_edit") {
                File(dest, fileName).writeText("img")
            }
            FileOperationResult.Success(1, opSlot.captured, listOf("ok"))
        }
    }

    @Test
    fun `non-network path fails fast`() = runTest {
        val result = useCase.execute("/local/photo.jpg", NetworkImageEditUseCase.EditOperation.Rotate(90f))

        assertTrue(result.isFailure)
    }

    @Test
    fun `full rotate flow downloads edits and uploads`() = runTest {
        stubSmbDownloadWritesTemp("photo.jpg")
        coEvery { rotate.execute(any(), any()) } returns Result.success(Unit)

        val result = useCase.execute("smb://host/share/photo.jpg", NetworkImageEditUseCase.EditOperation.Rotate(90f))

        assertTrue(result.isSuccess)
        coVerify { rotate.execute(any(), 90f) }
        // Temp file cleaned up afterwards.
        assertFalse(File(cacheDir, "network_image_edit/photo.jpg").exists())
    }

    @Test
    fun `download failure returns failure`() = runTest {
        coEvery { smbHandler.executeCopy(any(), any()) } returns FileOperationResult.Failure("nope")

        val result = useCase.execute("smb://host/share/photo.jpg", NetworkImageEditUseCase.EditOperation.Rotate(90f))

        assertTrue(result.isFailure)
    }

    @Test
    fun `edit failure returns failure`() = runTest {
        stubSmbDownloadWritesTemp("photo.jpg")
        coEvery { rotate.execute(any(), any()) } returns Result.failure(RuntimeException("decode"))

        val result = useCase.execute("smb://host/share/photo.jpg", NetworkImageEditUseCase.EditOperation.Rotate(90f))

        assertTrue(result.isFailure)
    }

    @Test
    fun `upload failure returns failure`() = runTest {
        val opSlot = slot<FileOperation.Copy>()
        // First call (download) writes temp + succeeds; second call (upload) fails.
        coEvery { smbHandler.executeCopy(capture(opSlot), any()) } answers {
            val dest = opSlot.captured.destination
            if (dest.name == "network_image_edit") {
                File(dest, "photo.jpg").writeText("img")
                FileOperationResult.Success(1, opSlot.captured, listOf("ok"))
            } else {
                FileOperationResult.Failure("upload failed")
            }
        }
        coEvery { rotate.execute(any(), any()) } returns Result.success(Unit)

        val result = useCase.execute("smb://host/share/photo.jpg", NetworkImageEditUseCase.EditOperation.Rotate(90f))

        assertTrue(result.isFailure)
    }
}

package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NetworkImageEditUseCaseTest {

    private lateinit var context: Context
    private val rotateImageUseCase: RotateImageUseCase = mockk(relaxed = true)
    private val flipImageUseCase: FlipImageUseCase = mockk(relaxed = true)
    private val smbHandler: SmbFileOperationHandler = mockk()
    private val sftpHandler: SftpFileOperationHandler = mockk()
    private val ftpHandler: FtpFileOperationHandler = mockk()

    private lateinit var useCase: NetworkImageEditUseCase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        useCase = NetworkImageEditUseCase(
            context = context,
            rotateImageUseCase = rotateImageUseCase,
            flipImageUseCase = flipImageUseCase,
            smbFileOperationHandler = smbHandler,
            sftpFileOperationHandler = sftpHandler,
            ftpFileOperationHandler = ftpHandler
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `execute rejects local paths`() = runTest {
        val result = useCase.execute(
            networkPath = "/sdcard/pic.jpg",
            operation = NetworkImageEditUseCase.EditOperation.Rotate(90f)
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `execute downloads edits and uploads over SMB`() = runTest {
        prepareCopyMock(smbHandler)
        coEvery { rotateImageUseCase.execute(any(), 90f) } returns Result.success(Unit)

        val result = useCase.execute(
            networkPath = "smb://server/share/photo.jpg",
            operation = NetworkImageEditUseCase.EditOperation.Rotate(90f)
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { smbHandler.executeCopy(any()) }
        coVerify { rotateImageUseCase.execute(any(), 90f) }
    }

    @Test
    fun `flipImage delegates to execute`() = runTest {
        prepareCopyMock(smbHandler)
        coEvery { flipImageUseCase.execute(any(), any()) } returns Result.success(Unit)

        val result = useCase.flipImage(
            networkPath = "smb://server/share/photo.jpg",
            direction = FlipImageUseCase.FlipDirection.HORIZONTAL
        )

        assertTrue(result.isSuccess)
        coVerify { flipImageUseCase.execute(any(), FlipImageUseCase.FlipDirection.HORIZONTAL) }
    }

    private fun prepareCopyMock(handler: SmbFileOperationHandler) {
        coEvery { handler.executeCopy(any()) } answers {
            val operation = firstArg<FileOperation>() as FileOperation.Copy
            if (operation.destination.path.contains("network_image_edit")) {
                ensureTempFile(operation.destination)
            }
            FileOperationResult.Success(operation.sources.size, operation)
        }
        coEvery { sftpHandler.executeCopy(any()) } returns FileOperationResult.Success(0, mockk(relaxed = true))
        coEvery { ftpHandler.executeCopy(any()) } returns FileOperationResult.Success(0, mockk(relaxed = true))
    }

    private fun ensureTempFile(directory: File) {
        if (!directory.exists()) directory.mkdirs()
        File(directory, "temp.jpg").writeText("data")
    }
}

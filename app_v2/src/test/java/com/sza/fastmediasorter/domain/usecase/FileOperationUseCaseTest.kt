package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * JVM coverage for [FileOperationUseCase] protocol routing (executeInternal dispatch) and the
 * operation-history accessors. Local filesystem operations route to internal Local* ops which call
 * MediaStoreNotifier (Android) - those branches are covered by the dedicated Local*OperationTests.
 * Here only network/cloud paths are driven, dispatching to mocked handlers.
 */
class FileOperationUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val smbHandler = mockk<SmbFileOperationHandler>()
    private val sftpHandler = mockk<SftpFileOperationHandler>()
    private val ftpHandler = mockk<FtpFileOperationHandler>()
    private val cloudHandler = mockk<CloudFileOperationHandler>()
    private val localStrategy = mockk<LocalOperationStrategy>(relaxed = true)
    private lateinit var useCase: FileOperationUseCase

    @Before
    fun setup() {
        useCase = FileOperationUseCase(context, smbHandler, sftpHandler, ftpHandler, cloudHandler, localStrategy, mockk(relaxed = true))
    }

    private fun netFile(path: String): File = object : File(path) {
        override fun getPath(): String = path
    }

    private fun copyOp(source: String, dest: String) = FileOperation.Copy(
        sources = listOf(netFile(source)), destination = netFile(dest), overwrite = true,
    )

    @Test
    fun `cloud path routes to cloud handler`() = runTest {
        val expected = FileOperationResult.Success(1, copyOp("cloud://x/a", "cloud://x/b"))
        coEvery { cloudHandler.executeCopy(any(), any()) } returns expected

        val result = useCase.execute(copyOp("cloud://x/a.jpg", "cloud://x/dest"))

        assertEquals(expected, result)
        coVerify { cloudHandler.executeCopy(any(), any()) }
    }

    @Test
    fun `smb path routes to smb handler`() = runTest {
        coEvery { smbHandler.executeCopy(any(), any()) } returns
            FileOperationResult.Success(1, copyOp("smb://h/s/a", "smb://h/s/b"))

        useCase.execute(copyOp("smb://h/s/a.jpg", "smb://h/s/dest"))

        coVerify { smbHandler.executeCopy(any(), any()) }
    }

    @Test
    fun `sftp path routes to sftp handler`() = runTest {
        coEvery { sftpHandler.executeCopy(any(), any()) } returns
            FileOperationResult.Success(1, copyOp("sftp://h/a", "sftp://h/b"))

        useCase.execute(copyOp("sftp://h/a.jpg", "sftp://h/dest"))

        coVerify { sftpHandler.executeCopy(any(), any()) }
    }

    @Test
    fun `ftp path routes to ftp handler`() = runTest {
        coEvery { ftpHandler.executeCopy(any(), any()) } returns
            FileOperationResult.Success(1, copyOp("ftp://h/a", "ftp://h/b"))

        useCase.execute(copyOp("ftp://h/a.jpg", "ftp://h/dest"))

        coVerify { ftpHandler.executeCopy(any(), any()) }
    }

    @Test
    fun `mixed smb and sftp prefers smb when destination is smb`() = runTest {
        coEvery { smbHandler.executeCopy(any(), any()) } returns
            FileOperationResult.Success(1, copyOp("sftp://h/a", "smb://h/s/b"))

        // source SFTP, destination SMB -> SMB handler wins.
        useCase.execute(copyOp("sftp://h/a.jpg", "smb://h/s/dest"))

        coVerify { smbHandler.executeCopy(any(), any()) }
        coVerify(exactly = 0) { sftpHandler.executeCopy(any(), any()) }
    }

    @Test
    fun `cloud delete routes to cloud handler`() = runTest {
        coEvery { cloudHandler.executeDelete(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        useCase.execute(FileOperation.Delete(listOf(netFile("cloud://x/a.jpg"))))

        coVerify { cloudHandler.executeDelete(any()) }
    }

    @Test
    fun `lastOperation is recorded and cleared`() = runTest {
        val expected = FileOperationResult.Success(1, copyOp("smb://h/s/a", "smb://h/s/b"))
        coEvery { smbHandler.executeCopy(any(), any()) } returns expected

        assertNull(useCase.getLastOperation())
        useCase.execute(copyOp("smb://h/s/a.jpg", "smb://h/s/dest"))
        assertTrue(useCase.canUndo())
        assertEquals(expected, useCase.getLastOperation()?.result)

        useCase.clearHistory()
        assertNull(useCase.getLastOperation())
        assertTrue(!useCase.canUndo())
    }
}

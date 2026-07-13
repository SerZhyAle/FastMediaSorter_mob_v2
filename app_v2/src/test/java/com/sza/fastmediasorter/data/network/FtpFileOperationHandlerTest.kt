package com.sza.fastmediasorter.data.network

import android.content.Context
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for [FtpFileOperationHandler.executeRename] and the internal FTP-path parser. The
 * injected [FtpClient] and credentials repository are mocked; rename routes through them directly
 * (not through a socket). Other operations inherit Android/strategy-bound logic and are out of
 * scope here.
 */
class FtpFileOperationHandlerTest {

    private val ftpClient = mockk<FtpClient>(relaxed = true)
    private val credentialsRepository = mockk<NetworkCredentialsRepository>()

    private fun handler(): FtpFileOperationHandler = FtpFileOperationHandler(
        context = mockk<Context>(relaxed = true),
        ftpClient = ftpClient,
        smbClient = mockk<SmbClient>(relaxed = true),
        sftpClient = mockk<SftpClient>(relaxed = true),
        credentialsRepository = credentialsRepository,
        endpointResolver = mockk(relaxed = true), // S1006: unused on the FTP path
        stagingDir = mockk<StagingDirectoryProvider>(relaxed = true),
        stagingRegistry = mockk<LocalStagingRegistry>(relaxed = true),
        destinationClassifier = mockk<LocalDestinationClassifier>(relaxed = true),
        destinationWriter = mockk<LocalDestinationWriter>(relaxed = true)
    )

    private fun credentials(): NetworkCredentialsEntity {
        val c = mockk<NetworkCredentialsEntity>(relaxed = true)
        every { c.username } returns "user"
        every { c.password } returns "pass"
        return c
    }

    @Test
    fun `executeRename rejects non-ftp path`() = runTest {
        val op = FileOperation.Rename(File("/local/file.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue(result is FileOperationResult.Failure)
        assertTrue((result as FileOperationResult.Failure).error.contains("Not an FTP file"))
    }

    @Test
    fun `executeRename fails when credentials are missing`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns null
        coEvery { credentialsRepository.getCredentialsByHost(any()) } returns null
        val op = FileOperation.Rename(File("ftp://host:21/dir/old.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue((result as FileOperationResult.Failure).error.contains("Invalid FTP path"))
    }

    @Test
    fun `executeRename fails when target name already exists`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        coEvery { ftpClient.existsWithNewConnection(any(), any(), any(), any(), any()) } returns Result.success(true)
        val op = FileOperation.Rename(File("ftp://host:21/dir/old.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue((result as FileOperationResult.Failure).error.contains("already exists"))
    }

    @Test
    fun `executeRename succeeds and returns destination path`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        coEvery { ftpClient.existsWithNewConnection(any(), any(), any(), any(), any()) } returns Result.success(false)
        coEvery { ftpClient.renameFileWithNewConnection(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        val op = FileOperation.Rename(File("ftp://host:21/dir/old.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue(result is FileOperationResult.Success)
        assertTrue((result as FileOperationResult.Success).copiedFilePaths.any { it.endsWith("new.txt") })
    }

    @Test
    fun `executeRename reports failure when rename call fails`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        coEvery { ftpClient.existsWithNewConnection(any(), any(), any(), any(), any()) } returns Result.success(false)
        coEvery { ftpClient.renameFileWithNewConnection(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("rename refused"))
        val op = FileOperation.Rename(File("ftp://host:21/dir/old.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue(result is FileOperationResult.Failure)
    }
}

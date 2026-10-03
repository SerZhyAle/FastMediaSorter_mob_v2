package com.sza.fastmediasorter.data.network

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * Unit tests for SFTP handler paths that need no socket.
 */
class SftpFileOperationHandlerTest {

    private val sftpClient = mockk<SftpClient>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val context = mockk<Context>(relaxed = true).also {
        every { it.contentResolver } returns contentResolver
    }
    private val credentialsRepository = mockk<NetworkCredentialsRepository>()
    // S1006: resolver echoes the requested endpoint so SFTP file ops keep their single-host behaviour.
    private val endpointResolver = mockk<SftpEndpointResolver>().also {
        coEvery { it.resolve(any(), any()) } answers { HostPort(firstArg(), secondArg()) }
    }

    private fun handler(): SftpFileOperationHandler = SftpFileOperationHandler(
        context = context,
        sftpClient = sftpClient,
        smbClient = mockk<SmbClient>(relaxed = true),
        ftpClient = mockk<FtpClient>(relaxed = true),
        credentialsRepository = credentialsRepository,
        endpointResolver = endpointResolver,
        stagingDir = mockk<StagingDirectoryProvider>(relaxed = true),
        stagingRegistry = mockk<LocalStagingRegistry>(relaxed = true),
        destinationClassifier = mockk<LocalDestinationClassifier>(relaxed = true),
        destinationWriter = mockk<LocalDestinationWriter>(relaxed = true)
    )

    private fun strategy(): SftpOperationStrategy = SftpOperationStrategy(
        context = context,
        sftpClient = sftpClient,
        credentialsRepository = credentialsRepository,
        endpointResolver = endpointResolver,
        stagingDir = mockk<StagingDirectoryProvider>(relaxed = true),
        stagingRegistry = mockk<LocalStagingRegistry>(relaxed = true),
        destinationClassifier = mockk<LocalDestinationClassifier>(relaxed = true),
        destinationWriter = mockk<LocalDestinationWriter>(relaxed = true)
    )

    private fun credentials(): NetworkCredentialsEntity {
        val c = mockk<NetworkCredentialsEntity>(relaxed = true)
        every { c.username } returns "user"
        every { c.password } returns "pass"
        every { c.decryptedSshPrivateKey } returns null
        return c
    }

    @Test
    fun `executeRename rejects non-sftp path`() = runTest {
        val op = FileOperation.Rename(File("/local/file.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue((result as FileOperationResult.Failure).error.contains("Not an SFTP file"))
    }

    @Test
    fun `executeRename fails when credentials are missing`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns null
        coEvery { credentialsRepository.getCredentialsByHost(any()) } returns null
        val op = FileOperation.Rename(File("sftp://host:22/dir/old.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue((result as FileOperationResult.Failure).error.contains("Invalid SFTP path"))
    }

    @Test
    fun `executeRename skips when target already exists`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        coEvery { sftpClient.exists(any(), any()) } returns Result.success(true)
        val op = FileOperation.Rename(File("sftp://host:22/dir/old.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue((result as FileOperationResult.Failure).error.contains("already exists"))
    }

    @Test
    fun `executeRename succeeds and returns new path`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        coEvery { sftpClient.exists(any(), any()) } returns Result.success(false)
        coEvery { sftpClient.renameFile(any(), any(), any()) } returns Result.success(Unit)
        val op = FileOperation.Rename(File("sftp://host:22/dir/old.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue(result is FileOperationResult.Success)
        assertTrue((result as FileOperationResult.Success).copiedFilePaths.any { it.endsWith("new.txt") })
    }

    @Test
    fun `executeRename maps rename failure to failure result`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        coEvery { sftpClient.exists(any(), any()) } returns Result.success(false)
        coEvery { sftpClient.renameFile(any(), any(), any()) } returns Result.failure(RuntimeException("denied"))
        val op = FileOperation.Rename(File("sftp://host:22/dir/old.txt"), "new.txt")
        val result = handler().executeRename(op)
        assertTrue(result is FileOperationResult.Failure)
    }

    @Test
    fun `SFTP strategy uploads a Pictures content URI`() = runTest {
        mockkStatic(Uri::class)
        val sourceUri = mockk<Uri>()
        every { Uri.parse(any()) } returns sourceUri
        every { sourceUri.scheme } returns "content"
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))
        coEvery { sftpClient.uploadFile(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        try {
            val result = strategy().copyFile(
                source = "content://media/external/images/media/7",
                destination = "sftp://host:22/inbox/7",
                overwrite = true,
                progressCallback = null
            )

            assertTrue(result.isSuccess)
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    private fun move(overwrite: Boolean) = FileOperation.Move(
        sources = listOf(File("sftp://host:22/a/x.jpg")),
        destination = File("sftp://host:22/b"),
        overwrite = overwrite
    )

    @Test
    fun `same-server move renames on the server without a download`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        coEvery { sftpClient.rename(any(), any(), any()) } returns Result.success(Unit)

        val result = handler().executeMove(move(overwrite = true), null)

        assertTrue(result is FileOperationResult.Success)
        coVerify(exactly = 1) { sftpClient.rename(any(), "/a/x.jpg", "/b/x.jpg") }
        coVerify(exactly = 0) { sftpClient.downloadFile(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refused rename falls back to copy and keeps the source when the copy fails`() = runTest {
        val cache = kotlin.io.path.createTempDirectory("s4035").toFile()
        every { context.cacheDir } returns cache
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        coEvery { sftpClient.rename(any(), any(), any()) } returns Result.failure(IOException("refused"))
        coEvery { sftpClient.stat(any(), any()) } returns Result.failure(IOException("gone"))
        coEvery { sftpClient.downloadFile(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IOException("short"))

        try {
            val result = handler().executeMove(move(overwrite = true), null)

            assertFalse(result is FileOperationResult.Success)
            coVerify(exactly = 1) { sftpClient.rename(any(), any(), any()) }
            coVerify(exactly = 0) { sftpClient.deleteFile(any(), any()) }
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun `same-server move skips an existing destination when overwrite is off`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
        coEvery { sftpClient.exists(any(), any()) } returns Result.success(true)

        val result = handler().executeMove(move(overwrite = false), null)

        assertTrue((result as FileOperationResult.Success).skippedCount == 1)
        coVerify(exactly = 0) { sftpClient.rename(any(), any(), any()) }
    }
}

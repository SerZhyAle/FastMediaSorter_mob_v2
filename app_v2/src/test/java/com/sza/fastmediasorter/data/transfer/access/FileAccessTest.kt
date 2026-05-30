package com.sza.fastmediasorter.data.transfer.access

import android.net.Uri
import com.sza.fastmediasorter.data.cloud.NetworkCredentialsResolver
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import org.apache.commons.net.ftp.FTPFile
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for the [com.sza.fastmediasorter.data.transfer.FileAccess] implementations: scheme
 * predicate (pure) plus the exists/delete branches that hinge on parse / client results. Robolectric
 * supplies android.net.Uri; protocol clients + credentials resolver are mocked (no sockets, no
 * CryptoHelper). LocalFileAccess is exercised via the file:// (non-content) branch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileAccessTest {

    // region supports

    @Test
    fun `supports matches each scheme`() {
        assertTrue(SmbFileAccess(mockk()).supports("smb"))
        assertFalse(SmbFileAccess(mockk()).supports("ftp"))

        assertTrue(FtpFileAccess(mockk(), mockk()).supports("ftp"))
        assertFalse(FtpFileAccess(mockk(), mockk()).supports("smb"))

        assertTrue(SftpFileAccess(mockk(), mockk()).supports("sftp"))
        assertFalse(SftpFileAccess(mockk(), mockk()).supports("smb"))

        val local = LocalFileAccess(mockk(relaxed = true))
        assertTrue(local.supports("file"))
        assertTrue(local.supports("content"))
        assertTrue(local.supports(null))
        assertFalse(local.supports("smb"))
    }

    // endregion

    // region Local (file:// branch)

    @Test
    fun `local exists and delete operate on file uri`() = runBlocking {
        val tmp = File.createTempFile("faTest", ".txt").apply { writeText("x") }
        val access = LocalFileAccess(mockk(relaxed = true))
        val uri = Uri.fromFile(tmp)

        assertTrue(access.exists(uri))
        assertTrue(access.delete(uri))
        assertFalse(tmp.exists())
    }

    @Test
    fun `local delete returns true when file already gone`() = runBlocking {
        val access = LocalFileAccess(mockk(relaxed = true))
        val uri = Uri.fromFile(File("/tmp/does-not-exist-${System.nanoTime()}"))
        assertTrue(access.delete(uri))
    }

    // endregion

    // region SMB

    @Test
    fun `smb exists true only on Success true`() = runBlocking {
        val client = mockk<SmbClient>()
        coEvery { client.exists(any(), any()) } returns SmbResult.Success(true)
        val access = SmbFileAccess(client)
        assertTrue(access.exists(Uri.parse("smb://server/share/file.jpg")))
    }

    @Test
    fun `smb exists false on Success false`() = runBlocking {
        val client = mockk<SmbClient>()
        coEvery { client.exists(any(), any()) } returns SmbResult.Success(false)
        assertFalse(SmbFileAccess(client).exists(Uri.parse("smb://server/share/file.jpg")))
    }

    @Test
    fun `smb exists false when path unparseable`() = runBlocking {
        val access = SmbFileAccess(mockk())
        assertFalse(access.exists(Uri.parse("smb://")))
    }

    @Test
    fun `smb delete true on Success`() = runBlocking {
        val client = mockk<SmbClient>()
        coEvery { client.deleteFile(any(), any()) } returns SmbResult.Success(Unit)
        assertTrue(SmbFileAccess(client).delete(Uri.parse("smb://server/share/file.jpg")))
    }

    // endregion

    // region FTP

    @Test
    fun `ftp exists false when no credentials`() = runBlocking {
        val resolver = mockk<NetworkCredentialsResolver>()
        coEvery { resolver.getCredentials(any()) } returns null
        val access = FtpFileAccess(mockk(), resolver)
        assertFalse(access.exists(Uri.parse("ftp://host:21/f.txt")))
    }

    @Test
    fun `ftp exists true when file present in listing`() = runBlocking {
        val resolver = mockk<NetworkCredentialsResolver>()
        coEvery { resolver.getCredentials(any()) } returns
            NetworkCredentialsResolver.NetworkCredentials("host", 21, "u", "p")
        val client = mockk<FtpClient>(relaxed = true)
        coEvery { client.connect(any(), any(), any(), any()) } returns Result.success(Unit)
        val ftpFile = FTPFile().apply { name = "f.txt" }
        coEvery { client.listFilesWithMetadata(any(), any()) } returns Result.success(listOf(ftpFile))

        val access = FtpFileAccess(client, resolver)
        assertTrue(access.exists(Uri.parse("ftp://host:21/dir/f.txt")))
    }

    @Test
    fun `ftp exists false when connect fails`() = runBlocking {
        val resolver = mockk<NetworkCredentialsResolver>()
        coEvery { resolver.getCredentials(any()) } returns
            NetworkCredentialsResolver.NetworkCredentials("host", 21, "u", "p")
        val client = mockk<FtpClient>()
        coEvery { client.connect(any(), any(), any(), any()) } returns Result.failure(RuntimeException("no"))

        assertFalse(FtpFileAccess(client, resolver).exists(Uri.parse("ftp://host:21/f.txt")))
    }

    // endregion

    // region SFTP

    @Test
    fun `sftp exists delegates to client result`() = runBlocking {
        val resolver = mockk<NetworkCredentialsResolver>()
        coEvery { resolver.getCredentials(any()) } returns
            NetworkCredentialsResolver.NetworkCredentials("host", 22, "u", "p")
        val client = mockk<SftpClient>()
        coEvery { client.exists(any(), any()) } returns Result.success(true)

        assertTrue(SftpFileAccess(client, resolver).exists(Uri.parse("sftp://host:22/f.txt")))
    }

    @Test
    fun `sftp delete true on success result`() = runBlocking {
        val resolver = mockk<NetworkCredentialsResolver>()
        coEvery { resolver.getCredentials(any()) } returns
            NetworkCredentialsResolver.NetworkCredentials("host", 22, "u", "p")
        val client = mockk<SftpClient>()
        coEvery { client.deleteFile(any(), any()) } returns Result.success(Unit)

        assertTrue(SftpFileAccess(client, resolver).delete(Uri.parse("sftp://host:22/f.txt")))
    }

    @Test
    fun `sftp exists false when no credentials`() = runBlocking {
        val resolver = mockk<NetworkCredentialsResolver>()
        coEvery { resolver.getCredentials(any()) } returns null
        assertFalse(SftpFileAccess(mockk(), resolver).exists(Uri.parse("sftp://host:22/f.txt")))
    }

    // endregion
}

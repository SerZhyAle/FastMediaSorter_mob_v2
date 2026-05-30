package com.sza.fastmediasorter.data.hash

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.network.SmbFileOperations
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Unit tests for the network [com.sza.fastmediasorter.domain.hash.FileHasher] implementations
 * (FTP/SFTP/SMB): real path parsing, credential lookup + anonymous fallback, stream digesting,
 * and error propagation. Protocol clients and the credentials entity are mocked - no sockets,
 * no CryptoHelper (entity getters stubbed directly).
 */
class NetworkFileHashersTest {

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun credEntity(
        username: String = "user",
        password: String = "pass",
        domain: String = "",
        sshKey: String? = null
    ): NetworkCredentialsEntity {
        val entity = mockk<NetworkCredentialsEntity>()
        every { entity.username } returns username
        every { entity.password } returns password
        every { entity.domain } returns domain
        every { entity.decryptedSshPrivateKey } returns sshKey
        return entity
    }

    // region FTP

    @Test
    fun `ftp hasher parses path looks up creds and digests stream`() = runBlocking {
        val payload = "ftp-bytes".toByteArray()
        val ftpClient = mockk<FtpClient>()
        val repo = mockk<NetworkCredentialsRepository>()
        coEvery { repo.getByCredentialId("cred-1") } returns credEntity("alice", "secret")

        val hostSlot = slot<String>()
        val userSlot = slot<String>()
        coEvery {
            ftpClient.openInputStream(capture(hostSlot), any(), capture(userSlot), any(), any())
        } returns Result.success(payload.inputStream())

        val hasher = FtpFileHasher(ftpClient, repo)
        val file = createMediaFile(path = "ftp://ftp.example.com:21/pub/file.bin")
        val resource = createMediaResource(credentialsId = "cred-1")

        val hash = hasher.computeHash(file, resource, maxBytes = -1L)
        assertEquals(md5(payload), hash)
        assertEquals("ftp.example.com", hostSlot.captured)
        assertEquals("alice", userSlot.captured)
    }

    @Test
    fun `ftp hasher uses anonymous when no credentialsId`() = runBlocking {
        val ftpClient = mockk<FtpClient>()
        val repo = mockk<NetworkCredentialsRepository>()
        val userSlot = slot<String>()
        coEvery {
            ftpClient.openInputStream(any(), any(), capture(userSlot), any(), any())
        } returns Result.success("x".toByteArray().inputStream())

        val hasher = FtpFileHasher(ftpClient, repo)
        val file = createMediaFile(path = "ftp://host/file.bin")
        val resource = createMediaResource(credentialsId = null)

        hasher.computeHash(file, resource, maxBytes = -1L)
        assertEquals("anonymous", userSlot.captured)
    }

    @Test
    fun `ftp hasher throws on unparseable path`() {
        val hasher = FtpFileHasher(mockk(), mockk())
        val ex = runCatching {
            runBlocking {
                hasher.computeHash(createMediaFile(path = "/not/ftp/path"), createMediaResource(), -1L)
            }
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `ftp hasher propagates openInputStream failure`() {
        val ftpClient = mockk<FtpClient>()
        val repo = mockk<NetworkCredentialsRepository>()
        coEvery { ftpClient.openInputStream(any(), any(), any(), any(), any()) } returns
            Result.failure(java.io.IOException("ftp down"))

        val hasher = FtpFileHasher(ftpClient, repo)
        val ex = runCatching {
            runBlocking {
                hasher.computeHash(createMediaFile(path = "ftp://host/f.bin"), createMediaResource(), -1L)
            }
        }.exceptionOrNull()
        assertTrue(ex is java.io.IOException)
    }

    // endregion

    // region SFTP

    @Test
    fun `sftp hasher builds connection info with private key and digests`() = runBlocking {
        val payload = "sftp-bytes".toByteArray()
        val sftpClient = mockk<SftpClient>()
        val repo = mockk<NetworkCredentialsRepository>()
        coEvery { repo.getByCredentialId("c") } returns credEntity("bob", "pw", sshKey = "PRIVATE-KEY")

        val infoSlot = slot<SftpClient.SftpConnectionInfo>()
        coEvery { sftpClient.openInputStream(capture(infoSlot), any()) } returns
            Result.success(payload.inputStream())

        val hasher = SftpFileHasher(sftpClient, repo)
        val file = createMediaFile(path = "sftp://10.0.0.5:2222/home/file.dat")
        val resource = createMediaResource(credentialsId = "c")

        val hash = hasher.computeHash(file, resource, maxBytes = -1L)
        assertEquals(md5(payload), hash)
        assertEquals("10.0.0.5", infoSlot.captured.host)
        assertEquals("bob", infoSlot.captured.username)
        assertEquals("PRIVATE-KEY", infoSlot.captured.privateKey)
    }

    @Test
    fun `sftp hasher throws on unparseable path`() {
        val hasher = SftpFileHasher(mockk(), mockk())
        val ex = runCatching {
            runBlocking {
                hasher.computeHash(createMediaFile(path = "/local/x"), createMediaResource(), -1L)
            }
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `sftp hasher propagates failure`() {
        val sftpClient = mockk<SftpClient>()
        coEvery { sftpClient.openInputStream(any(), any()) } returns
            Result.failure(java.io.IOException("sftp down"))
        val hasher = SftpFileHasher(sftpClient, mockk(relaxed = true))
        val ex = runCatching {
            runBlocking {
                hasher.computeHash(createMediaFile(path = "sftp://h/f"), createMediaResource(credentialsId = null), -1L)
            }
        }.exceptionOrNull()
        assertTrue(ex is java.io.IOException)
    }

    // endregion

    // region SMB

    @Test
    fun `smb hasher digests on SmbResult Success`() = runBlocking {
        val payload = "smb-bytes".toByteArray()
        val smbOps = mockk<SmbFileOperations>()
        val repo = mockk<NetworkCredentialsRepository>()
        coEvery { repo.getByCredentialId("c") } returns credEntity("smbuser", "smbpass", domain = "WORKGROUP")
        coEvery { smbOps.openInputStream(any(), any()) } returns SmbResult.Success(payload.inputStream())

        val hasher = SmbFileHasher(smbOps, repo)
        val file = createMediaFile(path = "smb://192.168.1.10/share/folder/file.jpg")
        val resource = createMediaResource(credentialsId = "c")

        val hash = hasher.computeHash(file, resource, maxBytes = -1L)
        assertEquals(md5(payload), hash)
        coVerify { smbOps.openInputStream(any(), any()) }
    }

    @Test
    fun `smb hasher throws on unparseable path`() {
        val hasher = SmbFileHasher(mockk(), mockk(relaxed = true))
        val ex = runCatching {
            runBlocking {
                hasher.computeHash(createMediaFile(path = "/local/path"), createMediaResource(credentialsId = null), -1L)
            }
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `smb hasher propagates SmbResult Error`() {
        val smbOps = mockk<SmbFileOperations>()
        coEvery { smbOps.openInputStream(any(), any()) } returns SmbResult.Error("smb failed")
        val hasher = SmbFileHasher(smbOps, mockk(relaxed = true))
        val ex = runCatching {
            runBlocking {
                hasher.computeHash(
                    createMediaFile(path = "smb://srv/share/f.jpg"),
                    createMediaResource(credentialsId = null),
                    -1L
                )
            }
        }.exceptionOrNull()
        assertEquals("smb failed", ex?.message)
    }

    // endregion
}

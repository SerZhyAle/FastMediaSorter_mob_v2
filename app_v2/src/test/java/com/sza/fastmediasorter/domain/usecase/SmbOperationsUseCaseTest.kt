package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbFileInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM coverage for [SmbOperationsUseCase]. Covers connection-test result mapping, share-name
 * parsing, listing/result mapping (including private detectMediaType via [SmbOperationsUseCase.listFiles]),
 * credential lookups, and trash folder discovery/cleanup. Credential-save methods are not covered:
 * they call CryptoHelper.encrypt (Android keystore) on every path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmbOperationsUseCaseTest {

    private val smbClient = mockk<SmbClient>()
    private val sftpClient = mockk<SftpClient>(relaxed = true)
    private val ftpClient = mockk<FtpClient>(relaxed = true)
    private val credentialsRepository = mockk<NetworkCredentialsRepository>(relaxed = true)
    // S1006: relaxed - these tests exercise SMB/saveSftpCredentials, not the resolve() path.
    private val endpointResolver = mockk<SftpEndpointResolver>(relaxed = true)
    private lateinit var useCase: SmbOperationsUseCase

    @Before
    fun setup() {
        useCase = SmbOperationsUseCase(
            smbClient, sftpClient, ftpClient, credentialsRepository, endpointResolver,
            UnconfinedTestDispatcher(), mockk(relaxed = true),
        )
    }

    private fun smbCred() = NetworkCredentialsEntity(
        id = 1, credentialId = "c1", type = "SMB", server = "host", port = 445,
        username = "u", encryptedPassword = "", domain = "", shareName = "share", sshPrivateKey = null,
        accountId = "",
    )

    @Test
    fun `testConnection maps SMB success to Result success`() = runTest {
        coEvery { smbClient.testConnection(any(), any()) } returns SmbResult.Success("ok")

        val result = useCase.testConnection("host", "share/sub", "u", "p")

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun `testConnection splits share and subpath`() = runTest {
        val captured = mutableListOf<SmbConnectionInfo>()
        coEvery { smbClient.testConnection(capture(captured), any()) } returns SmbResult.Success("ok")

        useCase.testConnection("host", "myshare\\folder\\deep", "u", "p")

        // Backslashes normalized; only the first segment is the share name.
        assertEquals("myshare", captured.first().shareName)
    }

    @Test
    fun `testConnection maps error to failure preserving exception`() = runTest {
        val boom = IllegalStateException("denied")
        coEvery { smbClient.testConnection(any(), any()) } returns SmbResult.Error("denied", boom)

        val result = useCase.testConnection("host", "share", "u", "p")

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `listShares maps success`() = runTest {
        coEvery { smbClient.listShares(any(), any(), any(), any(), any()) } returns
            SmbResult.Success(listOf("A", "B"))

        val result = useCase.listShares("host")

        assertEquals(listOf("A", "B"), result.getOrNull())
    }

    @Test
    fun `scanMediaFiles maps SmbFileInfo and detects media types from extension`() = runTest {
        coEvery { smbClient.scanMediaFiles(any(), any(), any(), any(), any(), any()) } returns
            SmbResult.Success(
                listOf(
                    SmbFileInfo("photo.jpg", "/photo.jpg", false, 10, 0),
                    SmbFileInfo("clip.mp4", "/clip.mp4", false, 20, 0),
                    SmbFileInfo("song.mp3", "/song.mp3", false, 30, 0),
                    SmbFileInfo("anim.gif", "/anim.gif", false, 40, 0),
                ),
            )

        val files = useCase.scanMediaFiles("host", "share", "", "u", "p").getOrThrow()

        assertEquals(MediaType.IMAGE, files[0].type)
        assertEquals(MediaType.VIDEO, files[1].type)
        assertEquals(MediaType.AUDIO, files[2].type)
        assertEquals(MediaType.GIF, files[3].type)
    }

    @Test
    fun `listFiles filters out directories`() = runTest {
        coEvery { credentialsRepository.getByCredentialId("c1") } returns smbCred()
        coEvery { smbClient.listFiles(any(), any()) } returns SmbResult.Success(
            listOf(
                SmbFileInfo("dir", "/dir", true, 0, 0),
                SmbFileInfo("a.png", "/a.png", false, 5, 0),
            ),
        )
        val resource = com.sza.fastmediasorter.testing.createMediaResource(
            type = ResourceType.SMB, credentialsId = "c1",
        )

        val files = useCase.listFiles(resource).getOrThrow()

        assertEquals(1, files.size)
        assertEquals("a.png", files.first().name)
    }

    @Test
    fun `listFiles fails when resource has no credentials`() = runTest {
        val resource = com.sza.fastmediasorter.testing.createMediaResource(
            type = ResourceType.SMB, credentialsId = null,
        )

        val result = useCase.listFiles(resource)

        assertTrue(result.isFailure)
    }

    @Test
    fun `getConnectionInfo returns info for SMB credential`() = runTest {
        coEvery { credentialsRepository.getByCredentialId("c1") } returns smbCred()

        val info = useCase.getConnectionInfo("c1").getOrThrow()

        assertEquals("host", info.server)
        assertEquals("share", info.shareName)
    }

    @Test
    fun `getConnectionInfo fails for non-SMB credential type`() = runTest {
        coEvery { credentialsRepository.getByCredentialId("c1") } returns smbCred().copy(type = "FTP")

        assertTrue(useCase.getConnectionInfo("c1").isFailure)
    }

    @Test
    fun `getConnectionInfo fails when credential missing`() = runTest {
        coEvery { credentialsRepository.getByCredentialId("c1") } returns null

        assertTrue(useCase.getConnectionInfo("c1").isFailure)
    }

    @Test
    fun `checkTrashFolders detects SMB trash dirs`() = runTest {
        coEvery { credentialsRepository.getByCredentialId("c1") } returns smbCred()
        coEvery { smbClient.listFiles(any(), any()) } returns SmbResult.Success(
            listOf(
                SmbFileInfo(".trash_user", "/.trash_user", true, 0, 0),
                SmbFileInfo("photo.jpg", "/photo.jpg", false, 1, 0),
            ),
        )

        val (hasTrash, names) = useCase.checkTrashFolders(
            ResourceType.SMB, "c1", "smb://host/share/sub",
        ).getOrThrow()

        assertTrue(hasTrash)
        assertEquals(listOf(".trash_user"), names)
    }

    @Test
    fun `checkTrashFolders returns false when none present`() = runTest {
        coEvery { credentialsRepository.getByCredentialId("c1") } returns smbCred()
        coEvery { smbClient.listFiles(any(), any()) } returns SmbResult.Success(
            listOf(SmbFileInfo("photo.jpg", "/photo.jpg", false, 1, 0)),
        )

        val (hasTrash, names) = useCase.checkTrashFolders(
            ResourceType.SMB, "c1", "smb://host/share",
        ).getOrThrow()

        assertFalse(hasTrash)
        assertTrue(names.isEmpty())
    }

    @Test
    fun `cleanupTrash deletes discovered SMB trash folders`() = runTest {
        coEvery { credentialsRepository.getByCredentialId("c1") } returns smbCred()
        coEvery { smbClient.listFiles(any(), any()) } returns SmbResult.Success(
            listOf(SmbFileInfo(".trash_a", "/.trash_a", true, 0, 0)),
        )
        coEvery { smbClient.deleteDirectory(any(), any()) } returns SmbResult.Success(Unit)

        val deleted = useCase.cleanupTrash(ResourceType.SMB, "c1", "smb://host/share").getOrThrow()

        assertEquals(1, deleted)
    }

    @Test
    fun `cleanupTrash short-circuits when no trash`() = runTest {
        coEvery { credentialsRepository.getByCredentialId("c1") } returns smbCred()
        coEvery { smbClient.listFiles(any(), any()) } returns SmbResult.Success(emptyList())

        val deleted = useCase.cleanupTrash(ResourceType.SMB, "c1", "smb://host/share").getOrThrow()

        assertEquals(0, deleted)
    }

    @Test
    fun `clearAllConnectionPools delegates to all clients`() = runTest {
        coEvery { smbClient.clearConnectionPool() } returns Unit

        useCase.clearAllConnectionPools()

        coVerify { smbClient.clearConnectionPool() }
        coVerify { sftpClient.disconnectAll() }
        coVerify { ftpClient.disconnect() }
    }

    private fun sftpKeyCred() = NetworkCredentialsEntity(
        id = 2, credentialId = "sftp1", type = "SFTP", server = "sftp.host", port = 22,
        username = "u", encryptedPassword = "enc-passphrase", domain = "", shareName = null,
        sshPrivateKey = "existing-key", accountId = "",
    )

    // S0987: the two non-destructive merge branches (preserve key on null, preserve password on blank)
    // never reach CryptoHelper.encrypt, so unlike the other credential-save paths they are JVM-testable.

    @Test
    fun `saveSftpCredentials preserves existing SSH key and passphrase when privateKey null (S0987)`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort("SFTP", "sftp.host", 22) } returns sftpKeyCred()
        val captured = mutableListOf<NetworkCredentialsEntity>()
        coEvery { credentialsRepository.update(capture(captured)) } returns Unit

        val result = useCase.saveSftpCredentials("sftp.host", 22, "u", password = "imported-pw", privateKey = null)

        assertTrue(result.isSuccess)
        // Key must not be nulled; its passphrase (encryptedPassword) preserved, incoming password dropped.
        assertEquals("existing-key", captured.first().sshPrivateKey)
        assertEquals("enc-passphrase", captured.first().encryptedPassword)
    }

    @Test
    fun `saveSftpCredentials preserves stored password when incoming password blank (S0987)`() = runTest {
        val pwCred = sftpKeyCred().copy(sshPrivateKey = null, encryptedPassword = "enc-pw")
        coEvery { credentialsRepository.getByTypeServerAndPort("SFTP", "sftp.host", 22) } returns pwCred
        val captured = mutableListOf<NetworkCredentialsEntity>()
        coEvery { credentialsRepository.update(capture(captured)) } returns Unit

        val result = useCase.saveSftpCredentials("sftp.host", 22, "u", password = "", privateKey = null)

        assertTrue(result.isSuccess)
        assertEquals("enc-pw", captured.first().encryptedPassword)
        assertEquals(null, captured.first().sshPrivateKey)
    }
}

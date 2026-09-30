package com.sza.fastmediasorter.data.remote.ftp

import android.content.Context
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.apache.commons.net.ftp.FTPFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class FtpMediaScannerTest {

    private val ftpClient: FtpClient = mockk(relaxed = true)
    private val credentialsRepository: NetworkCredentialsRepository = mockk()
    private val context: Context = mockk(relaxed = true)

    private val scanner = FtpMediaScanner(
        ftpClient = ftpClient,
        credentialsRepository = credentialsRepository,
        context = context,
    )

    @Before
    fun setUp() {
        // Grant local-network permission so scanner proceeds to FTP calls.
        mockkObject(PermissionHelper)
        every { PermissionHelper.hasLocalNetworkPermission(context) } returns true
    }

    @Test
    fun `scanFolderPaged should return first page and hasMore when overflow exists`() = runTest {
        val credentials = mockk<NetworkCredentialsEntity>(relaxed = true)
        every { credentials.username } returns "user"
        every { credentials.password } returns "pass"

        val file1 = ftpFile(name = "a.jpg", size = 100)
        val file2 = ftpFile(name = "b.jpg", size = 200)
        val file3 = ftpFile(name = "c.jpg", size = 300)

        coEvery { credentialsRepository.getByCredentialId("cred-1") } returns credentials
        coEvery { ftpClient.connect("host", 21, "user", "pass") } returns Result.success(Unit)
        coEvery {
            ftpClient.listFilesWithMetadataPaged(
                remotePath = "folder",
                offset = 0,
                limit = any(),
                recursive = false
            )
        } returns Result.success(listOf(file1, file2, file3))

        val result = scanner.scanFolderPaged(
            path = "ftp://host/folder",
            supportedTypes = setOf(MediaType.IMAGE),
            sizeFilter = null,
            offset = 0,
            limit = 2,
            credentialsId = "cred-1",
            scanSubdirectories = false,
            showHiddenFiles = false
        )

        assertEquals(2, result.files.size)
        assertEquals("a.jpg", result.files[0].name)
        assertEquals("b.jpg", result.files[1].name)
        assertTrue(result.hasMore)

        coVerify(exactly = 1) { ftpClient.disconnect() }
    }

    @Test
    fun `scanFolderPaged should respect filtered offset`() = runTest {
        val credentials = mockk<NetworkCredentialsEntity>(relaxed = true)
        every { credentials.username } returns "user"
        every { credentials.password } returns "pass"

        val file1 = ftpFile(name = "a.jpg", size = 100)
        val file2 = ftpFile(name = "b.jpg", size = 200)
        val file3 = ftpFile(name = "c.jpg", size = 300)

        coEvery { credentialsRepository.getByCredentialId("cred-2") } returns credentials
        coEvery { ftpClient.connect("host", 21, "user", "pass") } returns Result.success(Unit)
        coEvery {
            ftpClient.listFilesWithMetadata("folder", recursive = false)
        } returns Result.success(listOf(file1, file2, file3))

        val result = pageOf(offset = 1, limit = 1, credentialsId = "cred-2")

        assertEquals(1, result.files.size)
        assertEquals("b.jpg", result.files.first().name)
        assertTrue(result.hasMore)

        coVerify(exactly = 1) { ftpClient.disconnect() }
    }

    @Test
    fun `later pages are sliced from one listing instead of re-walking the folder`() = runTest {
        val credentials = mockk<NetworkCredentialsEntity>(relaxed = true)
        every { credentials.username } returns "user"
        every { credentials.password } returns "pass"

        coEvery { credentialsRepository.getByCredentialId("cred-3") } returns credentials
        coEvery { ftpClient.connect("host", 21, "user", "pass") } returns Result.success(Unit)
        coEvery {
            ftpClient.listFilesWithMetadata("folder", recursive = false)
        } returns Result.success(listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg").map { ftpFile(it, 100) })

        val second = pageOf(offset = 2, limit = 2, credentialsId = "cred-3")
        val third = pageOf(offset = 4, limit = 2, credentialsId = "cred-3")

        assertEquals(listOf("c.jpg", "d.jpg"), second.files.map { it.name })
        assertTrue(second.hasMore)
        assertEquals(listOf("e.jpg"), third.files.map { it.name })
        assertFalse(third.hasMore)
        coVerify(exactly = 1) { ftpClient.listFilesWithMetadata("folder", recursive = false) }
        coVerify(exactly = 0) { ftpClient.listFilesWithMetadataPaged(any(), any(), any(), any()) }
    }

    private suspend fun pageOf(offset: Int, limit: Int, credentialsId: String) = scanner.scanFolderPaged(
        path = "ftp://host/folder",
        supportedTypes = setOf(MediaType.IMAGE),
        sizeFilter = null,
        offset = offset,
        limit = limit,
        credentialsId = credentialsId,
        scanSubdirectories = false,
        showHiddenFiles = false
    )

    private fun ftpFile(name: String, size: Long): FTPFile {
        return FTPFile().apply {
            this.name = name
            this.size = size
            this.type = FTPFile.FILE_TYPE
            this.timestamp = Calendar.getInstance()
        }
    }
}

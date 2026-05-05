package com.sza.fastmediasorter.data.remote.ftp

import android.content.Context
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.commons.net.ftp.FTPFile
import org.junit.Assert.assertEquals
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
            offset = 1,
            limit = 1,
            credentialsId = "cred-2",
            scanSubdirectories = false,
            showHiddenFiles = false
        )

        assertEquals(1, result.files.size)
        assertEquals("b.jpg", result.files.first().name)
        assertTrue(result.hasMore)

        coVerify(exactly = 1) { ftpClient.disconnect() }
    }

    private fun ftpFile(name: String, size: Long): FTPFile {
        return FTPFile().apply {
            this.name = name
            this.size = size
            this.type = FTPFile.FILE_TYPE
            this.timestamp = Calendar.getInstance()
        }
    }
}

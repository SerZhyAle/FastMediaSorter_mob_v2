package com.sza.fastmediasorter.data.remote.sftp

import android.content.Context
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [SftpMediaScanner.scanFolder]. [PermissionHelper] is object-mocked to grant the
 * local-network permission; [SftpClient.listFiles] is mocked to return listing entries (no socket).
 * Covers permission gate, invalid-path empty return, media-type filtering, hidden/dir/trash skips,
 * size filtering, all-files TEXT fallback, and list-failure propagation.
 */
class SftpMediaScannerTest {

    private val sftpClient = mockk<SftpClient>(relaxed = true)
    private val credentialsRepository = mockk<NetworkCredentialsRepository>()
    private val context = mockk<Context>(relaxed = true)
    // S1006: resolver stubbed to echo the requested endpoint so scanning keeps its single-host behaviour.
    private val endpointResolver = mockk<SftpEndpointResolver>()

    private lateinit var scanner: SftpMediaScanner

    private val imageTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.GIF)
    private val allTypes = MediaType.values().toSet()

    @Before
    fun setUp() {
        mockkObject(PermissionHelper)
        every { PermissionHelper.hasLocalNetworkPermission(any()) } returns true
        coEvery { endpointResolver.resolve(any(), any()) } answers { HostPort(firstArg(), secondArg()) }
        scanner = SftpMediaScanner(sftpClient, credentialsRepository, endpointResolver, context)
        coEvery { credentialsRepository.getByCredentialId(any()) } returns credentials()
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns credentials()
    }

    @After
    fun tearDown() {
        unmockkObject(PermissionHelper)
    }

    private fun credentials(): NetworkCredentialsEntity {
        val c = mockk<NetworkCredentialsEntity>(relaxed = true)
        every { c.username } returns "user"
        every { c.password } returns "pass"
        every { c.decryptedSshPrivateKey } returns null
        return c
    }

    private fun listing(path: String, size: Long = 100L, isDir: Boolean = false) =
        SftpFileListing(path = path, size = size, isDirectory = isDir, modifiedDate = 1_000L)

    @Test
    fun `scanFolder throws when permission is denied`() = runTest {
        every { PermissionHelper.hasLocalNetworkPermission(any()) } returns false
        var thrown: Throwable? = null
        try {
            scanner.scanFolder("sftp://host:22/dir", imageTypes, null, "cred", true, false, null)
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is LocalNetworkPermissionDeniedException)
    }

    @Test
    fun `scanFolder returns empty for invalid path`() = runTest {
        val result = scanner.scanFolder("not-a-valid-path", imageTypes, null, "cred", true, false, null)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `scanFolder filters by media type and skips dirs hidden and trash`() = runTest {
        coEvery { sftpClient.listFiles(any(), any(), any(), any()) } returns Result.success(
            listOf(
                listing("/dir/a.jpg"),
                listing("/dir/clip.mp4"),
                listing("/dir/notes.txt"),       // unknown type, not all-files mode → skipped
                listing("/dir/.hidden.jpg"),     // hidden → skipped
                listing("/dir/sub", isDir = true),
                listing("/dir/.trash_1/x.jpg")   // trash segment in filename? filename is x.jpg, path has trash dir
            )
        )
        val result = scanner.scanFolder("sftp://host:22/dir", imageTypes, null, "cred", true, false, null)
        val names = result.map { it.name }.toSet()
        assertTrue(names.contains("a.jpg"))
        assertTrue(names.contains("clip.mp4"))
        assertTrue(!names.contains("notes.txt"))
        assertTrue(!names.contains(".hidden.jpg"))
    }

    @Test
    fun `scanFolder treats unknown files as TEXT in all-files mode`() = runTest {
        coEvery { sftpClient.listFiles(any(), any(), any(), any()) } returns Result.success(
            listOf(listing("/dir/readme.unknownext"))
        )
        val result = scanner.scanFolder("sftp://host:22/dir", allTypes, null, "cred", true, false, null)
        assertEquals(1, result.size)
        assertEquals(MediaType.TEXT, result.single().type)
    }

    @Test
    fun `scanFolder applies size filter`() = runTest {
        coEvery { sftpClient.listFiles(any(), any(), any(), any()) } returns Result.success(
            listOf(listing("/dir/small.jpg", size = 50L), listing("/dir/big.jpg", size = 5_000L))
        )
        val filter = SizeFilter(
            imageSizeMin = 1_000L, imageSizeMax = Long.MAX_VALUE,
            videoSizeMin = 0L, videoSizeMax = Long.MAX_VALUE,
            audioSizeMin = 0L, audioSizeMax = Long.MAX_VALUE
        )
        val result = scanner.scanFolder("sftp://host:22/dir", imageTypes, filter, "cred", true, false, null)
        assertEquals(listOf("big.jpg"), result.map { it.name })
    }

    @Test
    fun `scanFolder includes hidden files when requested`() = runTest {
        coEvery { sftpClient.listFiles(any(), any(), any(), any()) } returns Result.success(
            listOf(listing("/dir/.secret.jpg"))
        )
        val result = scanner.scanFolder("sftp://host:22/dir", imageTypes, null, "cred", true, true, null)
        assertEquals(listOf(".secret.jpg"), result.map { it.name })
    }

    @Test
    fun `scanFolder propagates list failure as IOException`() = runTest {
        coEvery { sftpClient.listFiles(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("connection refused"))
        var thrown: Throwable? = null
        try {
            scanner.scanFolder("sftp://host:22/dir", imageTypes, null, "cred", true, false, null)
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is IOException)
    }
}

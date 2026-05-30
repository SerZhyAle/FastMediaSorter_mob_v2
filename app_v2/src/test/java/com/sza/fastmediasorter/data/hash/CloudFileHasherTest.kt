package com.sza.fastmediasorter.data.hash

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Unit tests for [CloudFileHasher]: provider→client selection, null cloudItemId guard,
 * null/unknown provider guard, full-vs-capped length mapping, and CloudResult.Error propagation.
 * All cloud SDK clients are mocked - no network.
 */
class CloudFileHasherTest {

    private val gDrive = mockk<GoogleDriveRestClient>()
    private val dropbox = mockk<DropboxClient>()
    private val oneDrive = mockk<OneDriveRestClient>()
    private val hasher = CloudFileHasher(gDrive, dropbox, oneDrive)

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `computeHash uses google drive client for GOOGLE_DRIVE provider`() = runBlocking {
        val payload = "drive-bytes".toByteArray()
        coEvery { gDrive.getFileInputStream("ITEM", 0L, -1L) } returns
            CloudResult.Success(payload.inputStream())

        val file = createMediaFile(cloudItemId = "ITEM")
        val resource = createMediaResource(cloudProvider = CloudProvider.GOOGLE_DRIVE)

        val hash = hasher.computeHash(file, resource, maxBytes = -1L)
        assertEquals(md5(payload), hash)
    }

    @Test
    fun `computeHash maps positive maxBytes to length and caps digest`() = runBlocking {
        val payload = ByteArray(100) { it.toByte() }
        coEvery { dropbox.getFileInputStream("ITEM", 0L, 10L) } returns
            CloudResult.Success(payload.inputStream())

        val file = createMediaFile(cloudItemId = "ITEM")
        val resource = createMediaResource(cloudProvider = CloudProvider.DROPBOX)

        val hash = hasher.computeHash(file, resource, maxBytes = 10L)
        assertEquals(md5(payload.copyOfRange(0, 10)), hash)
        coVerify { dropbox.getFileInputStream("ITEM", 0L, 10L) }
    }

    @Test
    fun `computeHash selects onedrive client`() = runBlocking {
        val payload = "od".toByteArray()
        coEvery { oneDrive.getFileInputStream(any(), any(), any()) } returns
            CloudResult.Success(payload.inputStream())

        val hash = hasher.computeHash(
            createMediaFile(cloudItemId = "X"),
            createMediaResource(cloudProvider = CloudProvider.ONEDRIVE),
            maxBytes = -1L
        )
        assertEquals(md5(payload), hash)
    }

    @Test
    fun `computeHash throws when cloudItemId is null`() {
        val ex = runCatching {
            runBlocking {
                hasher.computeHash(
                    createMediaFile(cloudItemId = null),
                    createMediaResource(cloudProvider = CloudProvider.GOOGLE_DRIVE),
                    maxBytes = -1L
                )
            }
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `computeHash throws when provider is null`() {
        val ex = runCatching {
            runBlocking {
                hasher.computeHash(
                    createMediaFile(cloudItemId = "ITEM"),
                    createMediaResource(cloudProvider = null),
                    maxBytes = -1L
                )
            }
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `computeHash propagates CloudResult Error as exception`() {
        coEvery { gDrive.getFileInputStream(any(), any(), any()) } returns
            CloudResult.Error("download failed")

        val ex = runCatching {
            runBlocking {
                hasher.computeHash(
                    createMediaFile(cloudItemId = "ITEM"),
                    createMediaResource(cloudProvider = CloudProvider.GOOGLE_DRIVE),
                    maxBytes = -1L
                )
            }
        }.exceptionOrNull()
        assertEquals("download failed", ex?.message)
    }
}

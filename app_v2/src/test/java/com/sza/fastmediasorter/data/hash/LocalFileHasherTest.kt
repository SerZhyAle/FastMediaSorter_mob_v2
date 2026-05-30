package com.sza.fastmediasorter.data.hash

import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * Unit tests for [LocalFileHasher] and the shared [md5Hex] reader: full-stream vs capped MD5,
 * boundary behaviour at the cap, and the local-file (non-contentUri) read path via TemporaryFolder.
 * Pure JVM - the contentUri branch (Uri.parse + ContentResolver) is Android-bound and excluded.
 */
class LocalFileHasherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun expectedMd5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `md5Hex full stream matches reference digest`() {
        val data = "hello world".toByteArray()
        val hash = md5Hex(data.inputStream(), maxBytes = -1L)
        assertEquals(expectedMd5(data), hash)
    }

    @Test
    fun `md5Hex caps read at maxBytes`() {
        val data = ByteArray(100) { it.toByte() }
        val hash = md5Hex(data.inputStream(), maxBytes = 10L)
        assertEquals(expectedMd5(data.copyOfRange(0, 10)), hash)
    }

    @Test
    fun `md5Hex with cap larger than stream reads whole stream`() {
        val data = ByteArray(5) { it.toByte() }
        val hash = md5Hex(data.inputStream(), maxBytes = 1000L)
        assertEquals(expectedMd5(data), hash)
    }

    @Test
    fun `md5Hex zero cap yields empty-input digest`() {
        val data = ByteArray(50) { it.toByte() }
        val hash = md5Hex(data.inputStream(), maxBytes = 0L)
        assertEquals(expectedMd5(ByteArray(0)), hash)
    }

    @Test
    fun `md5Hex spans multiple read buffers`() {
        // 80 KB exceeds the 32 KB internal buffer, forcing several read() calls.
        val data = ByteArray(80 * 1024) { (it % 251).toByte() }
        val hash = md5Hex(data.inputStream(), maxBytes = -1L)
        assertEquals(expectedMd5(data), hash)
    }

    @Test
    fun `computeHash reads local file when contentUri is null`() = runBlocking {
        val payload = "local file payload".toByteArray()
        val file: File = tempFolder.newFile("data.bin").apply { writeBytes(payload) }

        val hasher = LocalFileHasher(context = mockk(relaxed = true))
        val mediaFile = createMediaFile(path = file.absolutePath, contentUri = null)

        val hash = hasher.computeHash(mediaFile, createMediaResource(), maxBytes = -1L)
        assertEquals(expectedMd5(payload), hash)
    }

    @Test
    fun `computeHash respects maxBytes for local file`() = runBlocking {
        val payload = ByteArray(200) { it.toByte() }
        val file = tempFolder.newFile("capped.bin").apply { writeBytes(payload) }

        val hasher = LocalFileHasher(context = mockk(relaxed = true))
        val mediaFile = createMediaFile(path = file.absolutePath, contentUri = null)

        val hash = hasher.computeHash(mediaFile, createMediaResource(), maxBytes = 16L)
        assertEquals(expectedMd5(payload.copyOfRange(0, 16)), hash)
    }
}

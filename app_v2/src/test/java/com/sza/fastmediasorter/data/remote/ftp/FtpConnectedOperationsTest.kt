package com.sza.fastmediasorter.data.remote.ftp

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException

/**
 * Unit tests for [FtpConnectedOperations]. The Apache Commons-Net [FTPClient] is injected via the
 * `getClient` lambda and mocked - no sockets are opened. Covers the not-connected guard, listing
 * with dot-entry filtering, passive→active fallback, paginated listing, and full-read bytes.
 */
class FtpConnectedOperationsTest {

    private val mutex = Any()

    private fun file(name: String) = FTPFile().apply {
        this.name = name
        type = FTPFile.FILE_TYPE
    }

    private fun dir(name: String) = FTPFile().apply {
        this.name = name
        type = FTPFile.DIRECTORY_TYPE
    }

    private fun ops(client: FTPClient?) = FtpConnectedOperations(getClient = { client }, mutex = mutex)

    @Test
    fun `upload rejects positive completion with truncated remote file`() = runTest {
        val payload = "complete".toByteArray()
        val client = mockk<FTPClient>(relaxed = true)
        every { client.storeFile("/f.bin", any()) } answers {
            secondArg<InputStream>().readBytes()
            true
        }
        every { client.getSize("/f.bin") } returns "1"
        val result = ops(client).uploadFile("/f.bin", payload.inputStream(), payload.size.toLong())
        assertTrue(result.isFailure)
        verify(exactly = 0) { client.deleteFile(any()) }
    }

    @Test
    fun `download rejects partial passive timeout without retrying into the same sink`() = runTest {
        val client = mockk<FTPClient>(relaxed = true)
        every { client.getSize("/f.bin") } returns "1"
        every { client.retrieveFile("/f.bin", any()) } answers {
            secondArg<OutputStream>().write(1)
            throw SocketTimeoutException("partial")
        }
        val result = ops(client).downloadFile("/f.bin", ByteArrayOutputStream())
        assertTrue(result.isFailure)
        verify(exactly = 0) { client.enterLocalActiveMode() }
    }

    @Test
    fun `download retries in active mode only before writing any bytes`() = runTest {
        val client = mockk<FTPClient>(relaxed = true)
        every { client.getSize("/f.bin") } returns "1"
        every { client.retrieveFile("/f.bin", any()) } throws SocketTimeoutException("connect") andThenAnswer {
            secondArg<OutputStream>().write(1)
            true
        }
        val output = ByteArrayOutputStream()
        assertTrue(ops(client).downloadFile("/f.bin", output).isSuccess)
        assertEquals(listOf(1.toByte()), output.toByteArray().toList())
        verify(exactly = 1) { client.enterLocalActiveMode() }
        verify(exactly = 1) { client.enterLocalPassiveMode() }
    }

    @Test
    fun `listFiles fails when not connected`() = runTest {
        val result = ops(null).listFiles("/")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `listFiles returns names and drops dot entries`() = runTest {
        val client = mockk<FTPClient>()
        every { client.listFiles("/dir") } returns arrayOf(file("a.mp4"), dir("."), dir(".."), file("b.jpg"))
        val result = ops(client).listFiles("/dir")
        assertEquals(listOf("a.mp4", "b.jpg"), result.getOrThrow())
    }

    @Test
    fun `listFiles falls back to active mode on passive timeout`() = runTest {
        val client = mockk<FTPClient>(relaxed = true)
        every { client.listFiles("/dir") } throws SocketTimeoutException("passive") andThen
            arrayOf(file("recovered.mp4"))
        val result = ops(client).listFiles("/dir")
        assertEquals(listOf("recovered.mp4"), result.getOrThrow())
        verify(exactly = 1) { client.enterLocalActiveMode() }
        verify(exactly = 1) { client.enterLocalPassiveMode() }
    }

    @Test
    fun `listFilesWithMetadata fails when not connected`() = runTest {
        val result = ops(null).listFilesWithMetadata("/", recursive = false)
        assertTrue(result.isFailure)
    }

    @Test
    fun `listFilesWithMetadata single level returns files`() = runTest {
        val client = mockk<FTPClient>()
        every { client.listFiles("/dir") } returns arrayOf(file("a.mp4"), dir("sub"))
        val result = ops(client).listFilesWithMetadata("/dir", recursive = false)
        assertEquals(listOf("a.mp4"), result.getOrThrow().map { it.name })
    }

    @Test
    fun `listFilesWithMetadataPaged returns empty for non-positive limit`() = runTest {
        val client = mockk<FTPClient>(relaxed = true)
        val result = ops(client).listFilesWithMetadataPaged("/dir", offset = 0, limit = 0)
        assertEquals(emptyList<FTPFile>(), result.getOrThrow())
        verify(exactly = 0) { client.listFiles(any()) }
    }

    @Test
    fun `listFilesWithMetadataPaged non-recursive applies offset and limit`() = runTest {
        val client = mockk<FTPClient>()
        every { client.listFiles("/dir") } returns arrayOf(
            file("f0"), file("f1"), file("f2"), file("f3")
        )
        val result = ops(client).listFilesWithMetadataPaged("/dir", offset = 1, limit = 2, recursive = false)
        assertEquals(listOf("f1", "f2"), result.getOrThrow().map { it.name })
    }

    @Test
    fun `readFileBytes full read returns stream content`() = runTest {
        val payload = "ftp-bytes".toByteArray()
        val client = mockk<FTPClient>()
        every { client.retrieveFileStream("/f.bin") } returns payload.inputStream()
        every { client.completePendingCommand() } returns true
        val result = ops(client).readFileBytes("/f.bin", maxBytes = Long.MAX_VALUE)
        assertEquals(payload.toList(), result.getOrThrow().toList())
    }

    @Test
    fun `readFileBytes fails when completePendingCommand returns false`() = runTest {
        val client = mockk<FTPClient>()
        every { client.retrieveFileStream("/f.bin") } returns "x".byteInputStream()
        every { client.completePendingCommand() } returns false
        val result = ops(client).readFileBytes("/f.bin", maxBytes = Long.MAX_VALUE)
        assertTrue(result.isFailure)
    }

    @Test
    fun `readFileBytes fails when stream cannot open`() = runTest {
        val client = mockk<FTPClient>()
        every { client.retrieveFileStream("/missing") } returns null
        val result = ops(client).readFileBytes("/missing", maxBytes = Long.MAX_VALUE)
        assertTrue(result.isFailure)
    }
}

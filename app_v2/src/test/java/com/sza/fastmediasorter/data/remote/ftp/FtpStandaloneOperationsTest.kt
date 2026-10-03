package com.sza.fastmediasorter.data.remote.ftp

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.apache.commons.net.ftp.FTPClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Unit tests for the JVM-reachable helper of [FtpStandaloneOperations]:
 * [FtpStandaloneOperations.ensureRemoteDirectoryExists] and transfer integrity. Constructor mocks
 * keep transfer regressions independent of sockets and server availability.
 */
class FtpStandaloneOperationsTest {

    @Test
    fun `standalone uploads and downloads reject positive truncated completion`() = runTest {
        mockkConstructor(FTPClient::class)
        try {
            every { anyConstructed<FTPClient>().connect(any<String>(), any<Int>()) } returns Unit
            every { anyConstructed<FTPClient>().replyCode } returns 220
            every { anyConstructed<FTPClient>().login(any(), any()) } returns true
            every { anyConstructed<FTPClient>().enterLocalPassiveMode() } returns Unit
            every { anyConstructed<FTPClient>().setFileType(any()) } returns true
            every { anyConstructed<FTPClient>().sendCommand(any<String>(), any<String>()) } returns 200
            every { anyConstructed<FTPClient>().getSize("file.bin") } returns "1"
            every { anyConstructed<FTPClient>().storeFile("file.bin", any()) } answers {
                secondArg<InputStream>().readBytes()
                true
            }
            every { anyConstructed<FTPClient>().retrieveFile("file.bin", any()) } answers {
                secondArg<OutputStream>().write("short".toByteArray())
                true
            }
            val upload = FtpStandaloneOperations.uploadFile(
                "host",
                21,
                "user",
                "pass",
                "file.bin",
                "complete".byteInputStream()
            )
            val download = FtpStandaloneOperations.downloadFile(
                "host",
                21,
                "user",
                "pass",
                "file.bin",
                ByteArrayOutputStream()
            )
            assertTrue(upload.isFailure)
            assertTrue(download.isFailure)
            verify(exactly = 1) { anyConstructed<FTPClient>().storeFile("file.bin", any()) }
            verify(exactly = 1) { anyConstructed<FTPClient>().retrieveFile("file.bin", any()) }
        } finally {
            unmockkConstructor(FTPClient::class)
        }
    }

    @Test
    fun `ensureRemoteDirectoryExists is a no-op for empty path`() {
        val client = mockk<FTPClient>(relaxed = true)
        FtpStandaloneOperations.ensureRemoteDirectoryExists(client, "")
        verify(exactly = 0) { client.makeDirectory(any()) }
    }

    @Test
    fun `ensureRemoteDirectoryExists creates each path segment in order`() {
        val client = mockk<FTPClient>()
        every { client.makeDirectory(any()) } returns true
        FtpStandaloneOperations.ensureRemoteDirectoryExists(client, "/a/b/c")
        verifyOrder {
            client.makeDirectory("a")
            client.makeDirectory("a/b")
            client.makeDirectory("a/b/c")
        }
    }

    @Test
    fun `ensureRemoteDirectoryExists swallows mkdir exceptions and keeps going`() {
        val client = mockk<FTPClient>()
        every { client.makeDirectory("a") } throws RuntimeException("exists")
        every { client.makeDirectory("a/b") } returns true
        FtpStandaloneOperations.ensureRemoteDirectoryExists(client, "a/b")
        verify { client.makeDirectory("a/b") }
    }
}

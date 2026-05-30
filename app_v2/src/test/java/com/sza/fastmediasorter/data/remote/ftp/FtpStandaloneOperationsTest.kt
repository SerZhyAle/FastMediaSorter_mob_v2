package com.sza.fastmediasorter.data.remote.ftp

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.apache.commons.net.ftp.FTPClient
import org.junit.Test

/**
 * Unit tests for the JVM-reachable helper of [FtpStandaloneOperations]:
 * [FtpStandaloneOperations.ensureRemoteDirectoryExists]. The remaining methods open a fresh
 * socket-backed [FTPClient] internally and are out of scope. The client here is mocked.
 */
class FtpStandaloneOperationsTest {

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

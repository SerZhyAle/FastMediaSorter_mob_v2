package com.sza.fastmediasorter.data.remote.ftp

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException

/** Unit tests for [FtpDirectoryScanner] - traversal, recursion, pagination, mode fallback. */
class FtpDirectoryScannerTest {

    private fun file(name: String) = FTPFile().apply {
        this.name = name
        type = FTPFile.FILE_TYPE
    }

    private fun dir(name: String) = FTPFile().apply {
        this.name = name
        type = FTPFile.DIRECTORY_TYPE
    }

    // ── single level ─────────────────────────────────────────────────────────

    @Test
    fun `single level keeps files and drops dirs and dot entries`() {
        val client = mockk<FTPClient>()
        every { client.listFiles("/root") } returns arrayOf(
            file("a.mp4"), dir("sub"), file("b.jpg"), dir("."), dir("..")
        )
        val results = mutableListOf<FTPFile>()
        FtpDirectoryScanner.listFilesWithMetadataSingleLevel(client, "/root", results)
        assertEquals(listOf("a.mp4", "b.jpg"), results.map { it.name })
    }

    // ── recursive ─────────────────────────────────────────────────────────────

    @Test
    fun `recursive descends into subdirectories`() {
        val client = mockk<FTPClient>()
        every { client.listFiles("/root") } returns arrayOf(file("top.mp4"), dir("sub"))
        every { client.listFiles("/root/sub") } returns arrayOf(file("nested.mp4"), dir("."), dir(".."))
        val results = mutableListOf<FTPFile>()
        FtpDirectoryScanner.listFilesWithMetadataRecursive(client, "/root", results)
        assertEquals(listOf("top.mp4", "nested.mp4"), results.map { it.name })
    }

    @Test
    fun `recursive joins paths without double slash for trailing-slash parent`() {
        val client = mockk<FTPClient>()
        every { client.listFiles("/root/") } returns arrayOf(dir("sub"))
        every { client.listFiles("/root/sub") } returns arrayOf(file("x.mp4"))
        val results = mutableListOf<FTPFile>()
        FtpDirectoryScanner.listFilesWithMetadataRecursive(client, "/root/", results)
        assertEquals(listOf("x.mp4"), results.map { it.name })
        verify { client.listFiles("/root/sub") }
    }

    // ── paged ──────────────────────────────────────────────────────────────────

    @Test
    fun `paged skips offset and stops at limit`() {
        val client = mockk<FTPClient>()
        every { client.listFiles("/root") } returns arrayOf(
            file("f0"), file("f1"), file("f2"), file("f3"), file("f4")
        )
        val results = mutableListOf<FTPFile>()
        val paging = FtpDirectoryScanner.MetadataPagingState(offset = 1, limit = 2)
        FtpDirectoryScanner.listFilesWithMetadataRecursivePaged(client, "/root", results, paging)
        assertEquals(listOf("f1", "f2"), results.map { it.name })
        assertEquals(2, paging.collected)
    }

    @Test
    fun `paged returns immediately when limit already reached`() {
        val client = mockk<FTPClient>(relaxed = true)
        val results = mutableListOf<FTPFile>()
        val paging = FtpDirectoryScanner.MetadataPagingState(offset = 0, limit = 2, collected = 2)
        FtpDirectoryScanner.listFilesWithMetadataRecursivePaged(client, "/root", results, paging)
        verify(exactly = 0) { client.listFiles(any()) }
        assertEquals(0, results.size)
    }

    // ── passive/active fallback ─────────────────────────────────────────────────

    @Test
    fun `passive timeout falls back to active mode then restores passive`() {
        val client = mockk<FTPClient>(relaxed = true)
        every { client.listFiles("/root") } throws SocketTimeoutException("passive timeout") andThen
            arrayOf(file("recovered.mp4"))
        val results = mutableListOf<FTPFile>()
        FtpDirectoryScanner.listFilesWithMetadataSingleLevel(client, "/root", results)
        assertEquals(listOf("recovered.mp4"), results.map { it.name })
        verify(exactly = 1) { client.enterLocalActiveMode() }
        verify(exactly = 1) { client.enterLocalPassiveMode() }
    }
}

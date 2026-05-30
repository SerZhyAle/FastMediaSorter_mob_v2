package com.sza.fastmediasorter.data.network

import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeoutException

/**
 * Unit tests for the [SmbClient] facade. Covers the inline [SmbClient.listFiles] traversal and the
 * [SmbClient.testConnection] retry/backoff classification. The connection manager is mocked; the
 * share-discovery / scan coordinators are constructed from that mock, so no sockets are opened.
 */
class SmbClientTest {

    private val info = SmbConnectionInfo(server = "srv", shareName = "share")

    private fun item(name: String, isDir: Boolean): FileIdBothDirectoryInformation {
        val i = mockk<FileIdBothDirectoryInformation>()
        every { i.fileName } returns name
        every { i.fileAttributes } returns if (isDir) 0x10L else 0L
        every { i.endOfFile } returns 10L
        val ft = mockk<FileTime>()
        every { ft.toEpochMillis() } returns 1L
        every { i.lastWriteTime } returns ft
        return i
    }

    private fun client(manager: SmbConnectionManager): SmbClient =
        SmbClient(manager, mockk(relaxed = true), mockk(relaxed = true))

    @Test
    fun `listFiles maps share entries and skips dot entries`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("dir") } returns listOf(
            item("a.mp4", false), item("sub", true), item(".", true), item("..", true)
        )
        val manager = mockk<SmbConnectionManager>()
        val blockSlot = slot<suspend (DiskShare) -> SmbResult<Any?>>()
        coEvery { manager.withConnection(any(), any(), capture(blockSlot)) } coAnswers {
            blockSlot.captured.invoke(share)
        }
        val result = client(manager).listFiles(info, "/dir/")
        val data = (result as SmbResult.Success).data
        assertEquals(setOf("a.mp4" to false, "sub" to true), data.map { it.name to it.isDirectory }.toSet())
        assertEquals("dir/a.mp4", data.first { it.name == "a.mp4" }.path)
    }

    @Test
    fun `listFiles wraps exception`() = runTest {
        val manager = mockk<SmbConnectionManager>()
        coEvery { manager.withConnection(any(), any(), any<suspend (DiskShare) -> SmbResult<Any?>>()) } throws
            RuntimeException("offline")
        val result = client(manager).listFiles(info, "")
        assertTrue((result as SmbResult.Error).message.contains("Failed to list files"))
    }

    @Test
    fun `testConnection retries on timeout then fails as error`() = runTest {
        val manager = mockk<SmbConnectionManager>()
        // performTestConnection (non-empty shareName) calls withConnection; throw a timeout each time.
        coEvery { manager.withConnection(any(), any(), any<suspend (DiskShare) -> SmbResult<Any?>>()) } throws
            TimeoutException("Timed out")
        every { manager.getClient(any(), any()) } returns mockk(relaxed = true)
        val result = client(manager).testConnection(info, "folder")
        assertTrue(result is SmbResult.Error)
        // 3 attempts (MAX_RETRY_ATTEMPTS) of the share-path withConnection call.
        coVerify(exactly = 3) { manager.withConnection(any(), any(), any<suspend (DiskShare) -> SmbResult<Any?>>()) }
    }

    @Test
    fun `testConnection does not retry on non-retriable error`() = runTest {
        val manager = mockk<SmbConnectionManager>()
        coEvery { manager.withConnection(any(), any(), any<suspend (DiskShare) -> SmbResult<Any?>>()) } throws
            RuntimeException("auth denied")
        every { manager.getClient(any(), any()) } returns mockk(relaxed = true)
        val result = client(manager).testConnection(info, "folder")
        assertTrue(result is SmbResult.Error)
        coVerify(exactly = 1) { manager.withConnection(any(), any(), any<suspend (DiskShare) -> SmbResult<Any?>>()) }
    }
}

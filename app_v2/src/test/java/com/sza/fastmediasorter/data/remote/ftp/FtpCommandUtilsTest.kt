package com.sza.fastmediasorter.data.remote.ftp

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.commons.net.ftp.FTPClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** Unit tests for FtpCommandUtils bounded-read and safe-complete helpers. No real FTP connection. */
class FtpCommandUtilsTest {

    // ── safeCompletePendingCommand ───────────────────────────────────────────

    @Test
    fun `safeComplete returns underlying result when no NPE`() {
        val client = mockk<FTPClient>()
        every { client.completePendingCommand() } returns true
        assertTrue(safeCompletePendingCommand(client, "test"))
    }

    @Test
    fun `safeComplete converts internal NPE into IOException`() {
        val client = mockk<FTPClient>()
        every { client.completePendingCommand() } throws NullPointerException("getReply")
        val ex = assertThrows(IOException::class.java) {
            safeCompletePendingCommand(client, "download")
        }
        assertTrue(ex.message!!.contains("download"))
    }

    // ── readBoundedAndAbort ──────────────────────────────────────────────────

    @Test
    fun `rejects non-positive maxBytes`() {
        val client = mockk<FTPClient>(relaxed = true)
        assertThrows(IllegalArgumentException::class.java) {
            readBoundedAndAbort(client, ByteArrayInputStream(ByteArray(0)), maxBytes = 0, label = "x")
        }
    }

    @Test
    fun `natural EOF before cap returns all bytes without abort`() {
        val client = mockk<FTPClient>()
        every { client.completePendingCommand() } returns true
        val payload = "hello".toByteArray()
        val result = readBoundedAndAbort(client, ByteArrayInputStream(payload), maxBytes = 1000, label = "eof")
        assertArrayEquals(payload, result.bytes)
        assertFalse(result.abortInvoked)
        assertTrue(result.completeOk)
        verify(exactly = 0) { client.abort() }
    }

    @Test
    fun `cap reached triggers abort and truncates to exactly maxBytes`() {
        val client = mockk<FTPClient>()
        every { client.abort() } returns true
        every { client.completePendingCommand() } returns true
        val payload = ByteArray(10_000) { (it % 256).toByte() }
        val result = readBoundedAndAbort(client, ByteArrayInputStream(payload), maxBytes = 100, label = "cap")
        assertEquals(100, result.bytes.size)
        assertTrue(result.abortInvoked)
        verify(exactly = 1) { client.abort() }
    }

    @Test
    fun `abort IOException is swallowed and bytes preserved`() {
        val client = mockk<FTPClient>()
        every { client.abort() } throws IOException("abort failed")
        every { client.completePendingCommand() } returns true
        val payload = ByteArray(5000) { 1 }
        val result = readBoundedAndAbort(client, ByteArrayInputStream(payload), maxBytes = 50, label = "x")
        assertEquals(50, result.bytes.size)
        assertTrue(result.abortInvoked)
    }

    @Test
    fun `completePendingCommand IOException reports completeOk false but returns bytes`() {
        val client = mockk<FTPClient>()
        every { client.abort() } returns true
        every { client.completePendingCommand() } throws IOException("control closed")
        val payload = ByteArray(5000) { 2 }
        val result = readBoundedAndAbort(client, ByteArrayInputStream(payload), maxBytes = 50, label = "x")
        assertEquals(50, result.bytes.size)
        assertFalse(result.completeOk)
    }

    @Test
    fun `read loop IOException propagates to caller`() {
        val client = mockk<FTPClient>(relaxed = true)
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("stream broke")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("stream broke")
        }
        assertThrows(IOException::class.java) {
            readBoundedAndAbort(client, failing, maxBytes = 100, label = "fail")
        }
    }
}

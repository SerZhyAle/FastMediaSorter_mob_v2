package com.sza.fastmediasorter.data.remote.ftp

import com.sza.fastmediasorter.data.remote.ftp.helpers.FtpTransferIntegrityManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException

class FtpTransferIntegrityManagerTest {

    private val path = "/file.bin"
    private val payload = "complete".toByteArray()

    private fun uploadClient(storedSize: Long = payload.size.toLong()): FTPClient {
        val client = mockk<FTPClient>()
        every { client.storeFile(path, any()) } answers {
            secondArg<InputStream>().readBytes()
            true
        }
        every { client.getSize(path) } returns storedSize.toString()
        return client
    }

    @Test
    fun `upload accepts matching stored and consumed lengths`() {
        FtpTransferIntegrityManager.upload(uploadClient(), path, payload.inputStream(), payload.size.toLong())
    }

    @Test
    fun `positive upload completion with truncated stored file fails`() {
        val client = uploadClient(storedSize = 1L)
        assertThrows(IOException::class.java) {
            FtpTransferIntegrityManager.upload(client, path, payload.inputStream(), payload.size.toLong())
        }
        verify(exactly = 0) { client.deleteFile(any()) }
    }

    @Test
    fun `short source fails even when stored size matches consumed bytes`() {
        assertThrows(IOException::class.java) {
            FtpTransferIntegrityManager.upload(uploadClient(), path, payload.inputStream(), payload.size + 1L)
        }
    }

    @Test
    fun `unknown caller size still verifies stored length`() {
        FtpTransferIntegrityManager.upload(uploadClient(), path, payload.inputStream(), 0L)
        assertThrows(IOException::class.java) {
            FtpTransferIntegrityManager.upload(uploadClient(1L), path, payload.inputStream(), 0L)
        }
    }

    @Test
    fun `empty upload is verified as zero bytes`() {
        FtpTransferIntegrityManager.upload(uploadClient(0L), path, byteArrayOf().inputStream(), 0L)
    }

    @Test
    fun `positive upload completion that leaves unread input fails`() {
        val client = mockk<FTPClient>()
        every { client.storeFile(path, any()) } returns true
        assertThrows(IOException::class.java) {
            FtpTransferIntegrityManager.upload(client, path, payload.inputStream(), 0L)
        }
    }

    @Test
    fun `negative upload completion fails without metadata query`() {
        val client = mockk<FTPClient>()
        every { client.storeFile(path, any()) } returns false
        every { client.replyString } returns "550 refused"
        assertThrows(IOException::class.java) {
            FtpTransferIntegrityManager.upload(client, path, payload.inputStream(), 0L)
        }
        verify(exactly = 0) { client.getSize(any()) }
    }

    @Test
    fun `size falls back to MLST including an explicit empty file`() {
        val client = mockk<FTPClient>()
        every { client.getSize(path) } returns null
        every { client.mlistFile(path) } returns FTPFile().apply {
            type = FTPFile.FILE_TYPE
            rawListing = "type=file;size=0; file.bin"
            size = 0L
        }
        assertEquals(0L, FtpTransferIntegrityManager.remoteSize(client, path))
    }

    @Test
    fun `missing MLST size fact uses exact LIST entry instead of default zero`() {
        val client = mockk<FTPClient>()
        every { client.getSize(path) } returns null
        every { client.mlistFile(path) } returns FTPFile().apply {
            type = FTPFile.FILE_TYPE
            rawListing = "type=file; file.bin"
        }
        every { client.listFiles(path) } returns arrayOf(
            FTPFile().apply {
                type = FTPFile.FILE_TYPE
                name = "file.bin"
                size = payload.size.toLong()
            }
        )
        assertEquals(payload.size.toLong(), FtpTransferIntegrityManager.remoteSize(client, path))
    }

    @Test
    fun `missing metadata and unrelated listing cannot prove transfer length`() {
        val client = mockk<FTPClient>()
        every { client.getSize(path) } returns "-1"
        every { client.mlistFile(path) } returns null
        every { client.listFiles(path) } returns arrayOf(
            FTPFile().apply {
                type = FTPFile.FILE_TYPE
                name = "different.bin"
                size = payload.size.toLong()
            }
        )
        assertThrows(IOException::class.java) { FtpTransferIntegrityManager.remoteSize(client, path) }
    }

    @Test
    fun `malformed MLST size cannot be mistaken for an empty file`() {
        val client = mockk<FTPClient>()
        every { client.getSize(path) } returns null
        every { client.mlistFile(path) } returns FTPFile().apply {
            type = FTPFile.FILE_TYPE
            rawListing = "type=file;size=invalid; file.bin"
            size = 0L
        }
        every { client.listFiles(path) } returns emptyArray()
        assertThrows(IOException::class.java) { FtpTransferIntegrityManager.remoteSize(client, path) }
    }

    private fun downloadClient(bytes: ByteArray = payload): FTPClient {
        val client = mockk<FTPClient>()
        every { client.getSize(path) } returns payload.size.toString()
        every { client.retrieveFile(path, any()) } answers {
            secondArg<OutputStream>().write(bytes)
            true
        }
        return client
    }

    @Test
    fun `download verifies the actual bytes written`() {
        val output = ByteArrayOutputStream()
        FtpTransferIntegrityManager.download(downloadClient(), path, output)
        assertEquals(payload.toList(), output.toByteArray().toList())
    }

    @Test
    fun `positive download completion with short or oversized output fails`() {
        for (bytes in listOf(byteArrayOf(), payload + payload)) {
            assertThrows(IOException::class.java) {
                FtpTransferIntegrityManager.download(downloadClient(bytes), path, ByteArrayOutputStream())
            }
        }
    }

    @Test
    fun `empty download succeeds when remote size is zero`() {
        val client = downloadClient(byteArrayOf())
        every { client.getSize(path) } returns "0"
        FtpTransferIntegrityManager.download(client, path, ByteArrayOutputStream())
    }

    @Test
    fun `negative download completion fails`() {
        val client = downloadClient()
        every { client.retrieveFile(path, any()) } returns false
        every { client.replyString } returns "550 refused"
        assertThrows(IOException::class.java) {
            FtpTransferIntegrityManager.download(client, path, ByteArrayOutputStream())
        }
    }

    @Test
    fun `partial timeout is converted to non-retryable failure`() {
        val client = downloadClient()
        every { client.retrieveFile(path, any()) } answers {
            secondArg<OutputStream>().write(payload)
            throw SocketTimeoutException("late timeout")
        }
        val failure = assertThrows(IOException::class.java) {
            FtpTransferIntegrityManager.download(client, path, ByteArrayOutputStream())
        }
        assertEquals(IOException::class.java, failure.javaClass)
    }
}

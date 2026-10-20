package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveAppDataApi
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveHttpClient
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousCodec
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class GoogleDriveSftpRendezvousBoundedReadTest {

    private class InstrumentedInputStream(private val delegate: InputStream) : InputStream() {
        var bytesRead: Int = 0
            private set
        var closed: Boolean = false
            private set

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val r = delegate.read(b, off, len)
            if (r > 0) bytesRead += r
            return r
        }

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    @Test
    fun `oversized stream stops consumption at max bytes plus one and closes stream`() = runBlocking {
        val totalBytes = 32 * 1024
        val maxRecordBytes = SftpRendezvousCodec.MAX_RECORD_BYTES.toLong()
        val oversizedData = ByteArray(totalBytes) { 0x42.toByte() }
        val instrumented = InstrumentedInputStream(ByteArrayInputStream(oversizedData))

        val context = mockk<Context>(relaxed = true)
        every { context.getString(R.string.download_failed, any()) } returns "Download failed"

        val auth = mockk<GoogleDriveBrowserAuthManager>()
        every { auth.hasActiveSession() } returns true
        coEvery { auth.getFreshAccessToken() } returns "valid_token"

        val httpClient = mockk<GoogleDriveHttpClient>()
        coEvery {
            httpClient.getFileInputStream("file_123", any(), "valid_token", 0L, -1L)
        } returns GoogleDriveHttpClient.StreamResult.Success(instrumented)

        val identityRepo = mockk<GoogleIdentityRepository>(relaxed = true)

        val appDataApi = GoogleDriveAppDataApi(context, httpClient, identityRepo, auth)

        val result = appDataApi.downloadFile("file_123", maxBytes = maxRecordBytes)

        assertTrue("Result should be CloudResult.Error for oversized stream", result is CloudResult.Error)
        assertTrue(
            "Should read at most MAX_RECORD_BYTES + 1 before rejection (read ${instrumented.bytesRead})",
            instrumented.bytesRead <= maxRecordBytes + 1
        )
        assertTrue("Underlying stream must be closed", instrumented.closed)
    }

    @Test
    fun `within bound stream succeeds and reads all bytes`() = runBlocking {
        val payload = "{\"device\": \"test\"}".toByteArray(Charsets.UTF_8)
        val instrumented = InstrumentedInputStream(ByteArrayInputStream(payload))

        val context = mockk<Context>(relaxed = true)
        val auth = mockk<GoogleDriveBrowserAuthManager>()
        every { auth.hasActiveSession() } returns true
        coEvery { auth.getFreshAccessToken() } returns "valid_token"

        val httpClient = mockk<GoogleDriveHttpClient>()
        coEvery {
            httpClient.getFileInputStream("file_ok", any(), "valid_token", 0L, -1L)
        } returns GoogleDriveHttpClient.StreamResult.Success(instrumented)

        val identityRepo = mockk<GoogleIdentityRepository>(relaxed = true)
        val appDataApi = GoogleDriveAppDataApi(context, httpClient, identityRepo, auth)

        val result = appDataApi.downloadFile("file_ok", maxBytes = SftpRendezvousCodec.MAX_RECORD_BYTES.toLong())

        assertTrue(result is CloudResult.Success)
        val success = result as CloudResult.Success
        assertEquals(payload.size, success.data.size)
        assertEquals(payload.size, instrumented.bytesRead)
        assertTrue(instrumented.closed)
    }

    @Test
    fun `rendezvous data source rejects file by metadata size prefilter`() = runBlocking {
        val appData = mockk<GoogleDriveAppDataApi>()
        val dataSource = GoogleDriveSftpRendezvousDataSource(appData)

        val oversizedFile = CloudFile(
            id = "f1",
            name = "fmsx-req-test.json",
            path = "",
            isFolder = false,
            size = (SftpRendezvousCodec.MAX_RECORD_BYTES + 100).toLong()
        )

        coEvery { appData.listChildren(any()) } returns CloudResult.Success(listOf(oversizedFile))

        val snapshot = dataSource.snapshot().getOrThrow()
        assertTrue("Oversized record by metadata must be filtered out", snapshot.requests.isEmpty())
    }

    @Test
    fun `rendezvous data source rejects oversized stream even if metadata size is small`() = runBlocking {
        val appData = mockk<GoogleDriveAppDataApi>()
        val dataSource = GoogleDriveSftpRendezvousDataSource(appData)

        val fakeSmallFile = CloudFile(
            id = "f2",
            name = "fmsx-req-test2.json",
            path = "",
            isFolder = false,
            size = 100L // metadata claims small
        )

        coEvery { appData.listChildren(any()) } returns CloudResult.Success(listOf(fakeSmallFile))
        // Bounded download rejects stream
        coEvery {
            appData.downloadFile("f2", SftpRendezvousCodec.MAX_RECORD_BYTES.toLong())
        } returns CloudResult.Error("Exceeds max bounded size")

        val snapshot = dataSource.snapshot().getOrThrow()
        assertTrue("Stream rejection must result in no decoded record", snapshot.requests.isEmpty())
    }
}

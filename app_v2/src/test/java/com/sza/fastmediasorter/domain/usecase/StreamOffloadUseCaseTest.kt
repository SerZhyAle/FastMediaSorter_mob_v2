package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategies.FtpToLocalStrategy
import com.sza.fastmediasorter.data.transfer.strategies.SftpToLocalStrategy
import com.sza.fastmediasorter.data.transfer.strategies.SmbToLocalStrategy
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StreamOffloadUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val smb = mockk<SmbToLocalStrategy>()
    private val sftp = mockk<SftpToLocalStrategy>()
    private val ftp = mockk<FtpToLocalStrategy>()
    private val cacheRepository = mockk<StreamingCacheRepository>()

    private lateinit var filesDir: File
    private lateinit var useCase: StreamOffloadUseCase

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        // Force the external-files fallback path to use the temp filesDir.
        every { context.getExternalFilesDir(any()) } returns null
        every { context.filesDir } returns filesDir
        useCase = StreamOffloadUseCase(
            context = context,
            smbToLocal = smb,
            sftpToLocal = sftp,
            ftpToLocal = ftp,
            directoryStrategies = emptyMap<String, FileOperationStrategy>(),
            streamingCacheRepository = cacheRepository,
        )
    }

    @Test
    fun `estimateDownloadSec returns MAX_VALUE for non-positive speed`() {
        assertEquals(Long.MAX_VALUE, useCase.estimateDownloadSec(1_000_000L, 0.0))
        assertEquals(Long.MAX_VALUE, useCase.estimateDownloadSec(1_000_000L, -5.0))
    }

    @Test
    fun `estimateDownloadSec computes seconds and floors at 1`() {
        // 10 Mbit at 10 Mbps = 1s.
        assertEquals(1L, useCase.estimateDownloadSec(1_250_000L, 10.0))
        // Tiny file is coerced to at least 1 second.
        assertEquals(1L, useCase.estimateDownloadSec(10L, 100.0))
    }

    @Test
    fun `resolveDestinationFile sanitizes filename and nests under hash`() {
        val file = useCase.resolveDestinationFile("abc123", "ev:il/na*me?.mp4")

        assertTrue(file.parentFile!!.name == "abc123")
        assertFalse(file.name.contains("/"))
        assertFalse(file.name.contains(":"))
        assertFalse(file.name.contains("*"))
        assertEquals("ev_il_na_me_.mp4", file.name)
    }

    @Test
    fun `resolveDestinationFile blank name falls back to file`() {
        val file = useCase.resolveDestinationFile("h", "")
        assertEquals("file", file.name)
    }

    @Test
    fun `hasFreeSpace is true when file fits within usable space`() {
        assertTrue(useCase.hasFreeSpace(1L))
    }

    @Test
    fun `hasFreeSpace is false when file plus headroom exceeds usable space`() {
        // Use slightly more than the real usable space; the *1.1 headroom guarantees a false result
        // without overflowing Long (disk sizes are far below Long.MAX_VALUE / 11).
        val usable = File(filesDir, "streaming_cache").let { it.mkdirs(); it.usableSpace }
        assertFalse(useCase.hasFreeSpace(usable + 1L))
    }

    @Test
    fun `run emits Failed INSUFFICIENT_SPACE when space check fails`() = runTest {
        every { cacheRepository.resolveHash(any()) } returns "hash1"
        val usable = File(filesDir, "streaming_cache").let { it.mkdirs(); it.usableSpace }
        val request = StreamOffloadUseCase.OffloadRequest(
            originalUri = "smb://h/share/big.mkv",
            filename = "big.mkv",
            sizeBytes = usable + 1L, // exceeds usable space after the 1.1x headroom, no Long overflow
            resourceKey = "key",
            sourceProtocol = StreamOffloadUseCase.SourceProtocol.SMB,
        )

        // The producer keeps the channelFlow open via awaitClose, so collect only the first
        // emission; `first()` cancels collection and lets the flow settle.
        val first = useCase.run(request).first()

        val failed = first as StreamOffloadUseCase.OffloadProgress.Failed
        assertEquals(StreamOffloadUseCase.FailureReason.INSUFFICIENT_SPACE, failed.reason)
    }
}

package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeClipboardClient
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import com.sza.fastmediasorter.domain.model.ExchangeClipboardMeta
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class ExchangeClipboardRepositoryTest {

    private val client = mockk<DeviceExchangeClipboardClient>()
    private val accounts = mockk<DeviceExchangeAccountRepository>(relaxed = true)
    private val e2e = mockk<ExchangeE2eRepository>(relaxed = true)

    private fun TestScope.repository() =
        ExchangeClipboardRepository(
            client = client,
            accounts = accounts,
            e2e = e2e,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    @Test
    fun `cancelled put aborts hashing without reading the whole stream`() = runTest {
        val totalSize = 10 * 1024 * 1024L
        val entry = DeviceExchangeAccountEntry("127.0.0.1", 8443, "alice", "DEVICE_A", null)
        val meta = ExchangeClipboardMeta(ExchangeClipboardKind.TEXT, totalSize, null, null)

        var jobRef: Job? = null
        var bytesRead = 0L

        val fakeStream = object : InputStream() {
            override fun read(): Int {
                if (bytesRead >= totalSize) return -1
                bytesRead++
                return 0x55
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesRead >= totalSize) return -1
                val chunk = minOf(len.toLong(), totalSize - bytesRead).toInt()
                bytesRead += chunk
                if (bytesRead >= 64 * 1024) {
                    jobRef?.cancel()
                }
                return chunk
            }
        }

        val job = launch {
            try {
                repository().put(
                    entry = entry,
                    meta = meta,
                    openSource = { fakeStream },
                    onProgress = {},
                )
            } catch (e: CancellationException) {
                throw e
            }
        }
        jobRef = job

        job.join()

        assertTrue("Expected job to be cancelled", job.isCancelled)
        assertTrue("Stream should not be read to the end: read $bytesRead of $totalSize", bytesRead < totalSize)
    }
}

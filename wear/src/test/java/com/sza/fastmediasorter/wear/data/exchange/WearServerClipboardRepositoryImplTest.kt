package com.sza.fastmediasorter.wear.data.exchange

import android.content.Context
import android.net.Network
import com.sza.fastmediasorter.wear.data.network.PendingTransferNetwork
import com.sza.fastmediasorter.wear.data.network.TransferNetworkRequester
import com.sza.fastmediasorter.wear.data.network.WearTransferNetworkManager
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardFailure
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardReceiveReport
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSaved
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSink
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** S4227: a get that does not end in a verified value places nothing (strategic goal 5). */
class WearServerClipboardRepositoryImplTest {

    private val entry = WearExchangeEntry("ex.example", PORT, "owner", "ZGV2aWNl", null, features = listOf("clipboard"))
    private val client = mockk<WearServerClipboardClient>()
    private val sink = RecordingSink()
    private val network = WearTransferNetworkManager(
        TransferNetworkRequester {
            object : PendingTransferNetwork {
                override suspend fun await(timeoutMs: Long): Network = mockk()

                override fun release() = Unit
            }
        }
    )
    private val repository = WearServerClipboardRepositoryImpl(mockk<Context>(), OneEntryStore(entry), client, network)

    @Test
    fun `a cancelled get discards the sink and commits nothing`() {
        coEvery { client.info(any(), any()) } returns WearServerClipboardInfo.Answered(VALUE)
        coEvery { client.get(any(), any(), any(), any(), any()) } coAnswers {
            arg<suspend (WearServerClipboardValue) -> OutputStream?>(3).invoke(VALUE)
            throw CancellationException("left the screen")
        }

        val outcome = runCatching { runBlocking { repository.receive({ sink }) { _, _ -> } } }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(1, sink.discarded)
        assertEquals(0, sink.committed)
    }

    @Test
    fun `a hash mismatch discards the sink and names the entry`() {
        coEvery { client.info(any(), any()) } returns WearServerClipboardInfo.Answered(VALUE)
        coEvery { client.get(any(), any(), any(), any(), any()) } coAnswers {
            arg<suspend (WearServerClipboardValue) -> OutputStream?>(3).invoke(VALUE)
            WearServerClipboardGet.Failed(WearServerClipboardFailure.HASH_MISMATCH)
        }

        val report = runBlocking { repository.receive({ sink }) { _, _ -> } }

        val failure = (report as WearServerClipboardReceiveReport.Failed).failures.single()
        assertEquals("ex.example:$PORT", failure.label)
        assertEquals(WearServerClipboardFailure.HASH_MISMATCH, failure.failure)
        assertEquals(1, sink.discarded)
        assertEquals(0, sink.committed)
    }

    private class RecordingSink : WearServerClipboardSink {
        var discarded = 0
        var committed = 0
        override val output: OutputStream = ByteArrayOutputStream()

        override fun commit(): WearServerClipboardSaved {
            committed++
            return WearServerClipboardSaved("v", path = "/v", contentUri = null)
        }

        override fun discard() {
            discarded++
        }
    }

    private class OneEntryStore(private val entry: WearExchangeEntry) : WearExchangeEntryStore {
        override val entries: Flow<List<WearExchangeEntry>> = MutableStateFlow(listOf(entry))
        override suspend fun all(): List<WearExchangeEntry> = listOf(entry)
        override suspend fun put(entry: WearExchangeEntry) = Unit
        override suspend fun remove(identity: String) = Unit
        override suspend fun setOutgoing(identity: String) = Unit
        override suspend fun token(identity: String): String = "token"
        override suspend fun saveToken(identity: String, token: String): Boolean = true
        override suspend fun forgetToken(identity: String) = Unit
    }

    private companion object {
        const val PORT = 7443
        val VALUE = WearServerClipboardValue(
            clipId = "id",
            kind = WearServerClipboardKind.TEXT,
            size = 4,
            sha256 = "00",
            storedAt = "2026-10-14T20:00:00Z",
            sourceDeviceId = null,
            sourceDeviceName = null,
            mimeType = null,
            name = null,
        )
    }
}

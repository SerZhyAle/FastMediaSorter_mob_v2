package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardPutResult
import com.sza.fastmediasorter.data.repository.ExchangeClipboardRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure.Cause
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import com.sza.fastmediasorter.domain.model.ExchangeClipboardMeta
import com.sza.fastmediasorter.domain.model.ExchangeClipboardSendOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/** "Send the clipboard to the server" over several entries (contract DEVICE-EXCHANGE 22 item CA). */
class SendClipboardToServerUseCaseTest {

    private val repository = mockk<ExchangeClipboardRepository>()
    private val useCase = SendClipboardToServerUseCase(repository)
    private val meta = ExchangeClipboardMeta(ExchangeClipboardKind.TEXT, PAYLOAD.size.toLong(), null, null)

    @Test
    fun `one stored entry is a success that names the other`() = runTest {
        coEvery { repository.candidates() } returns listOf(HOME, OFFICE)
        coEvery { repository.put(HOME, meta, any(), any()) } returns ExchangeClipboardPutResult.Stored(null, null, null)
        coEvery { repository.put(OFFICE, meta, any(), any()) } throws IOException("down")

        val outcome = useCase(meta, { ByteArrayInputStream(PAYLOAD) }) {}

        val office = ExchangeClipboardEntryFailure("office.example.net:443", Cause.Unreachable)
        assertEquals(ExchangeClipboardSendOutcome.Sent(1, listOf(office)), outcome)
    }

    @Test
    fun `every entry refusing is not sent and each is named`() = runTest {
        coEvery { repository.candidates() } returns listOf(HOME, OFFICE)
        coEvery { repository.put(any(), meta, any(), any()) } returns
            ExchangeClipboardPutResult.Refused("limit-reached")

        val outcome = useCase(meta, { ByteArrayInputStream(PAYLOAD) }) {} as ExchangeClipboardSendOutcome.NotSent

        assertEquals(listOf("home.example.org:8443", "office.example.net:443"), outcome.failures.map { it.entryLabel })
        assertEquals(
            ExchangeClipboardEntryFailure.Cause.Refused("limit-reached"),
            outcome.failures.first().cause,
        )
    }

    @Test
    fun `no participating entry sends nothing`() = runTest {
        coEvery { repository.candidates() } returns emptyList()

        assertEquals(ExchangeClipboardSendOutcome.NoEntry, useCase(meta, { ByteArrayInputStream(PAYLOAD) }) {})
        coVerify(exactly = 0) { repository.put(any(), any(), any(), any()) }
    }

    @Test
    fun `a server that turns out to lack the clipboard is named, not counted`() = runTest {
        coEvery { repository.candidates() } returns listOf(HOME)
        coEvery { repository.put(HOME, meta, any(), any()) } returns ExchangeClipboardPutResult.NotOffered

        val outcome = useCase(meta, { ByteArrayInputStream(PAYLOAD) }) {} as ExchangeClipboardSendOutcome.NotSent

        assertEquals(ExchangeClipboardEntryFailure.Cause.NotOffered, outcome.failures.single().cause)
    }

    private companion object {
        val PAYLOAD = "hello".toByteArray()
        val HOME = DeviceExchangeAccountEntry("home.example.org", 8443, "alice", "AAAAAAAAAAAAAAAAAAAAAA", null)
        val OFFICE = DeviceExchangeAccountEntry("office.example.net", 443, "alice", "BBBBBBBBBBBBBBBBBBBBBB", null)
    }
}

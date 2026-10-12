package com.sza.fastmediasorter.domain.usecase.wear

import com.google.gson.Gson
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeRefusedException
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangePairingCode
import com.sza.fastmediasorter.domain.model.WatchExchangeHandoverOutcome
import com.sza.fastmediasorter.domain.model.WearExchangeHandoverAck
import com.sza.fastmediasorter.domain.model.WearExchangeHandoverCodec
import com.sza.fastmediasorter.domain.model.WearExchangeHandoverParseResult
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearSyncEvents
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

/** S4158: the phone side of the watch hand-over - when the server is asked, who gets the code, what it says. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectWatchToExchangeEntryUseCaseTest {

    private val accounts = mockk<DeviceExchangeAccountRepository>()
    private val dataLayer = mockk<WearableDataLayerRepository>()
    private val gson = Gson()
    private val useCase = ConnectWatchToExchangeEntryUseCase(accounts, dataLayer, gson)

    @Test
    fun `companion off asks the server nothing`() = runTest {
        coEvery { dataLayer.isCompanionEnabled() } returns false

        assertEquals(WatchExchangeHandoverOutcome.CompanionOff, useCase(ENTRY))
        coVerify(exactly = 0) { accounts.issuePairingCode(any()) }
    }

    @Test
    fun `no watch asks the server nothing`() = runTest {
        companion(nodes = emptyList())

        assertEquals(WatchExchangeHandoverOutcome.NoConnectedWatch, useCase(ENTRY))
        coVerify(exactly = 0) { accounts.issuePairingCode(any()) }
    }

    @Test
    fun `a refused code carries the server reason`() = runTest {
        companion()
        coEvery { accounts.issuePairingCode(ENTRY) } throws DeviceExchangeRefusedException("rate-limited")

        assertEquals(WatchExchangeHandoverOutcome.CodeRefused("rate-limited"), useCase(ENTRY))
    }

    @Test
    fun `an unreachable server is told apart from a refusal`() = runTest {
        companion()
        coEvery { accounts.issuePairingCode(ENTRY) } throws IOException("timeout")

        assertEquals(WatchExchangeHandoverOutcome.ServerUnreachable, useCase(ENTRY))
    }

    @Test
    fun `the code goes to the first watch that takes it and its answer ends the wait`() = runTest {
        companion()
        coEvery { accounts.issuePairingCode(ENTRY) } returns DeviceExchangePairingCode(CODE, EXPIRES_AT)
        val sentTo = mutableListOf<String>()
        coEvery { dataLayer.sendMessage(any(), WearDataLayerPaths.EXCHANGE_HANDOVER, any()) } coAnswers {
            sentTo += firstArg<String>()
            val parsed = WearExchangeHandoverCodec.parse(thirdArg(), gson) as WearExchangeHandoverParseResult.Parsed
            assertEquals(CODE, parsed.payload.pairingCode)
            assertEquals(PIN, parsed.payload.pinnedCertificate)
            assertEquals(LOGIN, parsed.payload.login)
            WearSyncEvents.emitExchangeHandoverAck(WearExchangeHandoverAck(parsed.payload.requestId, enrolled = true))
        }

        assertEquals(WatchExchangeHandoverOutcome.Enrolled, useCase(ENTRY))
        assertEquals(listOf("a"), sentTo)
    }

    @Test
    fun `a watch refusal carries its reason`() = runTest {
        companion()
        coEvery { accounts.issuePairingCode(ENTRY) } returns DeviceExchangePairingCode(CODE, EXPIRES_AT)
        coEvery { dataLayer.sendMessage(any(), any(), any()) } coAnswers {
            val parsed = WearExchangeHandoverCodec.parse(thirdArg(), gson) as WearExchangeHandoverParseResult.Parsed
            WearSyncEvents.emitExchangeHandoverAck(
                WearExchangeHandoverAck(parsed.payload.requestId, enrolled = false, reason = "bad-credentials")
            )
        }

        assertEquals(WatchExchangeHandoverOutcome.WatchRefused("bad-credentials"), useCase(ENTRY))
    }

    @Test
    fun `no node taking the message ends at once`() = runTest {
        companion()
        coEvery { accounts.issuePairingCode(ENTRY) } returns DeviceExchangePairingCode(CODE, EXPIRES_AT)
        coEvery { dataLayer.sendMessage(any(), any(), any()) } throws IOException("node gone")

        assertEquals(WatchExchangeHandoverOutcome.NoConnectedWatch, useCase(ENTRY))
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `a silent watch is waited for and then reported`() = runTest {
        companion()
        coEvery { accounts.issuePairingCode(ENTRY) } returns DeviceExchangePairingCode(CODE, EXPIRES_AT)
        coEvery { dataLayer.sendMessage(any(), any(), any()) } returns Unit

        assertEquals(WatchExchangeHandoverOutcome.WatchDidNotAnswer, useCase(ENTRY))
        assertEquals(ACK_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `the payload text never prints the code or the login`() {
        val text = DeviceExchangePairingCode(CODE, EXPIRES_AT).toString()

        assertFalse(text.contains(CODE))
    }

    private fun companion(nodes: List<WearNode> = NODES) {
        coEvery { dataLayer.isCompanionEnabled() } returns true
        coEvery { dataLayer.getConnectedNodes() } returns nodes
    }

    private companion object {
        const val CODE = "7KQ2M9XA"
        const val EXPIRES_AT = "2026-10-11T12:10:00Z"
        const val LOGIN = "owner.home"
        const val PIN = "SHA256:pinned"
        const val ACK_TIMEOUT_MS = 45_000L
        val NODES = listOf(WearNode("a", "Watch A"), WearNode("b", "Watch B"))
        val ENTRY = DeviceExchangeAccountEntry(
            host = "exchange.example.net",
            port = 44_022,
            login = LOGIN,
            deviceId = "AAAAAAAAAAAAAAAAAAAAAA",
            pinnedCertificate = PIN,
        )
    }
}

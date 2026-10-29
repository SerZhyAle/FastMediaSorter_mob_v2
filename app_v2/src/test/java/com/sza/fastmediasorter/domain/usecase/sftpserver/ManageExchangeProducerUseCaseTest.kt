package com.sza.fastmediasorter.domain.usecase.sftpserver

import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollment
import com.sza.fastmediasorter.domain.model.SftpExchangeConfig
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import com.sza.fastmediasorter.domain.repository.SftpServerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageExchangeProducerUseCaseTest {

    private val accounts = mockk<DeviceExchangeAccountRepository>(relaxed = true)
    private val server = mockk<SftpServerRepository>(relaxed = true)
    private val config = MutableStateFlow(exchangeConfig(enabled = false))
    private val tunnel = MutableStateFlow<SftpTunnelState>(SftpTunnelState.Off)
    private val useCase = ManageExchangeProducerUseCase(accounts, server)

    init {
        every { server.exchangeConfig } returns config
        every { server.tunnelState } returns tunnel
    }

    @Test
    fun `publishing without an outgoing entry asks for one and changes nothing`() = runTest {
        coEvery { accounts.outgoingEntry() } returns null

        assertEquals(ExchangePublishStart.NEEDS_OUTGOING, useCase.startPublishing())

        coVerify(exactly = 0) { server.setExchangeEnabled(any()) }
        verify(exactly = 0) { server.applyExchangeSettings() }
    }

    @Test
    fun `publishing with an outgoing entry turns publishing on and registers`() = runTest {
        coEvery { accounts.outgoingEntry() } returns FIRST

        assertEquals(ExchangePublishStart.STARTED, useCase.startPublishing())

        coVerifyOrder {
            server.setExchangeEnabled(true)
            server.applyExchangeSettings()
        }
    }

    @Test
    fun `publishing through a guest outgoing entry asks for another one and changes nothing`() = runTest {
        coEvery { accounts.outgoingEntry() } returns GUEST

        assertEquals(ExchangePublishStart.NEEDS_OUTGOING, useCase.startPublishing())

        coVerify(exactly = 0) { server.setExchangeEnabled(any()) }
        verify(exactly = 0) { server.applyExchangeSettings() }
    }

    @Test
    fun `the publish choice never lists a guest entry`() = runTest {
        every { accounts.entries } returns flowOf(listOf(FIRST, GUEST, SECOND))

        assertEquals(listOf(FIRST, SECOND), useCase.entries.first())
    }

    @Test
    fun `only a guest account may not produce`() {
        assertTrue(FIRST.mayProduce)
        assertTrue(FIRST.copy(accountKind = DeviceExchangeAccountEntry.ACCOUNT_KIND_MEMBER).mayProduce)
        assertFalse(GUEST.mayProduce)
    }

    @Test
    fun `leaving with the server reachable unpublishes and keeps the entry`() = runTest {
        tunnel.value = SftpTunnelState.Registered(null)
        coEvery { server.unpublishExchangeFolder() } returns true

        assertEquals(ExchangeProducerLeave.UNPUBLISHED, useCase.leave())

        coVerify { server.setExchangeEnabled(false) }
        coVerify(exactly = 0) { accounts.remove(any()) }
        coVerify(exactly = 0) { accounts.setOutgoing(any()) }
    }

    @Test
    fun `leaving unpublishes the folder only and never withdraws the broadcast`() = runTest {
        tunnel.value = SftpTunnelState.Registered(null)
        coEvery { server.unpublishExchangeFolder() } returns true

        useCase.leave()

        coVerify(exactly = 1) { server.unpublishExchangeFolder() }
        coVerify(exactly = 0) { server.withdrawExchangePublication() }
    }

    @Test
    fun `leaving with the server down ends locally`() = runTest {
        tunnel.value = SftpTunnelState.Unreachable
        coEvery { server.unpublishExchangeFolder() } returns false

        assertEquals(ExchangeProducerLeave.LEFT_LOCALLY, useCase.leave())

        coVerify { server.setExchangeEnabled(false) }
    }

    @Test
    fun `leaving while nothing ran says so`() = runTest {
        coEvery { server.unpublishExchangeFolder() } returns false

        assertEquals(ExchangeProducerLeave.NOTHING_PUBLISHED, useCase.leave())
    }

    @Test
    fun `another outgoing entry while publishing leaves the first server before publishing again`() = runTest {
        config.value = exchangeConfig(enabled = true)
        tunnel.value = SftpTunnelState.Registered(null)
        coEvery { accounts.outgoingEntry() } returns FIRST
        coEvery { server.withdrawExchangePublication() } returns true

        assertEquals(ExchangeProducerLeave.UNPUBLISHED, useCase.makeOutgoing(SECOND))

        coVerifyOrder {
            server.withdrawExchangePublication()
            accounts.setOutgoing(SECOND)
            server.applyExchangeSettings()
        }
        coVerify(exactly = 0) { server.unpublishExchangeFolder() }
    }

    @Test
    fun `another outgoing entry while not publishing only moves the flag`() = runTest {
        coEvery { accounts.outgoingEntry() } returns FIRST

        assertNull(useCase.makeOutgoing(SECOND))

        coVerify { accounts.setOutgoing(SECOND) }
        coVerify(exactly = 0) { server.withdrawExchangePublication() }
        verify(exactly = 0) { server.applyExchangeSettings() }
    }

    @Test
    fun `the entry already outgoing changes nothing`() = runTest {
        config.value = exchangeConfig(enabled = true)
        coEvery { accounts.outgoingEntry() } returns FIRST

        assertNull(useCase.makeOutgoing(FIRST.copy(login = FIRST.login.uppercase())))

        coVerify(exactly = 0) { accounts.setOutgoing(any()) }
        coVerify(exactly = 0) { server.withdrawExchangePublication() }
    }

    @Test
    fun `declared roles cover a later start of publishing`() {
        assertEquals(
            listOf("resource-consumer", "resource-producer", "broadcaster"),
            DeviceExchangeEnrollment.declaredRoles(producer = true, broadcaster = true),
        )
        assertEquals(
            listOf("resource-consumer"),
            DeviceExchangeEnrollment.declaredRoles(producer = false, broadcaster = false),
        )
    }

    private fun exchangeConfig(enabled: Boolean) = SftpExchangeConfig(enabled = enabled, shareId = "")

    private companion object {
        val FIRST = DeviceExchangeAccountEntry(
            host = "exchange.example.org",
            port = 8443,
            login = "alice",
            deviceId = "AAAAAAAAAAAAAAAAAAAAAA",
            pinnedCertificate = null,
            outgoing = true,
        )
        val SECOND = FIRST.copy(host = "other.example.net", deviceId = "BBBBBBBBBBBBBBBBBBBBBB", outgoing = false)
        val GUEST = FIRST.copy(
            host = "guest.example.net",
            deviceId = "CCCCCCCCCCCCCCCCCCCCCC",
            accountKind = DeviceExchangeAccountEntry.ACCOUNT_KIND_GUEST,
        )
    }
}

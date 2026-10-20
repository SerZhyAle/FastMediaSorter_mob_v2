package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastGrants
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeRelayKeys
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.data.repository.settings.ExchangeGrantRepository
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastGrant
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageBroadcastGuestsUseCaseTest {

    private val credentials = BroadcastCredentialRegistry(Dispatchers.Unconfined)
    private val keys = ExchangeRelayKeys()
    private val grants = ExchangeBroadcastGrants(credentials, keys)
    private val announcer = mockk<ExchangeBroadcastAnnouncer>()
    private val store = mockk<ExchangeGrantRepository>(relaxed = true)
    private val tunnel = mockk<SftpServerTunnelManager>(relaxed = true)
    private val useCase = ManageBroadcastGuestsUseCase(grants, announcer, store, tunnel, Dispatchers.Unconfined)

    init {
        credentials.begin(BROADCAST)
        keys.begin(BROADCAST)
        every { announcer.announcedBroadcastId } returns BROADCAST
        every { announcer.announcedSourceKey } returns SOURCE
    }

    @Test
    fun `a blank or malformed login is refused`() = runTest {
        assertFalse(useCase.give("   "))
        assertFalse(useCase.give("a b"))
        assertTrue(useCase.grants.value.isEmpty())
        verify(exactly = 0) { tunnel.resendGrants() }
    }

    @Test
    fun `give adds a pending grant, keeps the guest and sends the grants`() = runTest {
        assertTrue(useCase.give(" bob "))

        val grant = useCase.grants.value.single()
        assertEquals("bob", grant.guest)
        assertEquals(BROADCAST, grant.broadcastId)
        assertEquals(ExchangeBroadcastGrant.State.PENDING, grant.state)
        coVerify { store.keepBroadcastGuest(SOURCE, "bob") }
        verify { tunnel.resendGrants() }
    }

    @Test
    fun `nothing is given without an announced broadcast`() = runTest {
        every { announcer.announcedBroadcastId } returns null

        assertFalse(useCase.give("bob"))
        assertTrue(useCase.grants.value.isEmpty())
    }

    @Test
    fun `withdraw removes the grant, forgets the guest and sends ungrant`() = runTest {
        useCase.give("bob")
        val grantId = useCase.grants.value.single().grantId

        useCase.withdraw(grantId)

        assertTrue(useCase.grants.value.isEmpty())
        coVerify { store.forgetBroadcastGuest(SOURCE, "bob") }
        verify { tunnel.sendUngrant(grantId) }
    }

    private companion object {
        const val BROADCAST = "EEEEEEEEEEEEEEEEEEEEEA"
        const val SOURCE = "AUDIO_ONLY"
    }
}

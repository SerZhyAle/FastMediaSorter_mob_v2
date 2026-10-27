package com.sza.fastmediasorter.domain.usecase.sftpserver

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpExchangeProducerSource
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerController
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerIdentityStore
import com.sza.fastmediasorter.data.repository.settings.ExchangeGrantRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerClientCredentials
import com.sza.fastmediasorter.domain.model.SftpServerState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageExchangeGrantsUseCaseTest {

    private val store = ExchangeGrantRepository(
        dataStore = InMemoryPreferencesStore(),
        encrypt = { "sealed:$it" },
        decrypt = { it?.removePrefix("sealed:") },
        ioDispatcher = Dispatchers.Unconfined,
    )
    private val server = mockk<SftpServerController>(relaxed = true)
    private val identity = mockk<SftpServerIdentityStore>()
    private val tunnel = mockk<SftpServerTunnelManager>(relaxed = true)
    private val producer = mockk<SftpExchangeProducerSource>()
    private val useCase = ManageExchangeGrantsUseCase(store, server, identity, tunnel, producer, Dispatchers.Unconfined)

    init {
        every { server.state } returns MutableStateFlow(SftpServerState.Running(2222, listOf("192.168.1.5")))
        coEvery { identity.clientCredentials() } returns
            SftpServerClientCredentials("fms", SftpServerAuthMode.PASSWORD, "install-secret", FINGERPRINT)
        coEvery { producer.outgoingEntry() } returns
            DeviceExchangeAccountEntry("exchange.example", 7443, "owner", "device", null)
    }

    @Test
    fun `a link code carries the link login, its own share id and no password`() = runTest {
        val created = checkNotNull(useCase.createLink(ROOT, "Photos", "correct horse"))

        val code = checkNotNull(SftpPairingPayload.decode(created.code))
        assertNull(code.password)
        assertEquals(created.grant.login, code.username)
        assertEquals(created.grant.linkShareId, code.shareId)
        assertEquals("exchange.example:7443", code.exchangeEndpoint)
        assertEquals("correct horse", created.password)
        assertEquals("correct horse", store.password(created.grant.login))
        coVerify { server.refreshGrantLogins() }
        verify { tunnel.resendGrants() }
    }

    @Test
    fun `a typed password shorter than eight characters is replaced by a generated one`() = runTest {
        val created = checkNotNull(useCase.createLink(ROOT, "Photos", "short"))

        assertEquals(24, created.password.length)
    }

    @Test
    fun `no running share makes no link`() = runTest {
        every { server.state } returns MutableStateFlow(SftpServerState.Stopped)

        assertNull(useCase.createLink(ROOT, "Photos", null))
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun `revoke removes the grant, ends its sessions and tells the server`() = runTest {
        val guest = checkNotNull(useCase.grantGuest(ROOT, "Photos", "anna"))
        val sent = slot<DeviceExchangeEnvelope>()
        every { tunnel.sendControl(capture(sent)) } returns true
        every { server.closeSessionsOf(any()) } just Runs

        useCase.revoke(guest.grantId)

        assertTrue(store.snapshot().isEmpty())
        assertNull(store.password(guest.login))
        verify { server.closeSessionsOf(guest.login) }
        assertEquals(DeviceExchangeEnvelope.TYPE_UNGRANT, sent.captured.type)
        assertEquals(guest.grantId, sent.captured.string(DeviceExchangeEnvelope.KEY_GRANT_ID))
    }

    @Test
    fun `grants are listed per folder`() = runTest {
        assertNotNull(useCase.grantGuest(ROOT, "Photos", "anna"))
        assertNotNull(useCase.grantGuest("content://tree/music", "Music", "bob"))

        val forPhotos = store.snapshot().filter { it.rootUri == ROOT }
        assertEquals(listOf("anna"), forPhotos.map { it.guest })
    }

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private companion object {
        const val ROOT = "content://tree/photos"
        const val FINGERPRINT = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
    }
}

package com.sza.fastmediasorter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeTokenStore
import com.sza.fastmediasorter.data.remote.exchange.ExchangeResourcePinStore
import com.sza.fastmediasorter.data.repository.settings.DeviceExchangeAccountStore
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeAddressChange
import com.sza.fastmediasorter.domain.model.DeviceExchangeCredential
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollResult
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollment
import com.sza.fastmediasorter.domain.model.DeviceExchangeRemoval
import com.sza.fastmediasorter.domain.model.DeviceExchangeWelcome
import com.sza.fastmediasorter.domain.model.ExchangeEndpoint
import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DeviceExchangeAccountRepositoryTest {

    private val store = DeviceExchangeAccountStore(InMemoryPreferencesStore())
    private val tokens = MemoryTokens()
    private val client = mockk<DeviceExchangeAccountClient>()
    private val pins = mockk<ExchangeResourcePinStore>(relaxed = true)
    private val resourceDao = mockk<ResourceDao>(relaxed = true)
    private val enrollment = DeviceExchangeEnrollment("Pixel", "2.6.0", listOf("resource-consumer"))
    private val password = DeviceExchangeCredential.Password("secret-value")

    private fun TestScope.repository() =
        DeviceExchangeAccountRepository(client, store, tokens, pins, resourceDao, StandardTestDispatcher(testScheduler))

    @Test
    fun `re-enrolling the same identity keeps the deviceId and the entry count`() = runTest {
        enrollAnswers(enrolled())
        val repository = repository()

        repository.enroll(SERVER_A, LOGIN, null, password, enrollment)
        val first = store.snapshot().single()
        repository.enroll(SERVER_A, LOGIN.uppercase(), null, password, enrollment)

        val held = store.snapshot().single()
        assertEquals(first.deviceId, held.deviceId)
        assertEquals(ANNOUNCED, held.publicEndpoint)
    }

    @Test
    fun `re-enrolling the outgoing entry keeps it outgoing`() = runTest {
        enrollAnswers(enrolled())
        val repository = repository()
        repository.enroll(SERVER_A, LOGIN, null, password, enrollment)
        repository.enroll(SERVER_A, OTHER_LOGIN, null, password, enrollment)
        repository.setOutgoing(store.snapshot().first())

        repository.enroll(SERVER_A, LOGIN, null, password, enrollment)

        assertEquals(LOGIN, repository.outgoingEntry()?.login)
        assertEquals(listOf(true, false), store.snapshot().map { it.outgoing })
    }

    @Test
    fun `two logins on one server and one elsewhere get three deviceIds`() = runTest {
        enrollAnswers(enrolled())
        val repository = repository()

        repository.enroll(SERVER_A, LOGIN, null, password, enrollment)
        repository.enroll(SERVER_A, OTHER_LOGIN, null, password, enrollment)
        repository.enroll(SERVER_B, LOGIN, null, password, enrollment)

        assertEquals(3, store.snapshot().map { it.deviceId }.toSet().size)
    }

    @Test
    fun `an id collision on a fresh id regenerates it once`() = runTest {
        val sent = mutableListOf<String>()
        coEvery { client.enroll(any(), any(), any()) } answers {
            sent += firstArg<DeviceExchangeAccountEntry>().deviceId
            if (sent.size == 1) refused(DeviceExchangeEnvelope.REASON_ID_COLLISION) else enrolled()
        }

        val result = repository().enroll(SERVER_A, LOGIN, null, password, enrollment)

        assertTrue(result is DeviceExchangeEnrollResult.Enrolled)
        assertNotEquals(sent[0], sent[1])
        assertEquals(sent[1], store.snapshot().single().deviceId)
    }

    @Test
    fun `a refused enrollment records nothing`() = runTest {
        enrollAnswers(refused(DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS))

        repository().enroll(SERVER_A, LOGIN, null, password, enrollment)

        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun `an address change keeps the deviceId, the token and the attachments`() = runTest {
        val entry = held(SERVER_A)
        tokens.save(entry, TOKEN)

        val outcome = repository().changeAddress(entry, SERVER_B)

        val moved = (outcome as DeviceExchangeAddressChange.Changed).entry
        assertEquals(entry.deviceId, moved.deviceId)
        assertEquals(TOKEN, tokens.token(moved))
        assertNull(tokens.token(entry))
        assertEquals(listOf(moved), store.snapshot())
        coVerify { resourceDao.rebindExchangeAccount(entry.identity, moved.identity) }
    }

    @Test
    fun `an address change carries the PIN of every bound resource to the new identity`() = runTest {
        val entry = held(SERVER_A)
        tokens.save(entry, TOKEN)
        coEvery { resourceDao.getAllResourcesSync() } returns listOf(
            bound(entry.identity, "r1"),
            bound(entry.identity, "r2"),
            bound("elsewhere:1/someone", "r3"),
        )

        val moved = (repository().changeAddress(entry, SERVER_B) as DeviceExchangeAddressChange.Changed).entry

        coVerify { pins.move(entry.identity, moved.identity, listOf("r1", "r2")) }
    }

    @Test
    fun `an address held by another entry of the same login is refused`() = runTest {
        val entry = held(SERVER_A)
        held(SERVER_B, deviceId = OTHER_DEVICE)

        val outcome = repository().changeAddress(entry, SERVER_B)

        assertEquals(DeviceExchangeAddressChange.IdentityTaken, outcome)
        assertEquals(2, store.snapshot().size)
    }

    @Test
    fun `removal with the server down forgets locally and keeps resources`() = runTest {
        val entry = held(SERVER_A)
        tokens.save(entry, TOKEN)
        coEvery { client.revokeOwnDevice(any()) } throws IOException("unreachable")

        val removal = repository().remove(entry)

        assertEquals(DeviceExchangeRemoval.FORGOTTEN_LOCALLY, removal)
        assertNull(tokens.token(entry))
        assertTrue(store.snapshot().isEmpty())
        coVerify(exactly = 0) { resourceDao.rebindExchangeAccount(any(), any()) }
    }

    @Test
    fun `removal with the server up revokes the entry's own device`() = runTest {
        val entry = held(SERVER_A)
        coEvery { client.revokeOwnDevice(entry) } returns Unit

        assertEquals(DeviceExchangeRemoval.REVOKED, repository().remove(entry))
    }

    @Test
    fun `the announced address is offered only when it differs`() = runTest {
        val repository = repository()
        val entry = held(SERVER_A)

        assertNull(repository.announcedEndpoint(entry.copy(publicEndpoint = SERVER_A.toString())))
        assertEquals(SERVER_B, repository.announcedEndpoint(entry.copy(publicEndpoint = SERVER_B.toString())))
    }

    @Test
    fun `the connection string carries the announced address and the pin, never the login`() = runTest {
        val entry = held(SERVER_A).copy(pinnedCertificate = PIN, publicEndpoint = ANNOUNCED)

        val text = checkNotNull(repository().connectionString(entry))

        assertEquals(ANNOUNCED, text.endpoint.toString())
        assertEquals(PIN, text.fingerprint)
        assertNull(text.loginHint)
    }

    private fun bound(account: String, resourceId: String) = ResourceEntity(
        id = 1L,
        name = "R",
        path = "sftp://192.168.1.5:2222/Private",
        type = ResourceType.SFTP,
        exchangeAccount = account,
        exchangeResourceId = resourceId,
    )

    private suspend fun held(endpoint: ExchangeEndpoint, deviceId: String = DEVICE) =
        DeviceExchangeAccountEntry(endpoint.host, endpoint.port, LOGIN, deviceId, null).also { store.put(it) }

    private fun enrollAnswers(result: DeviceExchangeEnrollResult) {
        coEvery { client.enroll(any(), any(), any()) } returns result
    }

    private fun enrolled() = DeviceExchangeEnrollResult.Enrolled(
        DeviceExchangeWelcome(LOGIN, ANNOUNCED, null, emptyList(), null)
    )

    private fun refused(reason: String) = DeviceExchangeEnrollResult.Refused(reason)

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class MemoryTokens : DeviceExchangeTokenStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun token(entry: DeviceExchangeAccountEntry): String? = values[entry.identity]

        override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean {
            values[entry.identity] = token
            return true
        }

        override suspend fun forget(entry: DeviceExchangeAccountEntry) {
            values.remove(entry.identity)
        }
    }

    private companion object {
        val SERVER_A = ExchangeEndpoint("exchange.example.org", 8443)
        val SERVER_B = ExchangeEndpoint("other.example.net", 44022)
        const val ANNOUNCED = "public.example.org:443"
        const val LOGIN = "alice"
        const val OTHER_LOGIN = "bob.family"
        const val DEVICE = "AAAAAAAAAAAAAAAAAAAAAA"
        const val OTHER_DEVICE = "BBBBBBBBBBBBBBBBBBBBBB"
        const val TOKEN = "device-token"
        const val PIN = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
    }
}

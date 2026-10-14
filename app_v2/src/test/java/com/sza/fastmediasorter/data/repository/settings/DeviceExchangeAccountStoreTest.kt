package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceExchangeAccountStoreTest {

    private val dataStore = InMemoryPreferencesStore()
    private val store = DeviceExchangeAccountStore(dataStore)

    @Test
    fun `two logins on one server and a third elsewhere are three entries`() = runTest {
        store.put(entry(HOST_A, LOGIN_ONE, DEVICE_1))
        store.put(entry(HOST_A, LOGIN_TWO, DEVICE_2))
        store.put(entry(HOST_B, LOGIN_ONE, DEVICE_3))

        val held = store.snapshot()

        assertEquals(listOf(DEVICE_1, DEVICE_2, DEVICE_3), held.map { it.deviceId })
        assertEquals(3, held.map { it.identity }.toSet().size)
    }

    @Test
    fun `the same identity in another case replaces the entry in place`() = runTest {
        store.put(entry(HOST_A, LOGIN_ONE, DEVICE_1))
        store.put(entry(HOST_B, LOGIN_ONE, DEVICE_3))

        store.put(entry(HOST_A.uppercase(), LOGIN_ONE.uppercase(), DEVICE_1, autoAttach = true))

        val held = store.snapshot()
        assertEquals(listOf(DEVICE_1, DEVICE_3), held.map { it.deviceId })
        assertEquals(true, held.first().autoAttach)
    }

    @Test
    fun `every field survives the round trip`() = runTest {
        val full = entry(HOST_A, LOGIN_ONE, DEVICE_1, autoAttach = true).copy(
            pinnedCertificate = PIN,
            publicEndpoint = "exchange.example.org:8443",
            outgoing = true,
            pinCapable = true,
        )
        store.put(full)

        assertEquals(full, DeviceExchangeAccountStore(dataStore).entry(full.identity))
    }

    @Test
    fun `an entry stored before the capability existed reads as not capable`() = runTest {
        store.put(entry(HOST_A, LOGIN_ONE, DEVICE_1))

        val held = store.entry(entry(HOST_A, LOGIN_ONE, DEVICE_1).identity)

        assertEquals(false, held?.pinCapable)
    }

    @Test
    fun `replace keeps the position and drops a holder of the new identity`() = runTest {
        val first = entry(HOST_A, LOGIN_ONE, DEVICE_1)
        store.put(first)
        store.put(entry(HOST_B, LOGIN_ONE, DEVICE_3))
        store.put(entry(HOST_C, LOGIN_ONE, DEVICE_2))

        store.replace(first.identity, first.copy(host = HOST_C))

        assertEquals(listOf(DEVICE_1, DEVICE_3), store.snapshot().map { it.deviceId })
        assertEquals(HOST_C, store.snapshot().first().host)
    }

    @Test
    fun `remove forgets only that identity`() = runTest {
        val gone = entry(HOST_A, LOGIN_ONE, DEVICE_1)
        store.put(gone)
        store.put(entry(HOST_A, LOGIN_TWO, DEVICE_2))

        store.remove(gone.identity)

        assertNull(store.entry(gone.identity))
        assertEquals(listOf(DEVICE_2), store.snapshot().map { it.deviceId })
    }

    @Test
    fun `an unreadable element is dropped and the rest stays`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey(ENTRIES_KEY)] =
                """[{"host":"a"},{"host":"$HOST_A","port":8443,"login":"$LOGIN_ONE","deviceId":"$DEVICE_1"}]"""
        }

        assertEquals(listOf(DEVICE_1), store.snapshot().map { it.deviceId })
    }

    @Test
    fun `the outgoing flag sits on one entry however often it moves`() = runTest {
        store.put(entry(HOST_A, LOGIN_ONE, DEVICE_1))
        store.put(entry(HOST_A, LOGIN_TWO, DEVICE_2))
        store.put(entry(HOST_B, LOGIN_ONE, DEVICE_3))

        store.setOutgoing(store.snapshot()[0].identity)
        store.setOutgoing(store.snapshot()[2].identity)

        assertEquals(listOf(false, false, true), store.snapshot().map { it.outgoing })
        assertEquals(DEVICE_3, store.outgoing()?.deviceId)
    }

    @Test
    fun `writing an outgoing entry takes the flag from the others`() = runTest {
        store.put(entry(HOST_A, LOGIN_ONE, DEVICE_1))
        store.setOutgoing(store.snapshot().single().identity)

        store.put(entry(HOST_B, LOGIN_ONE, DEVICE_3).copy(outgoing = true))

        assertEquals(listOf(false, true), store.snapshot().map { it.outgoing })
    }

    @Test
    fun `clearing the outgoing flag keeps every entry`() = runTest {
        store.put(entry(HOST_A, LOGIN_ONE, DEVICE_1))
        store.setOutgoing(store.snapshot().single().identity)

        store.setOutgoing(null)

        assertNull(store.outgoing())
        assertEquals(listOf(DEVICE_1), store.snapshot().map { it.deviceId })
    }

    @Test
    fun `a stored list with two outgoing entries reads the first only`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey(ENTRIES_KEY)] =
                """[{"host":"$HOST_A","port":8443,"login":"$LOGIN_ONE","deviceId":"$DEVICE_1","outgoing":true},""" +
                """{"host":"$HOST_B","port":8443,"login":"$LOGIN_ONE","deviceId":"$DEVICE_3","outgoing":true}]"""
        }

        assertEquals(listOf(true, false), store.snapshot().map { it.outgoing })
    }

    @Test
    fun `an unparsable value reads as no entries`() = runTest {
        dataStore.edit { it[stringPreferencesKey(ENTRIES_KEY)] = "[{" }

        assertEquals(emptyList<DeviceExchangeAccountEntry>(), store.snapshot())
    }

    private fun entry(host: String, login: String, deviceId: String, autoAttach: Boolean = false) =
        DeviceExchangeAccountEntry(
            host = host,
            port = PORT,
            login = login,
            deviceId = deviceId,
            pinnedCertificate = null,
            autoAttach = autoAttach,
        )

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private companion object {
        const val ENTRIES_KEY = "device_exchange_account_entries"
        const val HOST_A = "exchange.example.org"
        const val HOST_B = "other.example.net"
        const val HOST_C = "third.example.com"
        const val LOGIN_ONE = "alice"
        const val LOGIN_TWO = "bob.family"
        const val PORT = 8443
        const val DEVICE_1 = "AAAAAAAAAAAAAAAAAAAAAA"
        const val DEVICE_2 = "BBBBBBBBBBBBBBBBBBBBBB"
        const val DEVICE_3 = "CCCCCCCCCCCCCCCCCCCCCC"
        const val PIN = "SHA256:47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU"
    }
}

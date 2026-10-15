package com.sza.fastmediasorter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.data.repository.settings.DeviceExchangeAccountStore
import com.sza.fastmediasorter.domain.model.BackupExchangeAccount
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4238: exchange account entries leave the raw loop and come back without a token or the old device id. */
class RawSettingsRepositoryImplTest {

    private val dataStore = InMemoryPreferencesStore()
    private val accounts = DeviceExchangeAccountStore(dataStore)
    private val repository = RawSettingsRepositoryImpl(dataStore, accounts)

    @Test
    fun `raw export carries neither the account entries nor a token or a PIN`() = runTest {
        accounts.put(entry(LOGIN_ONE, DEVICE_1))
        dataStore.edit {
            it[stringPreferencesKey("device_exchange_token_0123456789abcdef")] = "cipher"
            it[stringPreferencesKey("exchange_resource_pin_0123456789abcdef_fedcba9876543210")] = "cipher"
            it[stringPreferencesKey("exchange_grant_password_guest.one")] = "cipher"
            it[stringPreferencesKey("exchange_grants_v1")] = "[]"
            it[stringPreferencesKey("theme_name")] = "dark"
        }

        val exported = repository.exportAll().map { it.key }

        assertEquals(listOf("theme_name"), exported)
    }

    @Test
    fun `an entry exports as an address a login and a pin without the device id`() = runTest {
        accounts.put(entry(LOGIN_ONE, DEVICE_1).copy(pinnedCertificate = PIN, autoAttach = true, outgoing = true))

        val exported = repository.exportExchangeAccounts().single()

        assertEquals(
            BackupExchangeAccount(HOST, PORT, LOGIN_ONE, PIN, autoAttach = true, outgoing = true),
            exported
        )
    }

    @Test
    fun `a restored entry takes a fresh device id and waits for sign-in`() = runTest {
        val restored = repository.importExchangeAccounts(
            listOf(BackupExchangeAccount(HOST, PORT, LOGIN_ONE, PIN, autoAttach = true, outgoing = true))
        )

        val held = accounts.snapshot().single()
        assertEquals(1, restored)
        assertEquals(LOGIN_ONE, held.login)
        assertEquals(PIN, held.pinnedCertificate)
        assertTrue(held.deviceId.isNotBlank())
        assertTrue(held.autoAttach)
        assertTrue(held.outgoing)
    }

    @Test
    fun `a restored entry never carries the device id of the phone it came from`() = runTest {
        accounts.put(entry(LOGIN_ONE, DEVICE_1))
        val exported = repository.exportExchangeAccounts()
        accounts.remove(accounts.snapshot().single().identity)

        repository.importExchangeAccounts(exported)

        assertNotEquals(DEVICE_1, accounts.snapshot().single().deviceId)
    }

    @Test
    fun `an identity the device already holds is left alone`() = runTest {
        accounts.put(entry(LOGIN_ONE, DEVICE_1))

        val restored = repository.importExchangeAccounts(
            listOf(BackupExchangeAccount(HOST, PORT, LOGIN_ONE.uppercase(), PIN))
        )

        val held = accounts.snapshot().single()
        assertEquals(0, restored)
        assertEquals(DEVICE_1, held.deviceId)
    }

    @Test
    fun `a restored entry never takes the outgoing role from the entry already chosen`() = runTest {
        accounts.put(entry(LOGIN_ONE, DEVICE_1).copy(outgoing = true))

        repository.importExchangeAccounts(
            listOf(BackupExchangeAccount(HOST, PORT, LOGIN_TWO, null, outgoing = true))
        )

        val byLogin = accounts.snapshot().associateBy { it.login }
        assertTrue(byLogin.getValue(LOGIN_ONE).outgoing)
        assertFalse(byLogin.getValue(LOGIN_TWO).outgoing)
    }

    @Test
    fun `an unusable record is skipped and the next one still restores`() = runTest {
        val restored = repository.importExchangeAccounts(
            listOf(
                BackupExchangeAccount("", PORT, LOGIN_ONE),
                BackupExchangeAccount(HOST, 0, LOGIN_ONE),
                BackupExchangeAccount(HOST, PORT, "no"),
                BackupExchangeAccount(HOST, PORT, LOGIN_TWO)
            )
        )

        assertEquals(1, restored)
        assertEquals(listOf(LOGIN_TWO), accounts.snapshot().map { it.login })
    }

    private fun entry(login: String, deviceId: String) = DeviceExchangeAccountEntry(
        host = HOST,
        port = PORT,
        login = login,
        deviceId = deviceId,
        pinnedCertificate = null,
    )

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private companion object {
        const val HOST = "exchange.example.org"
        const val PORT = 8443
        const val LOGIN_ONE = "alice"
        const val LOGIN_TWO = "bob.family"
        const val DEVICE_1 = "AAAAAAAAAAAAAAAAAAAAAA"
        const val PIN = "SHA256:47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU"
    }
}

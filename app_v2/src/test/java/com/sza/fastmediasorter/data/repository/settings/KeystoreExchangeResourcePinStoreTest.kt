package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystoreExchangeResourcePinStoreTest {

    private val preferences = InMemoryPreferencesStore()
    private var refuseWrites = false
    private val store = newStore()

    private fun newStore() = KeystoreExchangeResourcePinStore(
        dataStore = preferences,
        encrypt = { plain -> if (refuseWrites) null else "sealed:" + plain.reversed() },
        decrypt = { sealed -> sealed?.removePrefix("sealed:")?.reversed() },
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `a saved PIN is read back, also by a store that starts from the same preferences`() = runTest {
        assertTrue(store.save(BINDING, PIN))

        assertEquals(PIN, store.pin(BINDING))
        assertEquals(PIN, newStore().pin(BINDING))
    }

    @Test
    fun `a binding that holds no PIN answers null and stays unprotected`() = runTest {
        assertNull(store.pin(BINDING))
        assertNull(store.pin(ExchangeBinding(BINDING.sourceKey, "other-resource")))
    }

    @Test
    fun `the preferences hold neither the PIN nor the identity nor the resource id`() = runTest {
        store.save(BINDING, PIN)

        val dump = preferences.current().asMap().entries.joinToString { "${it.key.name}=${it.value}" }

        assertFalse(dump.contains(PIN))
        assertFalse(dump.contains(BINDING.sourceKey))
        assertFalse(dump.contains(BINDING.resourceId))
        assertTrue(dump.contains("exchange_resource_pin_"))
    }

    @Test
    fun `forget removes the PIN for this store and for a fresh one`() = runTest {
        store.save(BINDING, PIN)

        store.forget(BINDING)

        assertNull(store.pin(BINDING))
        assertNull(newStore().pin(BINDING))
    }

    @Test
    fun `a refused write reports false and keeps nothing`() = runTest {
        refuseWrites = true

        assertFalse(store.save(BINDING, PIN))

        assertNull(store.pin(BINDING))
    }

    @Test
    fun `move carries only the named resources to the new identity`() = runTest {
        val kept = ExchangeBinding(BINDING.sourceKey, "r2")
        store.save(BINDING, PIN)
        store.save(kept, OTHER_PIN)

        store.move(BINDING.sourceKey, NEW_SOURCE, listOf(BINDING.resourceId))

        assertEquals(PIN, store.pin(ExchangeBinding(NEW_SOURCE, BINDING.resourceId)))
        assertNull(store.pin(BINDING))
        assertEquals(OTHER_PIN, store.pin(kept))
    }

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        fun current(): Preferences = state.value

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private companion object {
        val BINDING = ExchangeBinding("exchange.example.org:8443/alice", "d5sTbSIZ1diWe7u7mtCesw")
        const val NEW_SOURCE = "other.example.net:44022/alice"
        const val PIN = "4711-secret"
        const val OTHER_PIN = "0042"
    }
}

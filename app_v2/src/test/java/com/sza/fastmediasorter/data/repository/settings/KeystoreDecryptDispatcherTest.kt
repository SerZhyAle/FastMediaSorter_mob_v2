package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ExchangeGrants
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore.StoredPair
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * A Keystore decrypt is a binder call plus AES-GCM that runs in the caller's context, so each store hops to the
 * injected IO dispatcher for it. The seam records the thread it was called on; a store that decrypted in the
 * caller's context would record the test thread instead.
 */
class KeystoreDecryptDispatcherTest {

    private val preferences = InMemoryPreferencesStore()
    private val threads = CopyOnWriteArrayList<String>()
    private val decryptDispatcher = Executors
        .newSingleThreadExecutor { Thread(it, DECRYPT_THREAD) }
        .asCoroutineDispatcher()

    @After
    fun tearDown() {
        decryptDispatcher.close()
    }

    private val plainEncrypt: (String) -> String? = { it }

    private val plainDecrypt: (String?) -> String? = { it }

    // Debug-mode coroutines append " @coroutine#N" to the thread name, so only the prefix is the pool's.
    private val recordingDecrypt: (String?) -> String? = { stored ->
        threads += Thread.currentThread().name.substringBefore(COROUTINE_SUFFIX)
        stored
    }

    @Test
    fun `the resource PIN store decrypts on the injected dispatcher and a cache hit costs no hop`() = runTest {
        pinStore(plainDecrypt, Dispatchers.Unconfined).save(BINDING, PIN)
        val store = pinStore(recordingDecrypt, decryptDispatcher)

        assertEquals(PIN, store.pin(BINDING))
        assertEquals(PIN, store.pin(BINDING))

        assertEquals(listOf(DECRYPT_THREAD), threads.toList())
    }

    @Test
    fun `the link password store decrypts on the injected dispatcher`() = runTest {
        linkPasswordStore(plainDecrypt, Dispatchers.Unconfined).put(PASSWORD)
        val store = linkPasswordStore(recordingDecrypt, decryptDispatcher)

        assertEquals(PASSWORD, store.get())

        assertEquals(listOf(DECRYPT_THREAD), threads.toList())
    }

    @Test
    fun `the broadcast password store decrypts on the injected dispatcher`() = runTest {
        broadcastPasswordStore(plainDecrypt, Dispatchers.Unconfined).put(STREAM_ID, USER, PASSWORD)
        val store = broadcastPasswordStore(recordingDecrypt, decryptDispatcher)

        assertEquals(StoredPair(USER, PASSWORD), store.get(STREAM_ID))

        assertEquals(listOf(DECRYPT_THREAD), threads.toList())
    }

    @Test
    fun `the grant password is decrypted on the injected dispatcher`() = runTest {
        val grant = ExchangeGrants.newGuest(ROOT, "Photos", "anna")
        grantRepository(plainDecrypt, Dispatchers.Unconfined).add(grant, PASSWORD)
        val repository = grantRepository(recordingDecrypt, decryptDispatcher)

        assertEquals(PASSWORD, repository.password(grant.login))

        assertEquals(listOf(DECRYPT_THREAD), threads.toList())
    }

    private fun pinStore(decrypt: (String?) -> String?, ioDispatcher: CoroutineDispatcher) =
        KeystoreExchangeResourcePinStore(preferences, plainEncrypt, decrypt, ioDispatcher)

    private fun linkPasswordStore(decrypt: (String?) -> String?, ioDispatcher: CoroutineDispatcher) =
        KeystoreBroadcastLinkPasswordStore(preferences, plainEncrypt, decrypt, ioDispatcher)

    private fun broadcastPasswordStore(decrypt: (String?) -> String?, ioDispatcher: CoroutineDispatcher) =
        KeystoreBroadcastPasswordStore(preferences, plainEncrypt, decrypt, ioDispatcher)

    private fun grantRepository(decrypt: (String?) -> String?, ioDispatcher: CoroutineDispatcher) =
        ExchangeGrantRepository(preferences, plainEncrypt, decrypt, ioDispatcher)

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private companion object {
        const val DECRYPT_THREAD = "keystore-decrypt-test"
        const val COROUTINE_SUFFIX = " @coroutine"
        const val PIN = "4711-secret"
        const val PASSWORD = "correct horse"
        const val STREAM_ID = "stream-1"
        const val USER = "listener"
        const val ROOT = "content://tree/photos"
        val BINDING = ExchangeBinding("exchange.example.org:8443/alice", "d5sTbSIZ1diWe7u7mtCesw")
    }
}

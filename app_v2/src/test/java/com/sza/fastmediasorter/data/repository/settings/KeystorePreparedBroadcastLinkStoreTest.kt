package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.model.PreparedLinkChoice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class KeystorePreparedBroadcastLinkStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val decryptDispatcher = Executors
        .newSingleThreadExecutor { Thread(it, DECRYPT_THREAD) }
        .asCoroutineDispatcher()
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        // Okio storage: File.renameTo cannot replace an existing destination on Windows (S1449).
        dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve("prepared.preferences_pb").toOkioPath()
            },
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        decryptDispatcher.close()
    }

    private fun store(
        decrypt: (String?) -> String? = { it },
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = KeystorePreparedBroadcastLinkStore(dataStore, { it }, decrypt, ioDispatcher)

    @Test
    fun `a put record is read back and a second put with the same id replaces it`() = runTest {
        val store = store()
        store.put(link("b1"))
        assertEquals(listOf(link("b1")), store.all())

        store.put(link("b1").copy(title = "Renamed"))
        assertEquals(listOf("Renamed"), store.all().map { it.title })
    }

    @Test
    fun `a consumed record stays with its secrets cleared and remove drops it`() = runTest {
        val store = store()
        store.put(link("b1"))

        store.markConsumed("b1")
        val consumed = store.all().single()
        assertTrue(consumed.consumed)
        assertNull(consumed.linkPassword)
        assertNull(consumed.relayLinkKey)
        assertNull(consumed.tunnelPin)
        assertTrue(consumed.guests.isEmpty())

        store.remove("b1")
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `a value that does not decrypt reads as no record`() = runTest {
        store().put(link("b1"))

        assertTrue(store(decrypt = { null }).all().isEmpty())
    }

    @Test
    fun `an unreadable list refuses put remove and markConsumed and keeps the stored string`() = runTest {
        store().put(link("b1"))
        val before = storedString()
        var failing = true
        val flaky = store(decrypt = { stored -> if (failing) null else stored })

        assertFalse(flaky.put(link("b2")))
        flaky.remove("b1")
        flaky.markConsumed("b1")

        assertEquals(before, storedString())
        failing = false
        assertEquals(listOf(link("b1")), flaky.all())
    }

    @Test
    fun `a write that failed on one unreadable read succeeds when the list reads again`() = runTest {
        store().put(link("b1"))
        var failuresLeft = 1
        val flaky = store(
            decrypt = { stored ->
                if (failuresLeft > 0) {
                    failuresLeft--
                    null
                } else {
                    stored
                }
            },
        )

        assertFalse(flaky.put(link("b2")))
        assertTrue(flaky.put(link("b2")))

        assertEquals(listOf("b1", "b2"), flaky.all().map { it.broadcastId })
    }

    @Test
    fun `allOrNull tells an absent list from an unreadable one`() = runTest {
        assertEquals(emptyList<PreparedBroadcastLink>(), store().allOrNull())
        store().put(link("b1"))

        val unreadable = store(decrypt = { null })

        assertNull(unreadable.allOrNull())
        assertTrue(unreadable.all().isEmpty())
    }

    @Test
    fun `discardUnreadable drops a list that cannot be read and nothing else`() = runTest {
        store().put(link("b1"))
        assertFalse(store().discardUnreadable())
        assertEquals(listOf(link("b1")), store().all())

        val unreadable = store(decrypt = { null })
        assertTrue(unreadable.discardUnreadable())
        assertNull(storedString())
        assertFalse(unreadable.discardUnreadable())

        assertTrue(unreadable.put(link("b2")))
        assertEquals(listOf(link("b2")), store().all())
    }

    @Test
    fun `an unrelated key write emits nothing from links`() = runTest(UnconfinedTestDispatcher()) {
        val memory = InMemoryPreferencesStore()
        val store = KeystorePreparedBroadcastLinkStore(memory, { it }, { it }, UnconfinedTestDispatcher(testScheduler))
        val emitted = mutableListOf<List<PreparedBroadcastLink>>()
        backgroundScope.launch { store.links.collect(emitted::add) }

        memory.edit { it[stringPreferencesKey("unrelated_setting")] = "changed" }

        assertEquals(1, emitted.size)
    }

    @Test
    fun `a write of the own key emits the new list`() = runTest(UnconfinedTestDispatcher()) {
        val memory = InMemoryPreferencesStore()
        val store = KeystorePreparedBroadcastLinkStore(memory, { it }, { it }, UnconfinedTestDispatcher(testScheduler))
        val emitted = mutableListOf<List<PreparedBroadcastLink>>()
        backgroundScope.launch { store.links.collect(emitted::add) }

        store.put(link("b1"))

        assertEquals(listOf(link("b1")), emitted.last())
        assertEquals(2, emitted.size)
    }

    @Test
    fun `links and all decrypt on the injected dispatcher`() = runTest {
        store().put(link("b1"))
        val threads = CopyOnWriteArrayList<String>()
        val recording = store(
            decrypt = { stored ->
                threads += Thread.currentThread().name
                stored
            },
            ioDispatcher = decryptDispatcher,
        )

        recording.all()
        recording.links.first()

        // Debug-mode coroutines append " @coroutine#N" to the thread name, so only the prefix is the pool's.
        assertEquals(2, threads.size)
        assertTrue(threads.all { it.startsWith(DECRYPT_THREAD) })
    }

    private suspend fun storedString(): String? = dataStore.data.first()[stringPreferencesKey(STORED_KEY)]

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private fun link(id: String) = PreparedBroadcastLink(
        broadcastId = id,
        entryIdentity = "entry",
        publicEndpoint = "exchange.example:7443",
        certFingerprint = null,
        mode = "AUDIO_ONLY",
        title = "Phone",
        sourceId = null,
        createdAtMs = 1L,
        untilMs = 2L,
        linkChoice = PreparedLinkChoice.GENERATED,
        linkUser = "listener",
        linkPassword = "secret",
        relayLinkKey = "key",
        tunnelAlias = "fms-broadcast-prepared-1",
        tunnelPin = "SHA256:pin",
        guests = listOf("guest"),
    )

    private companion object {
        const val DECRYPT_THREAD = "keystore-decrypt-test"
        const val STORED_KEY = "broadcast_prepared_links"
    }
}

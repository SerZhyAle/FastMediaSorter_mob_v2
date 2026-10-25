package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.model.PreparedLinkChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeystorePreparedBroadcastLinkStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
    }

    private fun store(decrypt: (String?) -> String? = { it }) =
        KeystorePreparedBroadcastLinkStore(dataStore, { it }, decrypt)

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
}

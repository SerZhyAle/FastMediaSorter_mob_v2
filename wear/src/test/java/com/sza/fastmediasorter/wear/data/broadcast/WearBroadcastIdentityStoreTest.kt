package com.sza.fastmediasorter.wear.data.broadcast

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2813: the two values whose whole purpose is to be identical on the next run. Nothing on a watch
 * shows either of them, so a regression here is invisible until a listener cannot reconnect.
 *
 * The backing store is an in-memory double rather than a file store: a file-backed `DataStore`
 * renames a temporary file over the live one on every write, which the JVM refuses on Windows while
 * the previous write still holds the target open, so a second write in one test fails for a reason
 * that has nothing to do with the store under test.
 */
class WearBroadcastIdentityStoreTest {

    @Test
    fun `no port is remembered before a session has bound one`() = runTest {
        assertNull(store().preferredPort())
    }

    @Test
    fun `the last remembered port is the one reported back`() = runTest {
        val store = store()

        store.rememberPort(FIRST_PORT)
        store.rememberPort(SECOND_PORT)

        assertEquals(SECOND_PORT, store.preferredPort())
    }

    @Test
    fun `the source id is generated once and returned unchanged afterwards`() = runTest {
        val store = store()

        val first = store.sourceId()
        val second = store.sourceId()

        assertFalse("a blank id would name no source at all", first.isBlank())
        assertEquals(first, second)
    }

    @Test
    fun `a fresh instance over the same storage reads the id the previous one generated`() = runTest {
        // Surviving storage with a new instance on top of it is what an app restart looks like to
        // this class: it must read the id back, not mint a second one.
        val storage = MutableStateFlow(emptyPreferences())
        val generated = WearBroadcastIdentityStore(FakePreferencesDataStore(storage)).sourceId()

        assertEquals(generated, WearBroadcastIdentityStore(FakePreferencesDataStore(storage)).sourceId())
    }

    private fun store(): WearBroadcastIdentityStore =
        WearBroadcastIdentityStore(FakePreferencesDataStore(MutableStateFlow(emptyPreferences())))

    private class FakePreferencesDataStore(
        private val state: MutableStateFlow<Preferences>
    ) : DataStore<Preferences> {

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private companion object {
        const val FIRST_PORT = 41_000
        const val SECOND_PORT = 41_777
    }
}

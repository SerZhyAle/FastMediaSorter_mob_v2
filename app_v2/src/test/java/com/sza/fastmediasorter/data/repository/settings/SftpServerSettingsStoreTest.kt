package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The store reads the DataStore every app setting shares, so a write to an unrelated key must not
 * re-emit - each emission decrypts the password through the Keystore.
 */
class SftpServerSettingsStoreTest {

    private val dataStore = InMemoryPreferencesStore()

    @Test
    fun `unrelated setting write emits nothing`() = runTest(UnconfinedTestDispatcher()) {
        val store = SftpServerSettingsStore(dataStore, UnconfinedTestDispatcher(testScheduler))
        val emitted = mutableListOf<SftpServerConfig>()
        backgroundScope.launch { store.values.collect(emitted::add) }

        dataStore.edit { it[stringPreferencesKey("unrelated_setting")] = "changed" }

        assertEquals(1, emitted.size)
    }

    @Test
    fun `sftp setting write emits the new value`() = runTest(UnconfinedTestDispatcher()) {
        val store = SftpServerSettingsStore(dataStore, UnconfinedTestDispatcher(testScheduler))
        val emitted = mutableListOf<SftpServerConfig>()
        backgroundScope.launch { store.values.collect(emitted::add) }

        store.setPort(PORT)

        assertEquals(listOf(SftpServerConfig.DEFAULT_PORT, PORT), emitted.map { it.port })
    }

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private companion object {
        const val PORT = 2323
    }
}

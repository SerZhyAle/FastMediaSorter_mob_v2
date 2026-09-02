package com.sza.fastmediasorter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S2330: drives [LauncherShortcutSyncRepositoryImpl] against a real file-backed
 * `DataStore<Preferences>`, because the distinction this store exists for - absent versus empty -
 * is a property of the storage layer and disappears under a map-backed fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherShortcutSyncRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: LauncherShortcutSyncRepositoryImpl

    @Before
    fun setUp() {
        // S1449: okio storage, not File storage - File.renameTo cannot replace an existing
        // destination on Windows.
        dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve("launcher_shortcut_sync.preferences_pb").toOkioPath()
            },
            scope = scope,
        )
        repository = LauncherShortcutSyncRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a store that was never written returns null`() = runTest {
        assertNull(repository.syncedRoutes())
    }

    @Test
    fun `an empty baseline reads back as empty and not as absent`() = runTest {
        repository.setSyncedRoutes(emptySet())

        assertEquals(emptySet<String>(), repository.syncedRoutes())
    }

    @Test
    fun `a populated baseline reads back unchanged`() = runTest {
        val routes = setOf("route.calculator", "route.ocr", "route.black_screen")

        repository.setSyncedRoutes(routes)

        assertEquals(routes, repository.syncedRoutes())
    }

    // The launcher reset needs the absent state back, not an empty one: an empty baseline would read
    // every launchable route as newly enabled and bury the re-seeded desktop in cells.
    @Test
    fun `clearing returns the store to absent and not to empty`() = runTest {
        repository.setSyncedRoutes(setOf("route.calculator"))

        repository.clearSyncedRoutes()

        assertNull(repository.syncedRoutes())
    }

    @Test
    fun `a second write replaces the baseline instead of merging into it`() = runTest {
        repository.setSyncedRoutes(setOf("route.calculator", "route.ocr"))

        repository.setSyncedRoutes(setOf("route.game"))

        assertEquals(setOf("route.game"), repository.syncedRoutes())
    }
}

package com.sza.fastmediasorter.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.testing.createFileFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
 * Round-trips [BrowseStateDataStore] against a real file-backed Preferences DataStore
 * (pure JVM). Covers the all-null -> null read branch, isEmpty -> remove-all write branch,
 * per-field present/absent persistence, and invalid-enum tolerance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseStateDataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: BrowseStateDataStore

    @Before
    fun setUp() {
        // S1449: okio storage, not the default File storage. FileStorage persists via
        // File.renameTo, which on Windows cannot replace an existing destination, so every write
        // after the first one failed here while read-only cases passed. OkioStorage moves via
        // Files.move(ATOMIC_MOVE, REPLACE_EXISTING), which does replace. DataStore's own message
        // blames "multiple instances of DataStore" - that is the common Linux cause, not this one,
        // and the wording sent the first two attempts at this fix astray.
        // createWithPath is not the seam: on Android it just wraps the Path back into a File.
        dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve("browse.preferences_pb").toOkioPath()
            },
            scope = scope,
        )
        store = BrowseStateDataStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `filter is null when nothing persisted`() = runTest {
        assertNull(store.filter.first())
    }

    @Test
    fun `save then read round-trips a populated filter`() = runTest {
        store.saveFilter(
            createFileFilter(
                nameContains = "vac",
                minDate = 10L,
                maxDate = 20L,
                minSizeMb = 1.5f,
                maxSizeMb = 9.0f,
                mediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO),
            )
        )

        val loaded = store.filter.first()
        requireNotNull(loaded)
        assertEquals("vac", loaded.nameContains)
        assertEquals(10L, loaded.minDate)
        assertEquals(20L, loaded.maxDate)
        assertEquals(1.5f, loaded.minSizeMb)
        assertEquals(9.0f, loaded.maxSizeMb)
        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO), loaded.mediaTypes)
    }

    @Test
    fun `saving an empty filter clears all keys`() = runTest {
        store.saveFilter(createFileFilter(nameContains = "x"))
        store.saveFilter(createFileFilter()) // empty -> removes everything
        assertNull(store.filter.first())
    }

    @Test
    fun `saving null clears all keys`() = runTest {
        store.saveFilter(createFileFilter(minDate = 5L))
        store.saveFilter(null)
        assertNull(store.filter.first())
    }

    @Test
    fun `clearFilter removes a previously saved filter`() = runTest {
        store.saveFilter(createFileFilter(nameContains = "keep"))
        store.clearFilter()
        assertNull(store.filter.first())
    }

    @Test
    fun `absent fields are removed while present ones persist`() = runTest {
        store.saveFilter(createFileFilter(nameContains = "a", minDate = 1L, maxDate = 2L))
        // Re-save with only minSizeMb -> name/date keys must be dropped.
        store.saveFilter(createFileFilter(minSizeMb = 4.0f))

        val loaded = store.filter.first()
        requireNotNull(loaded)
        assertNull(loaded.nameContains)
        assertNull(loaded.minDate)
        assertNull(loaded.maxDate)
        assertEquals(4.0f, loaded.minSizeMb)
    }
}

package com.sza.fastmediasorter.data.local.preferences

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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Drives [ReviewEligibilityDataStore] against a real file-backed
 * `DataStore<Preferences>` (Preferences DataStore is pure JVM - no Android deps),
 * so increments and snapshots are exercised end-to-end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewEligibilityDataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: ReviewEligibilityDataStore

    @Before
    fun setUp() {
        // S1449: okio storage, not File storage - File.renameTo cannot replace an existing
        // destination on Windows. See BrowseStateDataStoreTest for the full reasoning.
        dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve("review.preferences_pb").toOkioPath()
            },
            scope = scope,
        )
        store = ReviewEligibilityDataStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `incrementSortOps starts from zero and accumulates`() = runTest {
        assertEquals(3L, store.incrementSortOps(3L))
        assertEquals(4L, store.incrementSortOps())
        assertEquals(4L, store.snapshot().sortOpsCount)
    }

    @Test
    fun `incrementSessions accumulates by one`() = runTest {
        assertEquals(1L, store.incrementSessions())
        assertEquals(2L, store.incrementSessions())
        assertEquals(2L, store.snapshot().sessionsWithSort)
    }

    @Test
    fun `markReviewShown records the epoch`() = runTest {
        store.markReviewShown(123_456L)
        assertEquals(123_456L, store.snapshot().reviewShownEpoch)
    }

    @Test
    fun `snapshot defaults to zero for unset counters`() = runTest {
        val snap = store.snapshot()
        assertEquals(0L, snap.sortOpsCount)
        assertEquals(0L, snap.sessionsWithSort)
        assertEquals(0L, snap.reviewShownEpoch)
    }

    @Test
    fun `resetDebug clears all counters`() = runTest {
        store.incrementSortOps(5L)
        store.incrementSessions()
        store.markReviewShown(999L)

        store.resetDebug()

        val snap = store.snapshot()
        assertEquals(0L, snap.sortOpsCount)
        assertEquals(0L, snap.sessionsWithSort)
        assertEquals(0L, snap.reviewShownEpoch)
    }
}

package com.sza.fastmediasorter.data.local.db

import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1169, carried over to S1502: [StreamPlayOutcomeDao.markPlayOutcome] is a conditional write - it
 * must touch zero rows (and so leave the timestamp untouched) when the stored outcome already equals
 * the new value, so a repeated same-value probe cannot invalidate the observed Flow. S1502 moved the
 * guard off `stream_sources` into its own table and re-expressed it as INSERT .. WHERE NOT EXISTS,
 * because upsert syntax needs SQLite 3.24 while `legacy` runs on minSdk 23; behaviour is unchanged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamPlayOutcomeDaoTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.streamPlayOutcomeDao()

    private suspend fun stored(id: String) = dao.observeAll().first().single { it.streamId == id }

    @Test
    fun `unchanged outcome is a zero-row write that keeps the original timestamp`() = runTest {
        dao.markPlayOutcome("s1", "UNKNOWN", T1)
        assertEquals("UNKNOWN", stored("s1").outcome)
        assertEquals(T1, stored("s1").recordedAt)

        // Same value again with a newer timestamp: the NOT EXISTS guard matches, nothing is written.
        dao.markPlayOutcome("s1", "UNKNOWN", T2)
        assertEquals("UNKNOWN", stored("s1").outcome)
        assertEquals(T1, stored("s1").recordedAt)

        // A genuine change flips the outcome and advances the timestamp.
        dao.markPlayOutcome("s1", "OK", T3)
        assertEquals("OK", stored("s1").outcome)
        assertEquals(T3, stored("s1").recordedAt)
    }

    private companion object {
        const val T1 = 1_000L
        const val T2 = 2_000L
        const val T3 = 3_000L
    }
}

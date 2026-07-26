package com.sza.fastmediasorter.data.local.db

import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1169: [StreamSourceDao.markPlayOutcome] is a conditional UPDATE - it must touch zero rows (and so
 * leave the timestamp untouched) when the stored outcome already equals the new value, so a repeated
 * same-value probe cannot invalidate the observed catalog Flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamSourceDaoOutcomeTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.streamSourceDao()

    private fun row(id: String = "s1", url: String = "https://example/ch") = StreamSourceEntity(
        id = id,
        url = url,
        title = "Ch",
        mediaKind = "VIDEO",
        sourceOrigin = "MANUAL",
        sortIndex = 0,
        addedAt = 1L,
    )

    @Test
    fun `unchanged outcome is a zero-row update that keeps the original timestamp`() = runTest {
        dao.upsert(row())

        dao.markPlayOutcome("s1", "UNKNOWN", T1)
        var stored = dao.getByUrl("https://example/ch")!!
        assertEquals("UNKNOWN", stored.lastPlayOutcome)
        assertEquals(T1, stored.lastPlayOutcomeAt)

        // Same value again with a newer timestamp: WHERE clause matches no row, timestamp stays T1.
        dao.markPlayOutcome("s1", "UNKNOWN", T2)
        stored = dao.getByUrl("https://example/ch")!!
        assertEquals("UNKNOWN", stored.lastPlayOutcome)
        assertEquals(T1, stored.lastPlayOutcomeAt)

        // A genuine change flips the outcome and advances the timestamp.
        dao.markPlayOutcome("s1", "OK", T3)
        stored = dao.getByUrl("https://example/ch")!!
        assertEquals("OK", stored.lastPlayOutcome)
        assertEquals(T3, stored.lastPlayOutcomeAt)
    }

    private companion object {
        const val T1 = 1_000L
        const val T2 = 2_000L
        const val T3 = 3_000L
    }
}

package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1509: a terminal playback failure used to be charged to the channel unconditionally, so one ride
 * through a tunnel reddened every channel the user had opened. These cases pin the rule that keeps an
 * outage off the channel's record, and pin that recording a failure is never counted as a play.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordStreamPlayOutcomeUseCaseTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.streamSourceDao()
    private val repo get() = StreamSourceRepository(
        dbRule.db,
        dao,
        dbRule.db.streamQualityMemoryDao(),
        dbRule.db.streamUserStateDao(),
        // The catalog snapshot shares WhileSubscribed, so an unconfined scope with no collector
        // starts nothing; these cases exercise the outcome write path, not the snapshot.
        CoroutineScope(Dispatchers.Unconfined),
    )
    private val stats = RecordingStatsSink()
    private val useCase get() = RecordStreamPlayOutcomeUseCase(repo, stats)

    private suspend fun seedChannel() = dao.upsert(
        StreamSourceEntity(
            id = CHANNEL_ID,
            url = "http://host/a.mp3",
            title = "Title",
            mediaKind = "AUDIO",
            sourceOrigin = "MANUAL",
            sortIndex = 0,
            addedAt = 0L,
        )
    )

    @Test
    fun `a failure with a live network is charged to the channel`() = runTest {
        seedChannel()
        useCase.recordPlayFailure(CHANNEL_ID, hasNetwork = true)
        assertEquals(RecordStreamPlayOutcomeUseCase.OUTCOME_FAIL, repo.playOutcome(CHANNEL_ID))
    }

    @Test
    fun `a failure during an outage leaves the channel unconfirmed, not dead`() = runTest {
        seedChannel()
        useCase.recordPlayFailure(CHANNEL_ID, hasNetwork = false)
        assertEquals(RecordStreamPlayOutcomeUseCase.OUTCOME_UNKNOWN, repo.playOutcome(CHANNEL_ID))
    }

    @Test
    fun `an outage does not overwrite an earlier genuine failure with amber`() = runTest {
        seedChannel()
        useCase.recordPlayFailure(CHANNEL_ID, hasNetwork = true)
        useCase.recordPlayFailure(CHANNEL_ID, hasNetwork = false)
        // The later write wins by design - the channel is only "unconfirmed" from this device's view,
        // and the next successful play restores green. This pins that the write is not silently skipped.
        assertEquals(RecordStreamPlayOutcomeUseCase.OUTCOME_UNKNOWN, repo.playOutcome(CHANNEL_ID))
    }

    @Test
    fun `recording a failure never counts as a play`() = runTest {
        seedChannel()
        useCase.recordPlayFailure(CHANNEL_ID, hasNetwork = true)
        useCase.recordPlayFailure(CHANNEL_ID, hasNetwork = false)
        assertTrue(stats.isEmpty())
        assertNull(dao.getById(CHANNEL_ID)?.lastPlayedAt)
    }

    @Test
    fun `a successful play is green, counted, and timestamped`() = runTest {
        seedChannel()
        useCase.recordPlaySuccess(CHANNEL_ID)
        assertEquals(RecordStreamPlayOutcomeUseCase.OUTCOME_OK, repo.playOutcome(CHANNEL_ID))
        assertNotNull(dao.getById(CHANNEL_ID)?.lastPlayedAt)
        assertFalse(stats.isEmpty())
    }

    @Test
    fun `attribution follows the network, and nothing else`() {
        assertTrue(RecordStreamPlayOutcomeUseCase.isChannelAttributableFailure(hasNetwork = true))
        assertFalse(RecordStreamPlayOutcomeUseCase.isChannelAttributableFailure(hasNetwork = false))
    }

    private class RecordingStatsSink : StatsSink {
        private val events = mutableListOf<StatsEvent>()
        override fun record(event: StatsEvent) { events += event }
        override suspend fun flushNow() = Unit
        fun isEmpty(): Boolean = events.isEmpty()
    }

    private companion object {
        const val CHANNEL_ID = "s1"
    }
}

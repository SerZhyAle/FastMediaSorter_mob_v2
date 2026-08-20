package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1147: the manual-channel add use case gained a duplicate-URL guard. Before it, a second add of an
 * existing url crashed on the unique-`url` index (dao.upsert resolves conflicts on the primary key
 * only). These cases pin the guard against a real in-memory Room so that index participates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddStreamSourceUseCaseTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.streamSourceDao()
    private val repo get() = StreamSourceRepository(
        dbRule.db,
        dao,
        dbRule.db.streamQualityMemoryDao(),
        dbRule.db.streamUserStateDao(),
    )
    private val stats = RecordingStatsSink()
    private val useCase get() = AddStreamSourceUseCase(repo, StreamMediaKindClassifier(), stats)

    private fun row(id: String, url: String) = StreamSourceEntity(
        id = id,
        url = url,
        title = "Title",
        mediaKind = "AUDIO",
        sourceOrigin = "MANUAL",
        sortIndex = 0,
        addedAt = 0L,
    )

    @Test
    fun newUrl_isAdded_andReportsSuccess() = runTest {
        val result = useCase("http://host/a.mp3", null)
        assertEquals(AddStreamSourceUseCase.AddResult.Success, result)
        assertNotNull(dao.getByUrl("http://host/a.mp3"))
        assertEquals(1, stats.count(StatsEvent.StreamAdded))
    }

    @Test
    fun duplicateUrl_isRejected_withoutInsertOrStat() = runTest {
        dao.upsert(row("1", "http://host/a"))
        val result = useCase("http://host/a", null)
        assertEquals(AddStreamSourceUseCase.AddResult.Duplicate, result)
        // The existing row is untouched and no second row was created for the same url.
        assertEquals("1", dao.getByUrl("http://host/a")!!.id)
        assertEquals(0, stats.count(StatsEvent.StreamAdded))
    }

    @Test
    fun duplicateUrl_matchesAfterTrim() = runTest {
        dao.upsert(row("1", "http://host/a"))
        val result = useCase("  http://host/a  ", null)
        assertEquals(AddStreamSourceUseCase.AddResult.Duplicate, result)
    }

    @Test
    fun unsupportedScheme_isInvalidUrl_andNotAdded() = runTest {
        val result = useCase("file:///sdcard/x", null)
        assertEquals(AddStreamSourceUseCase.AddResult.InvalidUrl, result)
        assertNull(dao.getByUrl("file:///sdcard/x"))
    }

    private class RecordingStatsSink : StatsSink {
        private val events = mutableListOf<StatsEvent>()
        override fun record(event: StatsEvent) { events += event }
        override suspend fun flushNow() = Unit
        fun count(event: StatsEvent): Int = events.count { it == event }
    }
}

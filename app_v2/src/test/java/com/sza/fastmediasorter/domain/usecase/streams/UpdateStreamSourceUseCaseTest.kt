package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
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
 * S1145: the manual-channel edit use case gained an explicit media-kind override and a duplicate-URL
 * guard. These cases pin the new behaviour against a real in-memory Room (so the MANUAL-only DAO
 * scoping and the unique-`url` index participate), which the write path had no test coverage for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateStreamSourceUseCaseTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.streamSourceDao()
    private val repo get() = StreamSourceRepository(dbRule.db, dao, dbRule.db.streamPlayOutcomeDao())
    private val useCase get() = UpdateStreamSourceUseCase(repo, StreamMediaKindClassifier())

    private fun row(
        id: String,
        url: String,
        title: String = "Title",
        kind: String = "AUDIO",
        origin: String = "MANUAL",
    ) = StreamSourceEntity(
        id = id,
        url = url,
        title = title,
        mediaKind = kind,
        sourceOrigin = origin,
        sortIndex = 0,
        addedAt = 0L,
    )

    @Test
    fun explicitOverride_isHonored_overClassifier() = runTest {
        dao.upsert(row("1", "http://host/live.m3u8")) // classifier would derive VIDEO
        val result = useCase(dao.getById("1")!!, "http://host/live.m3u8", null, "AUDIO")
        assertEquals(UpdateStreamSourceUseCase.UpdateResult.Success, result)
        assertEquals("AUDIO", dao.getById("1")!!.mediaKind)
    }

    @Test
    fun nullOverride_reDerivesKindFromUrl() = runTest {
        dao.upsert(row("1", "http://host/a.mp3"))
        assertEquals(
            UpdateStreamSourceUseCase.UpdateResult.Success,
            useCase(dao.getById("1")!!, "http://host/v.m3u8", null, null),
        )
        assertEquals("VIDEO", dao.getById("1")!!.mediaKind)
        assertEquals(
            UpdateStreamSourceUseCase.UpdateResult.Success,
            useCase(dao.getById("1")!!, "rtsp://host/live", null, null),
        )
        assertEquals("RTSP", dao.getById("1")!!.mediaKind)
    }

    @Test
    fun duplicateUrl_isRejected_andRowUnchanged() = runTest {
        dao.upsert(row("1", "http://host/a"))
        dao.upsert(row("2", "http://host/b"))
        val result = useCase(dao.getById("1")!!, "http://host/b", null, null)
        assertEquals(UpdateStreamSourceUseCase.UpdateResult.Duplicate, result)
        assertEquals("http://host/a", dao.getById("1")!!.url)
    }

    @Test
    fun sameUrl_titleOnlyEdit_isAllowed() = runTest {
        dao.upsert(row("1", "http://host/a", title = "Old"))
        val result = useCase(dao.getById("1")!!, "http://host/a", "New", null)
        assertEquals(UpdateStreamSourceUseCase.UpdateResult.Success, result)
        assertEquals("New", dao.getById("1")!!.title)
    }

    @Test
    fun catalogRow_isNotEditable() = runTest {
        val catalog = row("1", "http://host/a", origin = "CATALOG")
        dao.upsert(catalog)
        val result = useCase(catalog, "http://host/z", "X", null)
        assertEquals(UpdateStreamSourceUseCase.UpdateResult.NotEditable, result)
        assertEquals("http://host/a", dao.getById("1")!!.url)
    }

    @Test
    fun unsupportedScheme_isInvalidUrl() = runTest {
        dao.upsert(row("1", "http://host/a"))
        val result = useCase(dao.getById("1")!!, "file:///sdcard/x", null, null)
        assertEquals(UpdateStreamSourceUseCase.UpdateResult.InvalidUrl, result)
        assertEquals("http://host/a", dao.getById("1")!!.url)
    }
}

package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.PlaybackPositionDao
import com.sza.fastmediasorter.data.local.db.PlaybackPositionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Branch coverage for [PlaybackPositionRepositoryImpl]: the 95%-completion gate on read,
 * the isCompleted derivation on write, the existing-row gate of markAsCompleted, the
 * count-limit trim, and the exception-swallowing contract. Companion
 * [PlaybackPositionRepositoryImplMarkCompletedTest] covers markPlaybackCompleted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackPositionRepositoryImplTest {

    private lateinit var dao: PlaybackPositionDao
    private lateinit var repo: PlaybackPositionRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repo = PlaybackPositionRepositoryImpl(dao)
    }

    @Test
    fun `getPosition returns null when no row stored`() = runTest {
        coEvery { dao.getPosition("/f") } returns null
        assertNull(repo.getPosition("/f"))
    }

    @Test
    fun `getPosition restores an in-progress position`() = runTest {
        coEvery { dao.getPosition("/f") } returns entity(position = 4_000, duration = 10_000, completed = false)
        assertEquals(4_000L, repo.getPosition("/f"))
    }

    @Test
    fun `getPosition returns zero when row marked completed`() = runTest {
        coEvery { dao.getPosition("/f") } returns entity(position = 4_000, duration = 10_000, completed = true)
        assertEquals(0L, repo.getPosition("/f"))
    }

    @Test
    fun `getPosition returns zero past the 95 percent threshold`() = runTest {
        coEvery { dao.getPosition("/f") } returns entity(position = 9_600, duration = 10_000, completed = false)
        assertEquals(0L, repo.getPosition("/f"))
    }

    @Test
    fun `getPosition swallows dao failure and returns null`() = runTest {
        coEvery { dao.getPosition("/f") } throws RuntimeException("db down")
        assertNull(repo.getPosition("/f"))
    }

    @Test
    fun `savePosition marks completed past 95 percent`() = runTest {
        val saved = slot<PlaybackPositionEntity>()
        coEvery { dao.savePosition(capture(saved)) } returns Unit

        repo.savePosition("/f", position = 9_600, duration = 10_000)

        assertTrue(saved.captured.isCompleted)
        assertEquals(9_600L, saved.captured.position)
    }

    @Test
    fun `savePosition leaves not-completed below threshold`() = runTest {
        val saved = slot<PlaybackPositionEntity>()
        coEvery { dao.savePosition(capture(saved)) } returns Unit

        repo.savePosition("/f", position = 1_000, duration = 10_000)

        assertFalse(saved.captured.isCompleted)
    }

    @Test
    fun `savePosition treats zero duration as not completed`() = runTest {
        val saved = slot<PlaybackPositionEntity>()
        coEvery { dao.savePosition(capture(saved)) } returns Unit

        repo.savePosition("/f", position = 5_000, duration = 0)

        assertFalse(saved.captured.isCompleted)
    }

    @Test
    fun `markAsCompleted updates the existing row`() = runTest {
        coEvery { dao.getPosition("/f") } returns entity(position = 1_000, duration = 10_000, completed = false)
        val saved = slot<PlaybackPositionEntity>()
        coEvery { dao.savePosition(capture(saved)) } returns Unit

        repo.markAsCompleted("/f")

        assertTrue(saved.captured.isCompleted)
    }

    @Test
    fun `markAsCompleted is a no-op without an existing row`() = runTest {
        coEvery { dao.getPosition("/f") } returns null
        repo.markAsCompleted("/f")
        coVerify(exactly = 0) { dao.savePosition(any()) }
    }

    @Test
    fun `cleanupOldPositions trims only when over the limit`() = runTest {
        coEvery { dao.getPositionsCount() } returns 10_000
        repo.cleanupOldPositions()
        coVerify { dao.keepOnlyRecentPositions(9_000) }
    }

    @Test
    fun `cleanupOldPositions does nothing under the limit`() = runTest {
        coEvery { dao.getPositionsCount() } returns 100
        repo.cleanupOldPositions()
        coVerify(exactly = 0) { dao.keepOnlyRecentPositions(any()) }
    }

    @Test
    fun `deletePosition and deleteAllPositions delegate to dao`() = runTest {
        repo.deletePosition("/f")
        repo.deleteAllPositions()
        coVerify { dao.deletePosition("/f") }
        coVerify { dao.deleteAllPositions() }
    }

    private fun entity(position: Long, duration: Long, completed: Boolean) = PlaybackPositionEntity(
        filePath = "/f",
        position = position,
        duration = duration,
        lastPlayedAt = 0L,
        isCompleted = completed,
    )
}

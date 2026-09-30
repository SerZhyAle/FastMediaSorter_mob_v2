package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S3836: the journal against a real database, where the old event-log trim evicted a program launched
 * once as soon as a few others were relaunched often enough.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherJournalRepositoryImplTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val repository by lazy {
        LauncherJournalRepositoryImpl(dbRule.db.launcherJournalDao(), dbRule.db.launcherLaunchStatsDao())
    }

    private fun feature(index: Int) = LauncherCellCommand.Feature("route_$index")

    @Test
    fun `a program launched once survives many relaunches of another`() = runTest {
        val once = feature(1)
        val often = feature(2)
        repository.record(once)
        repeat(RELAUNCHES) { repository.record(often) }

        val recents = repository.recentCommands(LauncherJournalRepository.MAX_RECENT_PROGRAMS).first()

        assertTrue(once in recents)
        assertEquals(2, recents.size)
    }

    @Test
    fun `only the oldest program leaves once the ceiling is passed`() = runTest {
        val total = LauncherJournalRepository.MAX_RECENT_PROGRAMS + 1
        for (index in 0 until total) {
            repository.record(feature(index))
            Thread.sleep(1)
        }

        val recents = repository.recentCommands(total).first()

        assertEquals(LauncherJournalRepository.MAX_RECENT_PROGRAMS, recents.size)
        assertFalse(feature(0) in recents)
        assertTrue(feature(total - 1) in recents)
    }

    @Test
    fun `a relaunch keeps one row and moves the program to the front`() = runTest {
        val first = feature(1)
        val second = feature(2)
        repository.record(first)
        Thread.sleep(1)
        repository.record(second)
        Thread.sleep(1)
        repository.record(first)

        val recents = repository.recentCommands(LauncherJournalRepository.MAX_RECENT_PROGRAMS).first()

        assertEquals(listOf(first, second), recents)
    }

    private companion object {
        const val RELAUNCHES = 60
    }
}

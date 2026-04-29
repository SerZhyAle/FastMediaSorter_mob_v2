package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.PlaybackPositionDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackPositionRepositoryImplMarkCompletedTest {

    private lateinit var mockDao: PlaybackPositionDao
    private lateinit var repo: PlaybackPositionRepositoryImpl

    @Before
    fun setUp() {
        mockDao = mockk(relaxed = true)
        repo = PlaybackPositionRepositoryImpl(mockDao)
    }

    @Test
    fun `markPlaybackCompleted deletes the saved position once`() = runTest {
        val path = "smb://server/file.mp4"

        repo.markPlaybackCompleted(path, "playback-completed")

        coVerify(exactly = 1) { mockDao.deletePosition(path) }
    }

    @Test
    fun `markPlaybackCompleted swallows DAO exception`() = runTest {
        val path = "smb://server/broken.mp4"
        coEvery { mockDao.deletePosition(path) } throws RuntimeException("simulated DB failure")

        repo.markPlaybackCompleted(path, "playback-completed")

        coVerify(exactly = 1) { mockDao.deletePosition(path) }
    }
}

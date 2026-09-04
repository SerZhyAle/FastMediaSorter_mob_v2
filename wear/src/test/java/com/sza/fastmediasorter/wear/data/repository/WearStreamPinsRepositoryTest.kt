package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S2497: Tests for WearStreamPinsRepository checking pin, unpin, pending deltas, and persistence.
 */
class WearStreamPinsRepositoryTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private fun repository(): WearStreamPinsRepository {
        val context = mockk<Context>()
        every { context.filesDir } returns temporaryFolder.root
        val phonePinsRepo = mockk<WearPhonePinsRepository>()
        every { phonePinsRepo.observe() } returns kotlinx.coroutines.flow.MutableStateFlow(emptySet())
        return WearStreamPinsRepository(context, Gson(), phonePinsRepo)
    }

    @Test
    fun `pin and unpin toggle pinned status and record deltas`() = runBlocking {
        val repo = repository()

        assertFalse(repo.isPinned("https://stream.example.com/live.m3u8"))
        assertEquals(emptySet<String>(), repo.getWatchPins())

        val isPinnedNow = repo.togglePin("https://stream.example.com/live.m3u8")
        assertTrue(isPinnedNow)
        assertTrue(repo.isPinned("https://stream.example.com/live.m3u8"))
        assertEquals(1, repo.getWatchPins().size)

        val deltas = repo.getPendingDelta()
        assertEquals(1, deltas.size)
        assertEquals(true, deltas.first().isPinned)

        val unpinnedNow = repo.togglePin("https://stream.example.com/live.m3u8")
        assertFalse(unpinnedNow)
        assertFalse(repo.isPinned("https://stream.example.com/live.m3u8"))
        assertEquals(0, repo.getWatchPins().size)

        val deltasAfter = repo.getPendingDelta()
        assertEquals(1, deltasAfter.size)
        assertEquals(false, deltasAfter.first().isPinned)
    }

    @Test
    fun `pins survive repository recreation`() = runBlocking {
        val repo1 = repository()
        repo1.setPin("https://stream.example.com/channel1", true)
        repo1.setPin("https://stream.example.com/channel2", true)

        val repo2 = repository()
        assertTrue(repo2.isPinned("https://stream.example.com/channel1"))
        assertTrue(repo2.isPinned("https://stream.example.com/channel2"))
        assertEquals(2, repo2.getWatchPins().size)
    }

    @Test
    fun `clearPendingDelta removes deltas`() = runBlocking {
        val repo = repository()
        repo.setPin("https://stream.example.com/channel1", true)
        assertEquals(1, repo.getPendingDelta().size)

        repo.clearPendingDelta()
        assertEquals(0, repo.getPendingDelta().size)
    }
}


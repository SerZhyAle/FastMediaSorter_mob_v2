package com.sza.fastmediasorter.wear.domain.repository

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1701: shuffle is a rule over the published set rather than a stored queue, so what has to hold is
 * the succession itself: it stays inside the set the browse screen published, and it never hands back
 * the file already playing - a repeat reads as a broken shuffle rather than as chance.
 */
class PlaybackSetManagerShuffleTest {

    private lateinit var manager: PlaybackSetManager

    private val files = (1..5).map { index -> file(index.toLong(), "track$index.mp3") }

    @Before
    fun setUp() {
        manager = PlaybackSetManager()
    }

    @Test
    fun `ordered succession walks the set and wraps around`() {
        manager.publish(files, startIndex = 0)

        assertEquals("track2.mp3", manager.next()?.name)
        assertEquals("track3.mp3", manager.next()?.name)

        manager.publish(files, startIndex = files.lastIndex)
        assertEquals("track1.mp3", manager.next()?.name)
    }

    @Test
    fun `ordered succession walks back and wraps around`() {
        manager.publish(files, startIndex = 0)

        assertEquals("track5.mp3", manager.previous()?.name)
        assertEquals("track4.mp3", manager.previous()?.name)
    }

    @Test
    fun `shuffled succession stays inside the published set`() {
        manager.publish(files, startIndex = 0)
        manager.shuffleEnabled = true

        repeat(SAMPLE_RUNS) {
            val picked = manager.next()
            assertTrue("shuffle left the published set", files.any { it.id == picked?.id })
        }
    }

    @Test
    fun `shuffled succession never repeats the current file`() {
        manager.publish(files, startIndex = 0)
        manager.shuffleEnabled = true

        repeat(SAMPLE_RUNS) {
            val before = manager.currentSet.value?.current
            val picked = manager.next()
            assertNotEquals("shuffle repeated the playing file", before?.id, picked?.id)
        }
    }

    @Test
    fun `shuffled previous also moves off the current file`() {
        manager.publish(files, startIndex = 2)
        manager.shuffleEnabled = true

        val before = manager.currentSet.value?.current
        val picked = manager.previous()

        assertNotEquals(before?.id, picked?.id)
    }

    @Test
    fun `a set of one file is safe in both modes`() {
        val single = listOf(file(9L, "only.mp3"))
        manager.publish(single, startIndex = 0)

        assertEquals("only.mp3", manager.next()?.name)

        manager.shuffleEnabled = true
        assertEquals("only.mp3", manager.next()?.name)
        assertEquals("only.mp3", manager.previous()?.name)
    }

    @Test
    fun `removing the current middle file selects its following neighbour`() {
        manager.publish(files, startIndex = 2)

        val next = manager.removeAndSelectNext(files[2].id)

        assertEquals("track4.mp3", next?.name)
        assertEquals(
            listOf("track1.mp3", "track2.mp3", "track4.mp3", "track5.mp3"),
            manager.currentSet.value?.files?.map { it.name }
        )
    }

    @Test
    fun `removing the final file selects the new final file`() {
        manager.publish(files, startIndex = files.lastIndex)

        assertEquals("track4.mp3", manager.removeAndSelectNext(files.last().id)?.name)
    }

    @Test
    fun `removing the only file clears the published set`() {
        val single = listOf(file(9L, "only.mp3"))
        manager.publish(single, startIndex = 0)

        assertEquals(null, manager.removeAndSelectNext(single.single().id))
        assertEquals(null, manager.currentSet.value)
    }

    private fun file(id: Long, name: String) = WearMediaFile(
        id = id,
        name = name,
        uri = mockk<Uri>(relaxed = true),
        mimeType = "audio/mpeg",
        size = 1024L,
        dateModified = 0L
    )

    private companion object {
        /** Enough draws that a rule broken only sometimes still fails the run. */
        const val SAMPLE_RUNS = 50
    }
}

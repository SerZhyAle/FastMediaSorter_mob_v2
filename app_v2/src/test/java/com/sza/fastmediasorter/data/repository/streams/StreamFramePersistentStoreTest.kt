package com.sza.fastmediasorter.data.repository.streams

import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * S1130: [StreamFramePersistentStore.evictToBudget] bounds the on-disk thumbnail store by total size,
 * never by a fixed file count - a captured catalog of thousands of channels used to silently lose its
 * oldest previews at a 64-file cap regardless of real disk usage.
 */
class StreamFramePersistentStoreTest {

    // evictToBudget never reads the injected Context (the dir is passed explicitly), so a relaxed mock
    // keeps this a fast pure-JVM test with no Robolectric/SDK dependency.
    private val store = StreamFramePersistentStore(mockk(relaxed = true))
    private val dir: File = Files.createTempDirectory("stream-frame-evict").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun frame(name: String, bytes: Int, order: Int): File =
        File(dir, "$name.jpg").apply {
            writeBytes(ByteArray(bytes))
            setLastModified(BASE_MTIME + order * MTIME_STEP)
        }

    @Test
    fun `keeps every thumbnail when total footprint is within budget`() {
        // More tiles than the retired 64-file cap, but a tiny total footprint: none may be evicted.
        repeat(OVER_CAP_COUNT) { i -> frame("f$i", bytes = TINY, order = i) }

        store.evictToBudget(dir, maxBytes = ROOMY_BUDGET)

        assertEquals(OVER_CAP_COUNT, dir.listFiles()!!.size)
    }

    @Test
    fun `evicts oldest thumbnails first until total footprint is within budget`() {
        val oldest = frame("oldest", bytes = TILE, order = 0)
        val older = frame("older", bytes = TILE, order = 1)
        val mid = frame("mid", bytes = TILE, order = 2)
        val newer = frame("newer", bytes = TILE, order = 3)
        val newest = frame("newest", bytes = TILE, order = 4)

        // Budget holds only two TILE-sized frames; the three oldest must be evicted.
        store.evictToBudget(dir, maxBytes = TWO_TILES_BUDGET)

        assertFalse("oldest evicted", oldest.exists())
        assertFalse("older evicted", older.exists())
        assertFalse("mid evicted", mid.exists())
        assertTrue("newer retained", newer.exists())
        assertTrue("newest retained", newest.exists())
        assertEquals(2 * TILE.toLong(), dir.listFiles()!!.sumOf { it.length() })
    }

    @Test
    fun `counts and evicts only jpg frames`() {
        val kept = frame("keep", bytes = TILE, order = 0)
        val scratch = File(dir, "scratch.tmp").apply { writeBytes(ByteArray(ROOMY_BUDGET.toInt())) }

        // The large .tmp neither counts toward the budget nor gets deleted.
        store.evictToBudget(dir, maxBytes = TILE.toLong())

        assertTrue("jpg within budget retained", kept.exists())
        assertTrue("non-jpg untouched", scratch.exists())
    }

    private companion object {
        const val BASE_MTIME = 1_000_000_000_000L
        const val MTIME_STEP = 10_000L
        const val TINY = 10
        const val TILE = 100
        const val OVER_CAP_COUNT = 70
        const val ROOMY_BUDGET = 10_000L
        const val TWO_TILES_BUDGET = 250L
    }
}

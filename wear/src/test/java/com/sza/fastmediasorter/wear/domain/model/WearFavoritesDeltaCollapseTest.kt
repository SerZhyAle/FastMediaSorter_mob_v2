package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2435: the pending delta must stay bounded while still telling the phone the right final state.
 */
class WearFavoritesDeltaCollapseTest {

    @Test
    fun `repeated marks of one path collapse to the newest entry`() {
        var queue = emptyList<WearFavoriteDeltaItem>()
        queue = appendFavoriteDelta(queue, item(SOURCE_ID_LOCAL, "/music/a.mp3", true, 100L))
        queue = appendFavoriteDelta(queue, item(SOURCE_ID_LOCAL, "/music/a.mp3", false, 200L))
        queue = appendFavoriteDelta(queue, item(SOURCE_ID_LOCAL, "/music/a.mp3", true, 300L))

        assertEquals(1, queue.size)
        assertEquals(300L, queue.single().changedAt)
        assertTrue(queue.single().isFavorite)
    }

    @Test
    fun `different paths stay as separate entries in insertion order`() {
        var queue = emptyList<WearFavoriteDeltaItem>()
        queue = appendFavoriteDelta(queue, item(SOURCE_ID_LOCAL, "/music/a.mp3", true, 100L))
        queue = appendFavoriteDelta(queue, item(SOURCE_ID_LOCAL, "/music/b.mp3", true, 200L))

        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), queue.map { it.filePath })
    }

    @Test
    fun `two spellings of one stream address collapse into one entry`() {
        var queue = emptyList<WearFavoriteDeltaItem>()
        queue = appendFavoriteDelta(queue, item(SOURCE_ID_STREAM, "https://Radio.Example/live/", true, 100L))
        queue = appendFavoriteDelta(queue, item(SOURCE_ID_STREAM, "https://radio.example:443/live", false, 200L))

        assertEquals(1, queue.size)
        assertEquals(200L, queue.single().changedAt)
    }

    @Test
    fun `overflow keeps the limit and drops the oldest entries`() {
        val limit = 3
        var queue = emptyList<WearFavoriteDeltaItem>()
        for (index in 0 until 5) {
            queue = appendFavoriteDelta(queue, item(SOURCE_ID_LOCAL, "/music/$index.mp3", true, index.toLong()), limit)
        }

        assertEquals(limit, queue.size)
        assertEquals(listOf("/music/2.mp3", "/music/3.mp3", "/music/4.mp3"), queue.map { it.filePath })
    }

    private fun item(sourceId: String, filePath: String, isFavorite: Boolean, changedAt: Long) =
        WearFavoriteDeltaItem(sourceId = sourceId, filePath = filePath, isFavorite = isFavorite, changedAt = changedAt)
}

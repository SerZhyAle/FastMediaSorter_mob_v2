package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2497: Unit tests for appendStreamPinDelta collapsing and bounded queue logic.
 */
class WearStreamPinsDeltaCollapseTest {

    @Test
    fun `repeated pin operations on same stream collapse to newest entry`() {
        var queue = emptyList<WearStreamPinDeltaItem>()
        queue = appendStreamPinDelta(queue, item("https://stream.example.com/one", true, 100L))
        queue = appendStreamPinDelta(queue, item("https://stream.example.com/one", false, 200L))
        queue = appendStreamPinDelta(queue, item("https://stream.example.com/one", true, 300L))

        assertEquals(1, queue.size)
        assertEquals(300L, queue.single().changedAt)
        assertTrue(queue.single().isPinned)
    }

    @Test
    fun `different streams stay as separate entries in insertion order`() {
        var queue = emptyList<WearStreamPinDeltaItem>()
        queue = appendStreamPinDelta(queue, item("https://stream.example.com/a", true, 100L))
        queue = appendStreamPinDelta(queue, item("https://stream.example.com/b", true, 200L))

        assertEquals(listOf("https://stream.example.com/a", "https://stream.example.com/b"), queue.map { it.urlOrIdentity })
    }

    @Test
    fun `two spellings of one stream address collapse into one entry`() {
        var queue = emptyList<WearStreamPinDeltaItem>()
        queue = appendStreamPinDelta(queue, item("https://Radio.Example/live/", true, 100L))
        queue = appendStreamPinDelta(queue, item("https://radio.example:443/live", false, 200L))

        assertEquals(1, queue.size)
        assertEquals(200L, queue.single().changedAt)
    }

    @Test
    fun `overflow keeps the limit and drops the oldest entries`() {
        val limit = 3
        var queue = emptyList<WearStreamPinDeltaItem>()
        for (index in 0 until 5) {
            queue = appendStreamPinDelta(queue, item("https://stream.example.com/$index", true, index.toLong()), limit)
        }

        assertEquals(limit, queue.size)
        assertEquals(
            listOf("https://stream.example.com/2", "https://stream.example.com/3", "https://stream.example.com/4"),
            queue.map { it.urlOrIdentity }
        )
    }

    private fun item(urlOrIdentity: String, isPinned: Boolean, changedAt: Long) =
        WearStreamPinDeltaItem(urlOrIdentity = urlOrIdentity, isPinned = isPinned, changedAt = changedAt)
}

package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2146: the counter behind the streams list's default order.
 *
 * The store itself needs SharedPreferences and the watch module has no Robolectric harness, so the
 * decision is asserted where it actually lives - in the pure fold the store calls. The prune is the
 * part most worth covering: it is the mitigation §7 attaches to unbounded growth, and code that
 * never runs in testing is code that silently stops working.
 */
class WearStreamUsageFoldTest {

    @Test
    fun `a first play creates the entry with one play`() {
        val result = recordWearStreamPlay(emptyMap(), IDENTITY, atEpochMillis = 1_000L)

        assertEquals(1, result.getValue(IDENTITY).playCount)
        assertEquals(1_000L, result.getValue(IDENTITY).lastPlayedAt)
    }

    @Test
    fun `a second play on the same identity raises the count and advances the stamp`() {
        val first = recordWearStreamPlay(emptyMap(), IDENTITY, atEpochMillis = 1_000L)
        val second = recordWearStreamPlay(first, IDENTITY, atEpochMillis = 2_000L)

        assertEquals(2, second.getValue(IDENTITY).playCount)
        assertEquals(2_000L, second.getValue(IDENTITY).lastPlayedAt)
        assertEquals("one station played twice is still one entry", 1, second.size)
    }

    @Test
    fun `a store over the limit keeps the newest entries and drops the oldest`() {
        val limit = 3
        val crowded = (1..limit).associate { index ->
            val identity = "https://example.test/$index"
            identity to WearStreamUsage(identity, playCount = 9, lastPlayedAt = index.toLong())
        }

        val result = recordWearStreamPlay(crowded, IDENTITY, atEpochMillis = 500L, limit = limit)

        assertEquals(limit, result.size)
        assertTrue("the just-played station survives the prune", IDENTITY in result)
        assertFalse("the least recently played entry is the one dropped", "https://example.test/1" in result)
        assertTrue("https://example.test/3" in result)
    }

    @Test
    fun `a store at the limit is left whole when an existing entry is replayed`() {
        val limit = 2
        val full = mapOf(
            IDENTITY to WearStreamUsage(IDENTITY, playCount = 1, lastPlayedAt = 10L),
            OTHER to WearStreamUsage(OTHER, playCount = 1, lastPlayedAt = 20L)
        )

        val result = recordWearStreamPlay(full, IDENTITY, atEpochMillis = 30L, limit = limit)

        assertEquals(limit, result.size)
        assertEquals(2, result.getValue(IDENTITY).playCount)
        assertTrue("replaying an entry must not evict its neighbour", OTHER in result)
    }

    private companion object {
        const val IDENTITY = "https://example.test/stream"
        const val OTHER = "https://example.test/other"
    }
}

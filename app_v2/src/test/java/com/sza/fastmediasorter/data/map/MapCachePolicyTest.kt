package com.sza.fastmediasorter.data.map

import com.sza.fastmediasorter.domain.map.MapRefreshPolicy
import com.sza.fastmediasorter.domain.model.map.MapPoint
import com.sza.fastmediasorter.domain.model.map.MapSnapshot
import com.sza.fastmediasorter.domain.repository.MapResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1175: the two promises the map cell makes when the world is not cooperating - it does not re-fetch
 * a tile it already has, and it does not blank itself when a refresh fails.
 *
 * Both are age-dependent, so on a device they only show up after twenty minutes or after the network
 * drops, which is precisely when nobody is watching the cell.
 */
class MapCachePolicyTest {

    private val snapshot = MapSnapshot(
        point = MapPoint(49.8397, 24.0297),
        label = "Lviv",
        imagePath = "/cache/map_tiles/tile_15_1_1.png",
        attribution = "© OpenStreetMap contributors",
        capturedAt = 1_000_000L,
    )

    @Test
    fun `a snapshot inside the window is reused instead of re-fetched`() {
        val fresh = MapCachePolicy.freshOrNull(snapshot, snapshot.capturedAt + ONE_MINUTE_MS)

        assertEquals(snapshot, fresh)
    }

    @Test
    fun `a snapshot past the window is refreshed`() {
        assertNull(MapCachePolicy.freshOrNull(snapshot, snapshot.capturedAt + MapRefreshPolicy.TTL_MS))
    }

    @Test
    fun `an absent snapshot is never fresh`() {
        assertNull(MapCachePolicy.freshOrNull(null, snapshot.capturedAt))
    }

    @Test
    fun `a successful refresh wins over the previous snapshot`() {
        val fetched = snapshot.copy(label = "Lviv Opera", capturedAt = 2_000_000L)

        val result = MapCachePolicy.resultFor(snapshot, fetched, 2_000_000L)

        assertEquals(MapResult.Fresh(fetched), result)
    }

    @Test
    fun `a failed refresh keeps the previous snapshot and reports its age`() {
        val now = snapshot.capturedAt + ONE_HOUR_MS

        val result = MapCachePolicy.resultFor(snapshot, fetched = null, nowMs = now)

        assertEquals(MapResult.Stale(snapshot, ONE_HOUR_MS), result)
    }

    @Test
    fun `a failed refresh with nothing cached is unavailable`() {
        val result = MapCachePolicy.resultFor(previous = null, fetched = null, nowMs = 1L)

        assertEquals(MapResult.Unavailable, result)
    }

    private companion object {
        const val ONE_MINUTE_MS = 60_000L
        const val ONE_HOUR_MS = 3_600_000L
    }
}

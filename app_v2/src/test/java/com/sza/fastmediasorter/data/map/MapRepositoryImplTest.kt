package com.sza.fastmediasorter.data.map

import com.sza.fastmediasorter.domain.location.DeviceLocationSource
import com.sza.fastmediasorter.domain.map.MapPlaceLabelProvider
import com.sza.fastmediasorter.domain.map.MapRefreshPolicy
import com.sza.fastmediasorter.domain.map.MapTileProvider
import com.sza.fastmediasorter.domain.model.map.MapPoint
import com.sza.fastmediasorter.domain.repository.MapResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * S1175: the degradation chain the map cell lives on - it must never come up empty when it has ever
 * had a picture, and it must not re-download a tile it already has.
 *
 * On a device these paths only appear after twenty minutes, after the network drops, or after the
 * launcher process is killed, which is exactly when nobody is watching the cell.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapRepositoryImplTest {

    private val app = RuntimeEnvironment.getApplication()
    private val location = mockk<DeviceLocationSource>()
    private val tiles = mockk<MapTileProvider>()
    private val labels = mockk<MapPlaceLabelProvider>()
    private lateinit var tileFile: File

    private fun repository() = MapRepositoryImpl(app, location, tiles, labels)

    @Before
    fun setUp() {
        tileFile = File(app.cacheDir, "map_tiles/tile_15_1_1.png").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(1))
        }
        app.getSharedPreferences(MapRepositoryImpl.PREFS_NAME, 0).edit().clear().commit()
        every { tiles.attribution } returns ATTRIBUTION
        every { location.hasPermission() } returns true
        coEvery { location.lastKnownPoint() } returns POINT
        coEvery { tiles.tileFor(any(), any()) } returns tileFile.absolutePath
        coEvery { labels.labelFor(any()) } returns "Lviv Opera"
    }

    @Test
    fun `a second read inside the window reuses the tile instead of downloading it again`() = runTest {
        val repository = repository()

        val first = repository.current()
        val second = repository.current()

        assertTrue(first is MapResult.Fresh)
        assertEquals(first, second)
        coVerify(exactly = 1) { tiles.tileFor(any(), any()) }
    }

    @Test
    fun `a missing permission keeps the last picture instead of blanking the cell`() = runTest {
        val warm = repository()
        warm.current()
        every { location.hasPermission() } returns false

        // A fresh instance: a revoked grant usually shows up after the launcher process was killed,
        // which is precisely the case the in-memory field cannot answer.
        val result = repository().current()

        val snapshot = (result as MapResult.PermissionMissing).snapshot
        assertEquals(POINT, snapshot?.point)
        assertEquals(tileFile.absolutePath, snapshot?.imagePath)
    }

    @Test
    fun `a failed refresh after a restart shows the mirrored snapshot as stale`() = runTest {
        repository().current()
        ageMirroredSnapshot()
        coEvery { location.lastKnownPoint() } returns null

        val result = repository().current()

        assertTrue("expected a stale result, got $result", result is MapResult.Stale)
        assertEquals(tileFile.absolutePath, (result as MapResult.Stale).snapshot.imagePath)
        assertTrue(result.ageMs > MapRefreshPolicy.TTL_MS)
    }

    /**
     * Only the timestamp is rewritten; the snapshot itself is the one the repository just mirrored,
     * so this test cannot pass by encoding the mirror a second, different way.
     */
    private fun ageMirroredSnapshot() {
        val prefs = app.getSharedPreferences(MapRepositoryImpl.PREFS_NAME, 0)
        val aged = prefs.getLong(MapRepositoryImpl.KEY_TIME, 0L) - MapRefreshPolicy.TTL_MS - ONE_MINUTE_MS
        prefs.edit().putLong(MapRepositoryImpl.KEY_TIME, aged).commit()
    }

    @Test
    fun `no fix and nothing cached is unavailable rather than a crash`() = runTest {
        coEvery { location.lastKnownPoint() } returns null

        assertEquals(MapResult.Unavailable, repository().current())
    }

    @Test
    fun `a snapshot whose tile file is gone is not shown`() = runTest {
        repository().current()
        tileFile.delete()
        coEvery { location.lastKnownPoint() } returns null

        assertEquals(MapResult.Unavailable, repository().current())
    }

    @Test
    fun `a silent geocoder falls back to coordinates`() = runTest {
        coEvery { labels.labelFor(any()) } returns null

        val result = repository().current() as MapResult.Fresh

        assertEquals(POINT.formatted(), result.snapshot.label)
    }

    @Test
    fun `a failed tile download at an unchanged point reuses the tile already on disk`() = runTest {
        repository().current()
        ageMirroredSnapshot()
        coEvery { tiles.tileFor(any(), any()) } returns null

        // A new instance, so the aged mirror is what is loaded - reusing the warm one would answer
        // from its in-memory snapshot and never reach the tile provider at all.
        val result = repository().current()

        // The point has not moved, so the expired file under it is still the right picture - this is
        // the path where an over-eager cache eviction would leave the cell with a dangling path.
        // Two calls: the warm-up fetch and the refresh under test. The count is asserted because
        // without it this test passed while the refresh never reached the tile provider at all.
        coVerify(exactly = 2) { tiles.tileFor(any(), any()) }
        assertTrue("expected a refreshed result, got $result", result is MapResult.Fresh)
        assertEquals(tileFile.absolutePath, (result as MapResult.Fresh).snapshot.imagePath)
    }

    @Test
    fun `a cell that only polls does not keep asking the geocoder`() = runTest {
        val repository = repository()

        repository.current()
        repository.current()
        repository.current()

        // The address is resolved for the fetch that produced the snapshot and never again while it
        // is fresh: a poll on every return to the home screen must not become a geocoder round trip.
        coVerify(exactly = 1) { labels.labelFor(any()) }
    }

    @Test
    fun `a revoked permission stops the app resolving the stored position`() = runTest {
        repository().current()
        every { location.hasPermission() } returns false

        repository().current()

        // Once for the warm-up fetch, never for the permission-missing read: revoking the grant means
        // stop using my position, and geocoding it anyway would answer the opposite.
        coVerify(exactly = 1) { labels.labelFor(any()) }
    }

    @Test
    fun `a mirror without attribution is discarded rather than shown uncredited`() = runTest {
        repository().current()
        app.getSharedPreferences(MapRepositoryImpl.PREFS_NAME, 0).edit()
            .remove(MapRepositoryImpl.KEY_ATTRIBUTION)
            .commit()
        coEvery { location.lastKnownPoint() } returns null

        assertEquals(MapResult.Unavailable, repository().current())
    }

    private companion object {
        val POINT = MapPoint(49.8397, 24.0297)
        const val ATTRIBUTION = "© OpenStreetMap contributors"
        const val ONE_MINUTE_MS = 60_000L
    }
}

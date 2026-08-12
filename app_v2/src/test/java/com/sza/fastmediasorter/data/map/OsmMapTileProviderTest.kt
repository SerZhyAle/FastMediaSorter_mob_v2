package com.sza.fastmediasorter.data.map

import com.sza.fastmediasorter.domain.model.map.MapPoint
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * S1175: the tile cache is the half of this feature whose failures are invisible on a device - a
 * corrupt or evicted tile looks exactly like a slow network until the cell has been blank for a week.
 *
 * Every case here is one the OSM usage policy or a real audit finding put on the list: a failed
 * download must not destroy the tile already on disk, a throttle must stop the requests, and the
 * directory must not grow forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OsmMapTileProviderTest {

    private val app = RuntimeEnvironment.getApplication()
    private val client = mockk<OkHttpClient>()
    private lateinit var cacheDir: File

    private fun provider() = OsmMapTileProvider(app, client)

    @Before
    fun setUp() {
        cacheDir = File(app.cacheDir, "map_tiles")
        cacheDir.deleteRecursively()
    }

    @Test
    fun `a downloaded tile lands in the cache and is served from it next time`() = runTest {
        respondWith(code = 200)
        val provider = provider()

        val first = provider.tileFor(POINT, ZOOM)
        val second = provider.tileFor(POINT, ZOOM)

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(TILE_BYTES, File(first!!).readText())
        // One request for two reads: re-fetching a tile the cache already holds is what the usage
        // policy forbids, and what gets the whole application blocked.
        assertEquals(1, requestedUrls.size)
    }

    @Test
    fun `a failed download keeps the expired tile that is already on disk`() = runTest {
        val existing = seedTile(ageMs = TimeUnit.DAYS.toMillis(8))
        failWith(IOException("network down"))

        val result = provider().tileFor(POINT, ZOOM)

        // The regression this test exists for: an eviction that ran on the failure path deleted the
        // only picture the offline cell had, at exactly the moment it was needed.
        assertEquals(existing.absolutePath, result)
        assertTrue(existing.isFile)
    }

    @Test
    fun `a failed download leaves no partial file behind`() = runTest {
        failWith(IOException("network down"))

        provider().tileFor(POINT, ZOOM)

        val leftovers = cacheDir.listFiles().orEmpty().filter { it.name.endsWith(".part") }
        assertTrue("temp files left: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `a throttled source stops being asked`() = runTest {
        respondWith(code = 429)
        val provider = provider()

        provider.tileFor(POINT, ZOOM)
        val second = provider.tileFor(POINT, ZOOM)

        assertNull(second)
        // The second read issues nothing: retrying through a throttle is what turns it into a ban.
        assertEquals(1, requestedUrls.size)
    }

    @Test
    fun `a successful download evicts tiles past retention but keeps the new one`() = runTest {
        val stale = File(cacheDir.also { it.mkdirs() }, "tile_15_1_1.png").apply {
            // A different position than the one being fetched, so only age decides its fate.
            writeText("old")
            setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(9))
        }
        respondWith(code = 200)

        val fresh = provider().tileFor(POINT, ZOOM)

        assertFalse("expired tile survived: $stale", stale.isFile)
        assertTrue(File(fresh!!).isFile)
    }

    @Test
    fun `the requested tile addresses the point it was given`() = runTest {
        respondWith(code = 200)

        provider().tileFor(POINT, ZOOM)

        // Web Mercator for 49.8397, 24.0297 at zoom 15 - a wrong index silently shows another city,
        // which is the one tile defect that looks like a working map.
        assertEquals("https://tile.openstreetmap.org/15/18571/11135.png", requestedUrls.single())
    }

    private val requestedUrls = mutableListOf<String>()

    private fun respondWith(code: Int) {
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            requestedUrls += request.url.toString()
            mockk<Call> {
                every { execute() } returns Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("mocked")
                    .body(TILE_BYTES.toResponseBody("image/png".toMediaType()))
                    .build()
            }
        }
    }

    private fun failWith(error: IOException) {
        every { client.newCall(any()) } answers {
            requestedUrls += firstArg<Request>().url.toString()
            mockk<Call> { every { execute() } throws error }
        }
    }

    private fun seedTile(ageMs: Long): File = File(cacheDir.also { it.mkdirs() }, TILE_NAME).apply {
        writeText("cached")
        setLastModified(System.currentTimeMillis() - ageMs)
    }

    private companion object {
        val POINT = MapPoint(49.8397, 24.0297)
        const val ZOOM = OsmMapTileProvider.DEFAULT_ZOOM
        const val TILE_NAME = "tile_15_18571_11135.png"
        const val TILE_BYTES = "png-bytes"
    }
}

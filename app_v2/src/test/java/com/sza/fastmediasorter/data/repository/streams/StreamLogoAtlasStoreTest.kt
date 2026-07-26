package com.sza.fastmediasorter.data.repository.streams

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * S1201: sidecar parser + payload resolver for [StreamLogoAtlasStore]. The store is read-only (the
 * downloader writes the payload), so the tests place files directly under the delivery dir. Robolectric
 * supplies a real app context (and `org.json`) so the JSON parse and `filesDir` paths run as on device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamLogoAtlasStoreTest {

    private val context = RuntimeEnvironment.getApplication()
    private val store = StreamLogoAtlasStore(context)

    private fun deliveryDir(): File =
        File(File(context.filesDir, "delivery"), "STREAM_LOGO_ATLAS").apply { mkdirs() }

    private fun writeCoords(json: String) {
        File(deliveryDir(), "stream-logo-coords.json").writeText(json, Charsets.UTF_8)
    }

    @Test
    fun `well-formed url to index json yields the expected map`() = runTest {
        writeCoords("""{"http://radio/a.mp3":0,"http://radio/b.mp3":59,"http://radio/c.mp3":120}""")

        assertEquals(
            mapOf("http://radio/a.mp3" to 0, "http://radio/b.mp3" to 59, "http://radio/c.mp3" to 120),
            store.coords()
        )
    }

    @Test
    fun `stations sharing one site share one tile index`() = runTest {
        // The packer emits one tile per distinct logo, so several urls legitimately map to the same
        // index. The store must carry that through rather than treating it as a conflict.
        writeCoords("""{"http://net/jazz.mp3":7,"http://net/rock.mp3":7}""")

        val coords = store.coords()
        assertEquals(7, coords["http://net/jazz.mp3"])
        assertEquals(7, coords["http://net/rock.mp3"])
    }

    @Test
    fun `non-integer valued entry is skipped without throwing`() = runTest {
        writeCoords("""{"http://radio/a.mp3":5,"http://radio/bad.mp3":"nope"}""")

        val coords = store.coords()
        assertEquals(5, coords["http://radio/a.mp3"])
        assertNull("a non-integer entry must be dropped, not crash", coords["http://radio/bad.mp3"])
    }

    @Test
    fun `corrupt json yields empty map without throwing`() = runTest {
        writeCoords("{ this is not json")

        assertTrue(store.coords().isEmpty())
    }

    @Test
    fun `absent sidecar yields empty map and null atlas file`() = runTest {
        // Nothing written under the delivery dir.
        assertTrue(store.coords().isEmpty())
        assertNull("atlas must be absent before download", store.atlasFile())
    }
}

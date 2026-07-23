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
 * S1154: sidecar parser + payload resolver for [ChannelPreviewAtlasStore]. The store is read-only (the
 * downloader writes the payload), so the tests place files directly under the delivery dir. Robolectric
 * supplies a real app context (and `org.json`) so the JSON parse and `filesDir` paths run as on device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelPreviewAtlasStoreTest {

    private val context = RuntimeEnvironment.getApplication()
    private val store = ChannelPreviewAtlasStore(context)

    private fun deliveryDir(): File =
        File(File(context.filesDir, "delivery"), "CHANNEL_PREVIEW_ATLAS").apply { mkdirs() }

    private fun writeCoords(json: String) {
        File(deliveryDir(), "channel-preview-coords.json").writeText(json, Charsets.UTF_8)
    }

    @Test
    fun `well-formed url to index json yields the expected map`() = runTest {
        writeCoords("""{"http://chan/a.m3u8":0,"http://chan/b.m3u8":33,"http://chan/c.m3u8":68}""")

        assertEquals(
            mapOf("http://chan/a.m3u8" to 0, "http://chan/b.m3u8" to 33, "http://chan/c.m3u8" to 68),
            store.coords()
        )
    }

    @Test
    fun `non-integer valued entry is skipped without throwing`() = runTest {
        writeCoords("""{"http://chan/a.m3u8":5,"http://chan/bad.m3u8":"nope"}""")

        val coords = store.coords()
        assertEquals(5, coords["http://chan/a.m3u8"])
        assertNull("a non-integer entry must be dropped, not crash", coords["http://chan/bad.m3u8"])
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

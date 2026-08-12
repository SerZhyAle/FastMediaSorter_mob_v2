package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1483: the manifest decides whether the app offers newer artwork, so its FAILURE paths matter more
 * than its happy path. Every one of them must read as "nothing new": a manifest that inverts on a
 * malformed body would either nag every user forever or hide every future rebuild.
 */
// Robolectric supplies a real org.json: this module sets `isReturnDefaultValues = true`, so on the
// plain JVM runner every JSONObject call answers 0/null and every one of these assertions would pass
// or fail for the wrong reason.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class ArtworkManifestParseTest {

    private val client = ArtworkManifestClient(OkHttpClient())

    @Test
    fun `a well-formed manifest yields stamps and sizes`() {
        val manifest = client.parse(
            """
            {
              "schemaVersion": 1,
              "generatedAt": "2026-08-07T14:58:44Z",
              "sets": {
                "channelPreview": { "stamp": "aaa", "files": [ { "name": "p.zip", "size": 10 }, { "name": "c.json", "size": 5 } ] },
                "streamLogo": { "stamp": "bbb", "files": [ { "name": "l.zip", "size": 7 } ] }
              }
            }
            """.trimIndent()
        )

        requireNotNull(manifest)
        assertEquals("aaa", manifest.sets[DeliverableSet.CHANNEL_PREVIEW_ATLAS]?.stamp)
        assertEquals(15L, manifest.sets[DeliverableSet.CHANNEL_PREVIEW_ATLAS]?.totalBytes)
        assertEquals("bbb", manifest.sets[DeliverableSet.STREAM_LOGO_ATLAS]?.stamp)
        assertEquals(7L, manifest.sets[DeliverableSet.STREAM_LOGO_ATLAS]?.totalBytes)
    }

    @Test
    fun `an unknown schema version is ignored rather than guessed at`() {
        assertNull(client.parse("""{ "schemaVersion": 99, "sets": { "streamLogo": { "stamp": "x" } } }"""))
    }

    @Test
    fun `a malformed body reads as nothing new`() {
        assertNull(client.parse("not json at all"))
        assertNull(client.parse(""))
    }

    @Test
    fun `a set without a stamp is dropped, not defaulted`() {
        val manifest = client.parse(
            """{ "schemaVersion": 1, "sets": { "streamLogo": { "files": [ { "name": "l.zip", "size": 7 } ] } } }"""
        )
        requireNotNull(manifest)
        assertNull(manifest.sets[DeliverableSet.STREAM_LOGO_ATLAS])
    }

    @Test
    fun `a manifest with no sets parses to an empty map`() {
        val manifest = client.parse("""{ "schemaVersion": 1, "generatedAt": "now" }""")
        requireNotNull(manifest)
        assertEquals(0, manifest.sets.size)
    }
}

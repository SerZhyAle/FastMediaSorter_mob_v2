package com.sza.fastmediasorter.data.repository.streams

import com.sza.fastmediasorter.data.repository.StreamCatalogCsvParser
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.fastmediasorter.domain.usecase.streams.StreamMediaKindClassifier
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S0668 / PHASE_03: sidecar round-trip for [FaviconAtlasStore] plus the backward/forward-compat
 * coverage on the additive zip-walk extraction in [ImportStreamCatalogUseCase.extractCatalog].
 * Robolectric supplies a real app context (and `org.json`), so the JSON sidecar and `filesDir` paths
 * are exercised as on device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaviconAtlasStoreTest {

    private val context = RuntimeEnvironment.getApplication()
    private val store = FaviconAtlasStore(context)

    @Test
    fun `write then read returns the same atlas bytes and url-keyed coords`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        store.write(bytes, mapOf("u1" to 0, "u2" to 17))

        assertEquals(mapOf("u1" to 0, "u2" to 17), store.coords())
        val atlas = store.atlasFile()
        assertNotNull("atlas file expected", atlas)
        assertArrayEquals(bytes, atlas!!.readBytes())
    }

    @Test
    fun `write null clears atlas and coords`() = runTest {
        store.write(byteArrayOf(9, 9), mapOf("u1" to 1))
        store.write(null, emptyMap())

        assertNull("atlas must be absent after a null write", store.atlasFile())
        assertTrue("coords must be empty after a null write", store.coords().isEmpty())
    }

    @Test
    fun `corrupt coords file yields empty map without throwing`() = runTest {
        store.write(byteArrayOf(1), mapOf("u1" to 4))
        // Corrupt the sidecar on disk.
        val coordsFile = context.filesDir.resolve("streams/favicon-coords.json")
        coordsFile.writeText("{ this is not json", Charsets.UTF_8)

        assertTrue(store.coords().isEmpty())
    }

    @Test
    fun `coords are keyed by url not id`() = runTest {
        store.write(byteArrayOf(1), mapOf("http://chan/a.m3u8" to 2))
        val coords = store.coords()
        assertEquals(2, coords["http://chan/a.m3u8"])
        assertNull(coords["a"]) // an id-style key must not be present
    }

    // --- Compat invariant: additive zip-walk extraction (no network) ---

    private fun useCase(): ImportStreamCatalogUseCase = ImportStreamCatalogUseCase(
        okHttpClient = mockk<OkHttpClient>(relaxed = true),
        parser = StreamCatalogCsvParser(),
        classifier = mockk<StreamMediaKindClassifier>(relaxed = true),
        repository = mockk<StreamSourceRepository>(relaxed = true),
        faviconAtlasStore = store,
        // S1469: these cases drive extractCatalog() directly, which never consults connectivity.
        networkContextAnalyzer = mockk(relaxed = true),
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `old catalog zip with only csv yields null atlas and intact csv`() {
        val csv = "url,name\nhttp://t1,Stream1\n"
        val zip = zipOf("streams.csv" to csv.toByteArray(Charsets.UTF_8))

        val payload = useCase().extractCatalog(ByteArrayInputStream(zip))

        assertNotNull(payload)
        assertEquals(csv, payload!!.csv)
        assertNull("an old catalog has no atlas entry", payload.atlasPng)
    }

    @Test
    fun `csv first then png yields both with csv not truncated`() {
        val csv = "url,name,favicon_index\nhttp://t1,Stream1,0\n"
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
        // CSV packed FIRST, atlas AFTER - the order the publish step uses.
        val zip = zipOf(
            "streams.csv" to csv.toByteArray(Charsets.UTF_8),
            "favicon-atlas.png" to png,
        )

        val payload = useCase().extractCatalog(ByteArrayInputStream(zip))

        assertNotNull(payload)
        assertEquals("CSV must survive a later PNG entry", csv, payload!!.csv)
        assertNotNull("atlas after the CSV must still be captured", payload.atlasPng)
        assertArrayEquals(png, payload.atlasPng)
    }
}

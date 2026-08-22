package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamCatalogCsvParser
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * S0570: download the curated FastMediaSorter stream catalog (a zip GitHub Release asset containing
 * `streams.csv`), unzip + parse it, and merge-with-prune into the catalog-origin stream sources.
 * Reuses the shared [OkHttpClient]; the catalog is always fetched fresh (not SHA-pinned).
 */
class ImportStreamCatalogUseCase @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val parser: StreamCatalogCsvParser,
    private val classifier: StreamMediaKindClassifier,
    private val repository: StreamSourceRepository,
    // S0668: persists the favicon sprite-atlas + url->index sidecar extracted from the same zip.
    private val faviconAtlasStore: FaviconAtlasStore,
    // S1469: connectivity gate. Sits in the use case rather than in its callers so all four entry
    // points are covered at once; without it a dead network is only reported once OkHttp has timed out.
    private val networkContextAnalyzer: NetworkContextAnalyzer
) {
    suspend operator fun invoke(): CatalogImportResult = withContext(Dispatchers.IO) {
        Timber.i("Stream catalog import: starting")
        if (!networkContextAnalyzer.hasAnyNetwork()) {
            Timber.w("Stream catalog import failed: %s", "no network")
            return@withContext CatalogImportResult.Failure("no network")
        }
        val payload = try {
            downloadCatalog()
        } catch (e: Exception) {
            Timber.w(e, "Stream catalog import failed: %s", "download/unzip")
            return@withContext CatalogImportResult.Failure(e.message ?: "download error")
        }

        if (payload == null) {
            Timber.w("Stream catalog import: no streams.csv entry found in archive")
            return@withContext CatalogImportResult.Empty
        }

        val entries = try {
            parser.parse(payload.csv)
        } catch (e: Exception) {
            Timber.w(e, "Stream catalog import failed: %s", "parse")
            return@withContext CatalogImportResult.Failure(e.message ?: "parse error")
        }

        if (entries.isEmpty()) return@withContext CatalogImportResult.Empty

        // S0668: persist the favicon atlas + url->index sidecar before merging the rows. Keyed by url
        // (stable unique index) not the per-import random id. A sidecar write failure must NOT fail the
        // import - the rows still merge; favicons just stay absent until the next import.
        try {
            val coords = entries
                .filter { it.faviconIndex != null }
                .associate { it.url to it.faviconIndex!! }
            faviconAtlasStore.write(payload.atlasPng, coords)
            // Favicons vanishing after an import used to be undiagnosable from a device log: the whole
            // path was silent, so a favicon-less archive and a failed write looked identical. Log the
            // two numbers that separate them.
            Timber.i(
                "Stream catalog favicons: atlas=%d bytes, indexed rows=%d/%d",
                payload.atlasPng?.size ?: 0,
                coords.size,
                entries.size
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.w(e, "Stream catalog import: favicon sidecar write failed (rows still merge)")
        }

        val now = System.currentTimeMillis()
        val sources = entries.map { entry ->
            StreamSourceEntity(
                id = UUID.randomUUID().toString(),
                url = entry.url,
                title = entry.name,
                // Prefer the catalog-declared kind; fall back to scheme/extension classification.
                mediaKind = entry.mediaKind.uppercase().ifBlank { classifier.classify(entry.url) },
                sourceOrigin = "CATALOG",
                sortIndex = 0,
                addedAt = now,
                category = entry.category.ifBlank { null },
                topic = entry.topic.ifBlank { null },
                language = entry.language.ifBlank { null },
                country = entry.country.ifBlank { null },
                // S1117: carry the region-restriction flag ("geo") so the list can badge it; blank -> null.
                access = entry.access.ifBlank { null }
            )
        }

        val merge = try {
            repository.mergeCatalog(sources)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.w(e, "Stream catalog import failed: %s", "merge")
            return@withContext CatalogImportResult.Failure(e.message ?: "merge error")
        }

        Timber.i("Stream catalog import done: +%d ~%d -%d", merge.added, merge.updated, merge.removed)
        CatalogImportResult.Success(
            added = merge.added,
            updated = merge.updated,
            removed = merge.removed
        )
    }

    /**
     * Downloads the catalog zip and returns its [CatalogPayload] (CSV text + optional atlas PNG), or
     * null when the archive has no usable CSV. The zip is consumed in a SINGLE pass capturing both
     * entries; per-entry reads are capped to guard against a malformed/oversized zip.
     */
    private fun downloadCatalog(): CatalogPayload? {
        val request = Request.Builder().url(CATALOG_URL).build()
        // Derive a client that keeps the shared pool/dispatcher and 10s connect/read/write timeouts
        // but adds the overall call deadline the shared client lacks, so a slow-trickle host (one that
        // resets the read timeout on each tiny chunk) cannot hold the import coroutine indefinitely.
        val client = okHttpClient.newBuilder()
            .callTimeout(CATALOG_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val stream = response.body?.byteStream() ?: error("empty response body")
            return extractCatalog(stream)
        }
    }

    /**
     * S0668: walks the catalog zip ONCE, capturing the CSV text AND the atlas PNG bytes. The
     * PNG-extraction branch is ADDITIVE (compat invariant): every non-matched entry is still skipped via
     * `closeEntry()`, the CSV is still read via [readCappedUtf8], and an OLD catalog with no atlas entry
     * yields `atlasPng == null` with the CSV intact. Because the publish step packs the CSV FIRST, the
     * walk does NOT early-return on the CSV match (that would skip a later PNG entry) - both locals are
     * captured and the payload is built after the walk. `internal` so the extraction is unit-testable
     * from an in-memory zip without the network.
     */
    internal fun extractCatalog(stream: java.io.InputStream): CatalogPayload? {
        ZipInputStream(stream).use { zip ->
            var streamsCsv: String? = null
            var fallbackCsv: String? = null
            var atlasPng: ByteArray? = null
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                when {
                    entry.isDirectory -> Unit
                    name.endsWith(".csv") -> {
                        val text = readCappedUtf8(zip)
                        if (name.endsWith("streams.csv")) streamsCsv = text
                        else if (fallbackCsv == null) fallbackCsv = text
                    }
                    name.endsWith("favicon-atlas.png") -> {
                        // Over-cap atlas -> drop the atlas, KEEP the CSV (favicons just stay absent).
                        atlasPng = readCappedBytes(zip, MAX_ATLAS_BYTES)
                    }
                    // Any other entry is skipped, exactly as today (skip-unknown preserved).
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            val csv = streamsCsv ?: fallbackCsv ?: return null
            return CatalogPayload(csv = csv, atlasPng = atlasPng)
        }
    }

    /**
     * Reads the current zip entry as UTF-8. S1820: an over-cap CSV THROWS rather than returning null.
     * Returning null here collapsed into the same `Empty` result as an archive holding no CSV at all,
     * so the day the bank outgrew the cap every user would have seen "catalog is empty" with nothing in
     * the log to tell the two apart. The throw is caught by the `downloadCatalog()` caller and reported
     * as a Failure naming the cap. The atlas path deliberately keeps the null tolerance - there
     * over-cap means "drop the atlas, keep the CSV" (S0668).
     */
    private fun readCappedUtf8(zip: ZipInputStream): String {
        val bytes = readCappedBytes(zip, MAX_CSV_BYTES)
            ?: error("catalog CSV exceeds the ${MAX_CSV_BYTES / BYTES_PER_MIB} MiB cap")
        return String(bytes, Charsets.UTF_8)
    }

    /** Reads the current zip entry's bytes, returning null if they exceed [cap]. */
    private fun readCappedBytes(zip: ZipInputStream, cap: Int): ByteArray? {
        val buffer = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read == -1) break
            total += read
            if (total > cap) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** S0668: the two things extracted from the catalog zip - the CSV text and an optional atlas PNG. */
    internal data class CatalogPayload(val csv: String, val atlasPng: ByteArray?)

    sealed interface CatalogImportResult {
        data class Success(val added: Int, val updated: Int, val removed: Int) : CatalogImportResult
        data object Empty : CatalogImportResult
        data class Failure(val reason: String) : CatalogImportResult
    }

    private companion object {
        const val CATALOG_URL =
            "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip"

        const val BYTES_PER_MIB = 1024 * 1024

        // S1820: raised from 8 MiB. Measured 2026-08-19, the published streams.csv is 5.83 MB across
        // 19534 rows, up from 2691 rows a month earlier - the old cap left 1.4x headroom against
        // sevenfold monthly growth, so the bank would have outgrown it silently. Re-derive this from a
        // fresh measurement of the published asset, not from memory, when it next needs review.
        const val MAX_CSV_BYTES = 32 * BYTES_PER_MIB

        // S0668: atlas cap, separate from the CSV cap. A 16-col grid of 32 px tiles for a partial-coverage
        // catalog compresses well under this; over-cap drops the atlas but keeps the CSV.
        const val MAX_ATLAS_BYTES = 30 * 1024 * 1024

        // Hard ceiling on the whole catalog fetch (DNS + connect + write + zip body read). S1820 raised
        // it from 30 s: the published zip is 7.31 MB, which 30 s demanded be pulled at ~250 KB/s with no
        // dip, so an ordinary mobile link failed the whole import with no resume and no partial apply.
        // 180 s drops the required floor to ~42 KB/s. The ceiling stays finite on purpose - see the
        // downloadCatalog KDoc: a host that trickles bytes resets the read timeout on every chunk and
        // would otherwise hold the import coroutine forever. (The comment here used to claim it mirrored
        // di/LinkDownloadModule.kt; that client has had callTimeout(0) since S1139, so the claim had been
        // false and pointed the wrong way.)
        const val CATALOG_CALL_TIMEOUT_SECONDS = 180L
    }
}

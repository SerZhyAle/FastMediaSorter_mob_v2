package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamCatalogCsvParser
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
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
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(): CatalogImportResult = withContext(Dispatchers.IO) {
        val csvText = try {
            downloadCsv()
        } catch (e: Exception) {
            Timber.w(e, "Stream catalog import failed: %s", "download/unzip")
            return@withContext CatalogImportResult.Failure(e.message ?: "download error")
        }

        if (csvText == null) {
            Timber.w("Stream catalog import: no streams.csv entry found in archive")
            return@withContext CatalogImportResult.Empty
        }

        val entries = try {
            parser.parse(csvText)
        } catch (e: Exception) {
            Timber.w(e, "Stream catalog import failed: %s", "parse")
            return@withContext CatalogImportResult.Failure(e.message ?: "parse error")
        }

        if (entries.isEmpty()) return@withContext CatalogImportResult.Empty

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
                language = entry.language.ifBlank { null }
            )
        }

        val merge = try {
            repository.mergeCatalog(sources)
        } catch (e: Exception) {
            Timber.w(e, "Stream catalog import failed: %s", "merge")
            return@withContext CatalogImportResult.Failure(e.message ?: "merge error")
        }

        CatalogImportResult.Success(
            added = merge.added,
            updated = merge.updated,
            removed = merge.removed
        )
    }

    /**
     * Downloads the catalog zip and returns the first CSV entry decoded as UTF-8, or null when the
     * archive has no usable CSV. Per-entry reads are capped to guard against a malformed/oversized
     * zip; entries above the cap are skipped rather than buffered.
     */
    private fun downloadCsv(): String? {
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
            ZipInputStream(stream).use { zip ->
                var fallback: String? = null
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (!entry.isDirectory && name.endsWith(".csv")) {
                        val text = readCappedUtf8(zip)
                        if (text != null) {
                            if (name.endsWith("streams.csv")) return text
                            if (fallback == null) fallback = text
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                return fallback
            }
        }
    }

    /** Reads the current zip entry as UTF-8, returning null if it exceeds [MAX_CSV_BYTES]. */
    private fun readCappedUtf8(zip: ZipInputStream): String? {
        val buffer = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read == -1) break
            total += read
            if (total > MAX_CSV_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    sealed interface CatalogImportResult {
        data class Success(val added: Int, val updated: Int, val removed: Int) : CatalogImportResult
        data object Empty : CatalogImportResult
        data class Failure(val reason: String) : CatalogImportResult
    }

    private companion object {
        const val CATALOG_URL =
            "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip"
        const val MAX_CSV_BYTES = 8 * 1024 * 1024

        // Hard ceiling on the whole catalog fetch (DNS + connect + write + zip body read). Mirrors the
        // download-client callTimeout in di/LinkDownloadModule.kt; generous for a small catalog zip.
        const val CATALOG_CALL_TIMEOUT_SECONDS = 30L
    }
}

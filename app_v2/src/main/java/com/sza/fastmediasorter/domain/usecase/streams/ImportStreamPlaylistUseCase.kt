package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.M3uPlaylistParser
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ImportStreamPlaylistUseCase @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val parser: M3uPlaylistParser,
    private val classifier: StreamMediaKindClassifier,
    private val repository: StreamSourceRepository,
    private val statsSink: StatsSink,
) {
    suspend operator fun invoke(listUrl: String): ImportResult = withContext(Dispatchers.IO) {
        val body = try {
            download(listUrl)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.w(e, "Stream playlist import failed: download error for %s", listUrl)
            return@withContext ImportResult.Failure(e.message ?: "download error")
        }

        // USER-PLAYLIST 0.10 section 6 item A: the JSON carrier is optional and not read here, and the
        // M3U parser would take every non-comment JSON line for a stream URL, so it is refused whole.
        if (isJsonPlaylist(listUrl, body)) {
            Timber.w("Stream playlist import refused: JSON playlist carrier at %s", listUrl)
            return@withContext ImportResult.UnsupportedFormat
        }

        val entries = try {
            parser.parse(body)
        } catch (e: Exception) {
            Timber.w(e, "Stream playlist import failed: parse error for %s", listUrl)
            return@withContext ImportResult.Failure(e.message ?: "parse error")
        }

        if (entries.isEmpty()) return@withContext ImportResult.Empty

        val now = System.currentTimeMillis()
        val sources = entries.map { entry ->
            StreamSourceEntity(
                id = UUID.randomUUID().toString(),
                url = entry.url,
                title = entry.title,
                mediaKind = classifier.classify(entry.url),
                sourceOrigin = "IMPORTED",
                sortIndex = 0,
                addedAt = now
            )
        }
        val inserted = repository.addAllIgnoringDuplicates(sources)
        // Counts the sources this import actually added (duplicates ignored), accumulated all-time.
        statsSink.record(StatsEvent.PlaylistImported(count = inserted.toLong()))
        ImportResult.Success(inserted)
    }

    /**
     * The URL is user-typed, so it may be a large media file or a live stream that never reaches EOF.
     * The shared client has no call deadline, hence the derived one; the body is read under a byte cap,
     * and cancellation is checked per chunk because the blocking read does not observe it on its own.
     */
    private suspend fun download(listUrl: String): String {
        val request = Request.Builder().url(listUrl).build()
        val client = okHttpClient.newBuilder()
            .callTimeout(PLAYLIST_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("empty response body")
            return readCapped(body)
        }
    }

    private suspend fun readCapped(body: ResponseBody): String {
        val stream = body.byteStream()
        val buffer = ByteArray(READ_CHUNK_BYTES)
        val out = ByteArrayOutputStream()
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = stream.read(buffer)
            if (read == -1) break
            if (out.size() + read > MAX_PLAYLIST_BYTES) {
                error("playlist exceeds the ${MAX_PLAYLIST_BYTES / BYTES_PER_MIB} MiB cap")
            }
            out.write(buffer, 0, read)
        }
        // Re-wrapping keeps ResponseBody.string()'s charset and BOM handling for the capped bytes.
        return out.toByteArray().toResponseBody(body.contentType()).string()
    }

    private fun isJsonPlaylist(listUrl: String, body: String): Boolean {
        val path = listUrl.substringBefore('#').substringBefore('?')
        if (path.endsWith(JSON_EXTENSION, ignoreCase = true)) return true
        val firstChar = body.trimStart(BYTE_ORDER_MARK, ' ', '\t', '\r', '\n').firstOrNull()
        return firstChar == '{' || firstChar == '['
    }

    sealed interface ImportResult {
        data class Success(val inserted: Int) : ImportResult
        data object Empty : ImportResult
        data object UnsupportedFormat : ImportResult
        data class Failure(val reason: String) : ImportResult
    }

    internal companion object {
        private const val BYTES_PER_MIB = 1024 * 1024
        private const val READ_CHUNK_BYTES = 8 * 1024
        private const val JSON_EXTENSION = ".json"
        private const val BYTE_ORDER_MARK = '\uFEFF'

        // Large IPTV lists run to a few MiB; 16 MiB leaves headroom while keeping a media file
        // pasted by mistake from filling the heap.
        const val MAX_PLAYLIST_BYTES = 16 * BYTES_PER_MIB

        // Finite on purpose: a host that trickles bytes resets the read timeout on every chunk.
        private const val PLAYLIST_CALL_TIMEOUT_SECONDS = 120L
    }
}

package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.M3uPlaylistParser
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

class ImportStreamPlaylistUseCase @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val parser: M3uPlaylistParser,
    private val classifier: StreamMediaKindClassifier,
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(listUrl: String): ImportResult = withContext(Dispatchers.IO) {
        val body = try {
            download(listUrl)
        } catch (e: Exception) {
            Timber.w(e, "Stream playlist import failed: download error for %s", listUrl)
            return@withContext ImportResult.Failure(e.message ?: "download error")
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
        ImportResult.Success(inserted)
    }

    private fun download(listUrl: String): String {
        val request = Request.Builder().url(listUrl).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string() ?: error("empty response body")
        }
    }

    sealed interface ImportResult {
        data class Success(val inserted: Int) : ImportResult
        data object Empty : ImportResult
        data class Failure(val reason: String) : ImportResult
    }
}

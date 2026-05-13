package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * S0177: Native extractor for artstation.com.
 *
 * Uses the internal ArtStation projects JSON API — no WebView needed.
 * Public artwork does not require authentication.
 */
@Singleton
class ArtStationExtractionStrategy @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
    private val direct: DirectFileExtractionStrategy,
) : UrlExtractionStrategy {

    override val id: String = "artstation"

    override suspend fun probe(url: String): ProbeResult {
        val host = url.toHttpUrlOrNull()?.host ?: return ProbeResult.NotApplicable
        return if (host.endsWith("artstation.com")) {
            ProbeResult.Applicable(tentativeMime = null, tentativeSizeBytes = null)
        } else {
            ProbeResult.NotApplicable
        }
    }

    override suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrlOrNull()
            ?: return@withContext OpenResult.NotFound("artstation_invalid_url")
        val slug = parsed.pathSegments.lastOrNull { it.isNotBlank() }
            ?: return@withContext OpenResult.NotFound("artstation_no_slug")

        val apiUrl = "https://www.artstation.com/projects/$slug.json"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.artstation.com/")
            .build()

        val body = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext OpenResult.NotFound("artstation_api_error_${response.code}")
                }
                response.body?.string()
                    ?: return@withContext OpenResult.NotFound("artstation_empty_body")
            }
        } catch (e: IOException) {
            Timber.w(e, "ArtStationExtractor: open failed for %s", url)
            return@withContext OpenResult.Error(e)
        }

        val mediaUrl = runCatching { parseMediaUrl(body) }.getOrElse { e ->
            Timber.w(e, "ArtStationExtractor: parse failed for %s", url)
            null
        } ?: return@withContext OpenResult.NotFound("artstation_parse_failed")

        direct.open(
            url = mediaUrl,
            onProgress = onProgress,
            extraHeaders = mapOf("Referer" to "https://www.artstation.com/"),
        )
    }

    private fun parseMediaUrl(json: String): String? {
        val root = JSONObject(json)
        val assets = root.optJSONArray("assets") ?: return null

        // Prefer video clip over image.
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("asset_type") == "video_clip") {
                val videoUrl = asset.optString("video_clip_url").takeIf { it.isNotBlank() }
                if (videoUrl != null) return videoUrl
            }
        }
        // Fall back to first available image_url.
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val imageUrl = asset.optString("image_url").takeIf { it.isNotBlank() }
            if (imageUrl != null) return imageUrl
        }
        return null
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (compatible; FastMediaSorter)"
    }
}

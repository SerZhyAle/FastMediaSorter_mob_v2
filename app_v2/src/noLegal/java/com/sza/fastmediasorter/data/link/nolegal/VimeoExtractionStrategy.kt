package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * S0177: Native extractor for vimeo.com.
 *
 * Fetches the Vimeo player config JSON to extract a direct MP4 stream or HLS manifest.
 * Password-protected videos return NotFound — WebView-dynamic handles them as fallback.
 * Requires Referer: https://vimeo.com/ for player.vimeo.com config requests.
 */
@Singleton
class VimeoExtractionStrategy @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
    private val direct: DirectFileExtractionStrategy,
) : UrlExtractionStrategy {

    override val id: String = "vimeo"

    override suspend fun probe(url: String): ProbeResult {
        val host = url.toHttpUrlOrNull()?.host ?: return ProbeResult.NotApplicable
        return if (host.endsWith("vimeo.com")) {
            ProbeResult.Applicable(tentativeMime = null, tentativeSizeBytes = null)
        } else {
            ProbeResult.NotApplicable
        }
    }

    override suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url)
            ?: return@withContext OpenResult.NotFound("vimeo_no_id")

        val configUrl = "https://player.vimeo.com/video/$videoId/config"
        val request = Request.Builder()
            .url(configUrl)
            .header("Referer", "https://vimeo.com/")
            .header("User-Agent", USER_AGENT)
            .build()

        val configJson = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 403 indicates password-protected or private — fallback to WebView-dynamic.
                    return@withContext OpenResult.NotFound("vimeo_config_failed")
                }
                response.body?.string()
                    ?: return@withContext OpenResult.NotFound("vimeo_config_failed")
            }
        } catch (e: IOException) {
            Timber.w(e, "VimeoExtractor: config fetch failed for id=%s", videoId)
            return@withContext OpenResult.Error(e)
        }

        val extracted = runCatching { extractStreamInfo(configJson, videoId) }.getOrElse { e ->
            Timber.w(e, "VimeoExtractor: config parse failed for id=%s", videoId)
            null
        } ?: return@withContext OpenResult.NotFound("vimeo_parse_failed")

        when (extracted) {
            is StreamInfo.Progressive -> direct.open(
                url = extracted.url,
                onProgress = onProgress,
                extraHeaders = mapOf("Referer" to "https://vimeo.com/"),
            )
            is StreamInfo.Hls -> OpenResult.Streaming(
                manifest = StreamingManifest.Hls(manifestUrl = extracted.url),
                tentativeFileName = "vimeo_$videoId.mp4",
            )
        }
    }

    private fun extractVideoId(url: String): String? {
        val match = VIDEO_ID_REGEX.find(url) ?: return null
        return match.groupValues[1].takeIf { it.isNotBlank() }
    }

    private fun extractStreamInfo(json: String, videoId: String): StreamInfo? {
        val root = JSONObject(json)
        val files = root.optJSONObject("request")?.optJSONObject("files")
            ?: return null

        // Prefer progressive MP4 streams, sorted descending by width (highest resolution first).
        val progressive = files.optJSONArray("progressive")
        if (progressive != null && progressive.length() > 0) {
            val best = (0 until progressive.length())
                .mapNotNull { progressive.optJSONObject(it) }
                .filter { it.optString("url").isNotBlank() }
                .maxByOrNull { it.optInt("width", 0) }
            if (best != null) {
                return StreamInfo.Progressive(best.optString("url"))
            }
        }

        // Fallback: HLS manifest.
        val hlsUrl = files.optJSONObject("hls")?.optString("url")?.takeIf { it.isNotBlank() }
        if (hlsUrl != null) return StreamInfo.Hls(hlsUrl)

        return null
    }

    private sealed interface StreamInfo {
        data class Progressive(val url: String) : StreamInfo
        data class Hls(val url: String) : StreamInfo
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (compatible; FastMediaSorter)"
        val VIDEO_ID_REGEX = Regex(
            "vimeo\\.com/(?:video/|channels/\\S+/|groups/\\S+/videos/|album/\\d+/video/|manage/videos/|review/)?(?<id>\\d+)",
        )
    }
}

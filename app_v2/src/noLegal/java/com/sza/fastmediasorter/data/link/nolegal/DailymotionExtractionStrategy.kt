package com.sza.fastmediasorter.data.link.nolegal

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
 * S0177: Native extractor for dailymotion.com.
 *
 * Parses the embed page HTML to extract a time-limited HLS stream URL.
 * Stream URLs are IP-bound and expire quickly - extraction and playback must start immediately.
 * No DirectFileExtractionStrategy needed - result is always HLS streaming.
 *
 * Note: Dailymotion's public REST API requires an Enterprise key for stream_url.
 * Embed-page parsing is the only reliable approach for free access.
 */
@Singleton
class DailymotionExtractionStrategy @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
) : UrlExtractionStrategy {

    override val id: String = "dailymotion"

    override suspend fun probe(url: String): ProbeResult {
        val host = url.toHttpUrlOrNull()?.host ?: return ProbeResult.NotApplicable
        return if (host.endsWith("dailymotion.com")) {
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
            ?: return@withContext OpenResult.NotFound("dailymotion_no_id")

        val embedUrl = "https://www.dailymotion.com/embed/video/$videoId"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.dailymotion.com/")
            .build()

        val html = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext OpenResult.NotFound("dailymotion_embed_failed")
                }
                response.body?.string()
                    ?: return@withContext OpenResult.NotFound("dailymotion_embed_failed")
            }
        } catch (e: IOException) {
            Timber.w(e, "DailymotionExtractor: embed fetch failed for id=%s", videoId)
            return@withContext OpenResult.Error(e)
        }

        val hlsUrl = runCatching { parseHlsUrl(html) }.getOrElse { e ->
            Timber.w(e, "DailymotionExtractor: parse failed for id=%s", videoId)
            null
        }

        if (hlsUrl == null) {
            Timber.w("DailymotionExtractor: parse failed for %s", url)
            return@withContext OpenResult.NotFound("dailymotion_parse_failed")
        }

        // Return immediately - stream URLs are time-limited and IP-bound.
        OpenResult.Streaming(
            manifest = StreamingManifest.Hls(manifestUrl = hlsUrl),
            tentativeFileName = "dailymotion_$videoId.mp4",
        )
    }

    private fun extractVideoId(url: String): String? {
        val match = VIDEO_ID_REGEX.find(url) ?: return null
        return match.groupValues[1].takeIf { it.isNotBlank() }
    }

    private fun parseHlsUrl(html: String): String? {
        // Primary: window.__PLAYER_CONFIG__ JSON block.
        val configMatch = PLAYER_CONFIG_REGEX.find(html)
        if (configMatch != null) {
            val json = configMatch.groupValues[1]
            val hlsUrl = runCatching { extractHlsFromConfig(json) }.getOrNull()
            if (hlsUrl != null) return hlsUrl
        }

        // Fallback: locate any .m3u8 URL embedded in the page.
        val m3u8Match = M3U8_URL_REGEX.find(html)
        if (m3u8Match != null) {
            val raw = m3u8Match.groupValues[1]
            // Unescape common JSON escapes.
            return raw.replace("\\/", "/").takeIf { it.startsWith("http") }
        }

        return null
    }

    private fun extractHlsFromConfig(json: String): String? {
        val root = JSONObject(json)

        // Path 1: metadata.qualities.auto[0].url
        val metadata = root.optJSONObject("metadata")
        val qualities = metadata?.optJSONObject("qualities")
        val auto = qualities?.optJSONArray("auto")
        if (auto != null && auto.length() > 0) {
            val url = auto.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
            if (url != null) return url
        }

        // Path 2: first quality key, first item.
        if (qualities != null) {
            qualities.keys().forEach { key ->
                val arr = qualities.optJSONArray(key)
                val url = arr?.optJSONObject(0)?.optString("url")?.takeIf {
                    it.isNotBlank() && it.contains(".m3u8")
                }
                if (url != null) return url
            }
        }

        // Path 3: top-level qualities object.
        val topQualities = root.optJSONObject("qualities")
        if (topQualities != null) {
            val topAuto = topQualities.optJSONArray("auto")
            val url = topAuto?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
            if (url != null) return url
        }

        return null
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (compatible; FastMediaSorter)"
        val VIDEO_ID_REGEX = Regex(
            "dailymotion\\.com/(?:video/|embed/video/)?([a-zA-Z0-9]+)",
        )
        val PLAYER_CONFIG_REGEX = Regex(
            "__PLAYER_CONFIG__\\s*=\\s*(\\{.+?\\})\\s*;",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val M3U8_URL_REGEX = Regex(
            "\"(https?://[^\"]+\\.m3u8[^\"]*?)\"",
        )
    }
}

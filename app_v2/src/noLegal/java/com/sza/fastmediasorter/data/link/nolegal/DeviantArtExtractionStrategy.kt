package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
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
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * S0177: Native extractor for deviantart.com.
 *
 * Primary path: parses `window.__INITIAL_STATE__` JSON from the deviation page.
 * Fallback path: oEmbed API (returns preview-quality URL when primary parsing fails).
 * Cookies injected automatically by [LinkDownloadCookieJar] - no manual cookie handling needed.
 */
@Singleton
class DeviantArtExtractionStrategy @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
    private val direct: DirectFileExtractionStrategy,
) : UrlExtractionStrategy {

    override val id: String = "deviantart"

    override suspend fun probe(url: String): ProbeResult {
        val host = url.toHttpUrlOrNull()?.host ?: return ProbeResult.NotApplicable
        return if (host.endsWith("deviantart.com")) {
            ProbeResult.Applicable(tentativeMime = null, tentativeSizeBytes = null)
        } else {
            ProbeResult.NotApplicable
        }
    }

    override suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult = withContext(Dispatchers.IO) {
        // Primary path: fetch deviation page and parse __INITIAL_STATE__.
        val pageHtml = try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext OpenResult.NotFound("deviantart_fetch_error_${response.code}")
                }
                response.body?.string()
                    ?: return@withContext OpenResult.NotFound("deviantart_empty_body")
            }
        } catch (e: IOException) {
            Timber.w(e, "DeviantArtExtractor: page fetch failed for %s", url)
            return@withContext OpenResult.Error(e)
        }

        val primaryUrl = runCatching { parseInitialState(pageHtml) }.getOrElse { e ->
            Timber.w(e, "DeviantArtExtractor: __INITIAL_STATE__ parse failed for %s", url)
            null
        }

        if (primaryUrl != null) {
            return@withContext direct.open(
                url = primaryUrl,
                onProgress = onProgress,
                extraHeaders = mapOf("Referer" to "https://www.deviantart.com/"),
            )
        }

        // Fallback: oEmbed API - returns preview-quality URL.
        Timber.d("DeviantArtExtractor: fallback oEmbed for %s", url)
        val oEmbedUrl = runCatching { fetchOEmbed(url) }.getOrElse { e ->
            Timber.w(e, "DeviantArtExtractor: oEmbed failed for %s", url)
            null
        } ?: return@withContext OpenResult.NotFound("deviantart_parse_failed")

        direct.open(
            url = oEmbedUrl,
            onProgress = onProgress,
            extraHeaders = mapOf("Referer" to "https://www.deviantart.com/"),
        )
    }

    /**
     * Extracts original file URL from `window.__INITIAL_STATE__` JSON block in page HTML.
     * Navigates: `deviation.media.baseUri` + `deviation.media.prettyName`.
     */
    private fun parseInitialState(html: String): String? {
        // Match JSON object assigned to window.__INITIAL_STATE__ (DOT_ALL to span newlines).
        val match = INITIAL_STATE_REGEX.find(html) ?: return null
        val json = match.groupValues[1].takeIf { it.isNotBlank() } ?: return null

        val root = JSONObject(json)

        // Navigate @@initial -> deviation -> media.
        val atInitial = root.optJSONObject("@@initial") ?: root
        val deviation = atInitial.optJSONObject("deviation")
            ?: findDeviationInRoot(root)
            ?: return null
        val media = deviation.optJSONObject("media") ?: return null

        val baseUri = media.optString("baseUri").trimEnd('/')
        val prettyName = media.optString("prettyName")

        if (baseUri.isNotBlank() && prettyName.isNotBlank() && baseUri.startsWith("https://")) {
            return "$baseUri/$prettyName"
        }

        // Alternative: look for types[] entry with t == "full".
        val types = media.optJSONArray("types")
        if (types != null) {
            for (i in 0 until types.length()) {
                val entry = types.optJSONObject(i) ?: continue
                if (entry.optString("t") == "full") {
                    val c = entry.optString("c").takeIf { it.isNotBlank() } ?: continue
                    // c may be a URL template like "<baseUri>/f/<prettyName>".
                    val resolved = if (c.startsWith("http")) c else "$baseUri$c"
                    return resolved.takeIf { it.startsWith("https://") }
                }
            }
        }

        return null
    }

    /** Searches root JSON keys for a deviation object (handles varying nesting). */
    private fun findDeviationInRoot(root: JSONObject): JSONObject? {
        root.keys().forEach { key ->
            val child = root.optJSONObject(key) ?: return@forEach
            if (child.has("media") && child.has("deviationId")) return child
        }
        return null
    }

    private fun fetchOEmbed(pageUrl: String): String? {
        val encoded = URLEncoder.encode(pageUrl, "UTF-8")
        val apiUrl = "https://backend.deviantart.com/oembed?url=$encoded&format=json"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string() ?: return null
        }
        val json = JSONObject(body)
        return json.optString("url").takeIf { it.isNotBlank() && it.startsWith("http") }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (compatible; FastMediaSorter)"
        val INITIAL_STATE_REGEX = Regex(
            "window\\.__INITIAL_STATE__\\s*=\\s*(\\{.+?\\})\\s*;?\\s*</script>",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}

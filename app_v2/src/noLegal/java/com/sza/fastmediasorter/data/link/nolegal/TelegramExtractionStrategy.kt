package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.SiteBatchItem
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * S0303: Native extractor for public Telegram posts (`t.me/<channel>/<id>`), noLegal only.
 *
 * Fetches the login-free embed widget (`?embed=1`) and parses direct attachment URLs from the
 * stable `tgme_widget_message_*` markup: photos (background-image of `tgme_widget_message_photo_wrap`)
 * and progressive video/GIF (`tgme_widget_message_video[src]`). A single attachment delegates to
 * [DirectFileExtractionStrategy]; an album/grouped post returns [OpenResult.Batch] (each item is
 * re-resolved by the `"direct"` strategy). Private / invite / profile / bot-deeplink forms return a
 * specific [OpenResult.NotFound] reason so the cascade falls through to `"html"`/`"dynamic"`.
 *
 * NOTE: the `tgme_widget_message_*` selectors and Telegram CDN host shapes were pinned from
 * documented widget markup, not a live fetch (S0303 research env had no WebFetch). On any parse
 * miss this returns NotFound and the cascade continues, so selector drift degrades gracefully.
 */
@Singleton
class TelegramExtractionStrategy @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
    private val direct: DirectFileExtractionStrategy,
) : UrlExtractionStrategy {

    override val id: String = "telegram"

    override suspend fun probe(url: String): ProbeResult {
        val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return ProbeResult.NotApplicable
        return if (host in TELEGRAM_HOSTS) {
            ProbeResult.Applicable(tentativeMime = null, tentativeSizeBytes = null)
        } else {
            ProbeResult.NotApplicable
        }
    }

    override suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult = withContext(Dispatchers.IO) {
        Timber.d("S0303: telegram extraction open() for %s", url)
        val httpUrl = url.toHttpUrlOrNull()
            ?: return@withContext OpenResult.NotFound("telegram_bad_url")

        val post = classifyPost(httpUrl)
            ?: return@withContext OpenResult.NotFound(rejectReason(httpUrl))

        val embedUrl = "https://t.me/${post.channel}/${post.id}?embed=1"
        val html = try {
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext OpenResult.NotFound("telegram_fetch_error_${response.code}")
                }
                response.body?.string()
                    ?: return@withContext OpenResult.NotFound("telegram_empty_body")
            }
        } catch (e: IOException) {
            Timber.w(e, "TelegramExtractor: embed fetch failed for %s", embedUrl)
            return@withContext OpenResult.Error(e)
        }

        val mediaUrls = runCatching { parseMediaUrls(html) }.getOrElse { e ->
            Timber.w(e, "TelegramExtractor: parse failed for %s", embedUrl)
            emptyList()
        }

        when {
            mediaUrls.isEmpty() -> OpenResult.NotFound("telegram_no_media")
            mediaUrls.size == 1 -> direct.open(
                url = mediaUrls.first(),
                onProgress = onProgress,
                extraHeaders = mapOf("Referer" to "https://t.me/"),
            )
            else -> OpenResult.Batch(
                items = mediaUrls.take(MAX_BATCH_ITEMS).map { SiteBatchItem(url = it) },
                label = "${post.channel}/${post.id}",
            )
        }
    }

    /**
     * Resolve a supported public post to its (channel, id), canonicalising the web-preview form
     * (`/s/<channel>/<id>`). Returns null for unsupported forms (the caller maps the reason).
     */
    private fun classifyPost(httpUrl: HttpUrl): Post? {
        val segments = httpUrl.pathSegments.filter { it.isNotBlank() }
        val normalised = if (segments.firstOrNull() == "s") segments.drop(1) else segments
        if (normalised.size != 2) return null
        val (channel, id) = normalised
        if (!CHANNEL_REGEX.matches(channel)) return null
        if (!id.all { it.isDigit() }) return null
        return Post(channel, id)
    }

    private fun rejectReason(httpUrl: HttpUrl): String {
        val segments = httpUrl.pathSegments.filter { it.isNotBlank() }
        val first = segments.firstOrNull().orEmpty()
        return when {
            first == "c" -> "telegram_private_channel"
            first == "joinchat" || first.startsWith("+") -> "telegram_invite_link"
            else -> "telegram_no_post_id"
        }
    }

    private fun parseMediaUrls(html: String): List<String> {
        val doc = Jsoup.parse(html, "https://t.me/")
        val urls = LinkedHashSet<String>()

        // Photos: background-image:url('https://cdn*.cdn-telegram.org/file/...') on the photo wrap.
        doc.select("a.tgme_widget_message_photo_wrap, .tgme_widget_message_photo_wrap").forEach { el ->
            val style = el.attr("style")
            BG_IMAGE_REGEX.find(style)?.groupValues?.getOrNull(1)
                ?.takeIf { it.startsWith("http") }
                ?.let { urls.add(it) }
        }

        // Progressive video / GIF / round video: direct src on the widget video element.
        doc.select(
            "video.tgme_widget_message_video[src], " +
                "video.tgme_widget_message_roundvideo[src], " +
                "video.tgme_widget_message_video_player[src]",
        ).forEach { el ->
            el.absUrl("src").takeIf { it.startsWith("http") }?.let { urls.add(it) }
        }

        // Fallback to OpenGraph meta when no widget media node matched (single-media posts).
        if (urls.isEmpty()) {
            doc.select("meta[property=og:video], meta[property=og:video:url], meta[property=og:image]")
                .forEach { meta ->
                    meta.attr("content").takeIf { it.startsWith("http") }?.let { urls.add(it) }
                }
        }

        return urls.toList()
    }

    private data class Post(val channel: String, val id: String)

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (compatible; FastMediaSorter)"
        const val MAX_BATCH_ITEMS = 12
        val TELEGRAM_HOSTS = setOf("t.me", "telegram.me", "www.t.me", "www.telegram.me")
        // Telegram public usernames: 5-32 chars, letters/digits/underscore.
        val CHANNEL_REGEX = Regex("[A-Za-z0-9_]{5,32}")
        val BG_IMAGE_REGEX = Regex("background-image\\s*:\\s*url\\(['\"]?(.+?)['\"]?\\)")
    }
}

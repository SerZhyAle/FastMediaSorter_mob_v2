package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.BlockedReason
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.SiteBatchItem
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class HtmlPageExtractionStrategy @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
    private val direct: DirectFileExtractionStrategy,
    private val streamingSniffer: StreamingManifestSniffer,
    private val structuredMediaSniffer: StructuredMediaSniffer,
    private val settingsRepository: SettingsRepository,
) : UrlExtractionStrategy {

    override val id: String = "html"

    override suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull() ?: return@withContext ProbeResult.NotApplicable
        try {
            val request = Request.Builder().url(httpUrl).head().build()
            httpClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                if (contentType == "text/html") {
                    ProbeResult.Applicable(tentativeMime = null, tentativeSizeBytes = null)
                } else {
                    ProbeResult.NotApplicable
                }
            }
        } catch (io: IOException) {
            Timber.w(io, "HtmlPageExtractionStrategy: probe failed for %s", url)
            ProbeResult.TransientError(io)
        }
    }

    override suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult {
        val httpUrl = url.toHttpUrlOrNull() ?: return OpenResult.Blocked(BlockedReason.NonHttpScheme)

        val fetchResult: HtmlFetchResult = try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(httpUrl).get().build()
                httpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 || response.code == 403 -> {
                            LinkDownloadTrace.verbose(
                                "auth-required for ${LinkDownloadTrace.truncateUrl(url)} status=${response.code} strategy=$id",
                            )
                            HtmlFetchResult.AuthRequired
                        }
                        !response.isSuccessful -> HtmlFetchResult.Failed
                        else -> HtmlFetchResult.Body(
                            value = response.peekBody(MAX_HTML_BYTES).string(),
                            finalUrl = response.request.url.toString(),
                        )
                    }
                }
            }
        } catch (io: IOException) {
            Timber.w(io, "HtmlPageExtractionStrategy: html fetch failed for %s", url)
            return OpenResult.Error(io)
        }

        val rawHtml = when (fetchResult) {
            is HtmlFetchResult.Body -> fetchResult.value
            HtmlFetchResult.AuthRequired -> return OpenResult.Blocked(BlockedReason.AuthRequired)
            HtmlFetchResult.Failed -> return OpenResult.NotFound("html_fetch_failed")
        }
        val finalUrl = (fetchResult as? HtmlFetchResult.Body)?.finalUrl ?: httpUrl.toString()

        val candidates = harvestCandidates(rawHtml, baseUri = finalUrl)
        if (candidates.isEmpty()) {
            val loginWallEnabled = settingsRepository.getSettings().first().linkDownloadLoginWallHeuristicEnabled
            if (loginWallEnabled && looksLikeSoftLoginWall(rawHtml, finalUrl)) {
                LinkDownloadTrace.verbose(
                    "auth-required soft-login-wall for ${LinkDownloadTrace.truncateUrl(finalUrl)} strategy=$id",
                )
                return OpenResult.Blocked(BlockedReason.AuthRequired)
            }
            return OpenResult.NotFound("no_media_in_html")
        }

        val probed = probeCandidates(candidates.take(MAX_HEAD_PROBES))
        val full = probed + candidates.drop(MAX_HEAD_PROBES)
        val filtered = full.filter { candidate ->
            if (
                candidate.source == HtmlMediaCandidate.Source.HLS_MANIFEST ||
                candidate.source == HtmlMediaCandidate.Source.DASH_MANIFEST
            ) {
                return@filter true
            }
            val mime = candidate.tentativeMime
            mime == null || MediaMimeWhitelist.isAllowed(mime)
        }

        // S0197: Threads/IG photo carousel — when ≥ 2 authoritative image candidates come from
        // data-sjs, emit a Batch directly from the cheap path so the user gets all slides without
        // having to spin up the WebView strategy.
        val embeddedImages = filtered.filter {
            it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON && isImageCandidate(it)
        }
        if (embeddedImages.size >= 2) {
            return OpenResult.Batch(
                items = embeddedImages.take(MAX_BATCH_ITEMS).map { SiteBatchItem(url = it.url) },
            )
        }

        val chosen = CandidateSelectionPolicy.choose(filtered)
            ?: return OpenResult.NotFound("no_media_in_html")

        if (
            chosen.source == HtmlMediaCandidate.Source.HLS_MANIFEST ||
            chosen.source == HtmlMediaCandidate.Source.DASH_MANIFEST
        ) {
            val manifest = chosen.manifest ?: return OpenResult.NotFound("streaming_candidate_missing_manifest")
            return OpenResult.Streaming(
                manifest = manifest,
                tentativeFileName = deriveStreamingFileName(chosen.url),
            )
        }

        val host = httpUrl.host
        if (KnownAuthResources.isPreviewSensitiveHost(host)) {
            // S0197: EMBEDDED_JSON candidates are explicitly accepted as real-content. The prior
            // OG/IMG-exclusion check already covered this implicitly, but the explicit branch
            // makes the bypass observable in logs and survives future source-list refactors.
            val hasRealContent = filtered.any { candidate ->
                candidate.source == HtmlMediaCandidate.Source.EMBEDDED_JSON ||
                    (candidate.source != HtmlMediaCandidate.Source.OG_IMAGE &&
                        candidate.source != HtmlMediaCandidate.Source.IMG_TAG &&
                        candidate.source != HtmlMediaCandidate.Source.IMG_SRCSET)
            }
            val hasEmbeddedJsonImage = filtered.any { candidate ->
                candidate.source == HtmlMediaCandidate.Source.EMBEDDED_JSON && isImageCandidate(candidate)
            }
            if (hasEmbeddedJsonImage) {
                LinkDownloadTrace.verbose(
                    "html-strategy embedded-json-image-present bypass-preview-only " +
                        "host=${LinkDownloadTrace.truncateUrl(httpUrl.toString())}",
                )
            }
            if (!hasRealContent) {
                LinkDownloadTrace.verbose(
                    "html-strategy social-preview-only host=${LinkDownloadTrace.truncateUrl(httpUrl.toString())}",
                )
                return OpenResult.SocialPreviewOnly(host = host)
            }
        }

        return direct.open(chosen.url, onProgress)
    }

    private fun deriveStreamingFileName(url: String): String {
        val lastSegment = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull { it.isNotBlank() }
        if (lastSegment.isNullOrBlank()) return "download_${System.currentTimeMillis()}.mp4"
        val withoutExt = lastSegment.substringBeforeLast('.', missingDelimiterValue = lastSegment)
        val safe = withoutExt.replace(Regex("[^a-zA-Z0-9_.\\-]"), "_").take(80).ifBlank { "download" }
        return "$safe.mp4"
    }

    private suspend fun probeCandidates(input: List<HtmlMediaCandidate>): List<HtmlMediaCandidate> {
        if (input.isEmpty()) return input
        val timed = withTimeoutOrNull(CANDIDATE_BUDGET_MS) {
            coroutineScope {
                input.map { candidate ->
                    async(Dispatchers.IO) {
                        runCatching {
                            val request = Request.Builder().url(candidate.url).head().build()
                            httpClient.newCall(request).execute().use { response ->
                                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
                                val size = response.header("Content-Length")?.toLongOrNull()
                                candidate.copy(
                                    tentativeMime = mime ?: candidate.tentativeMime,
                                    tentativeSizeBytes = size ?: candidate.tentativeSizeBytes,
                                )
                            }
                        }.getOrElse { candidate }
                    }
                }.awaitAll()
            }
        }
        return timed ?: input
    }

    private suspend fun harvestCandidates(html: String, baseUri: String): List<HtmlMediaCandidate> {
        val baseHost = baseUri.toHttpUrlOrNull()?.host?.lowercase()
        val structured = structuredMediaSniffer.sniff(html, baseUri = baseUri)
        // S0197: harvest embedded data-sjs JSON for Threads/IG-family hosts so the cheap path
        // surfaces the authoritative post URL(s) before falling back to the WebView render.
        val embedded = if (KnownAuthResources.supportsEmbeddedJson(baseHost)) {
            structuredMediaSniffer.sniffEmbeddedJson(html, baseUri = baseUri)
        } else {
            emptyList()
        }
        val staticCandidates = harvestStaticCandidates(html, baseUri)
        // Embedded first so selection sees the authoritative candidates ahead of OG/IMG noise.
        val merged = (embedded + structured + staticCandidates).distinctBy { it.url }

        val structuredCount = merged.count {
            it.source == HtmlMediaCandidate.Source.JSON_LD ||
                it.source == HtmlMediaCandidate.Source.OEMBED
        }
        val embeddedCount = merged.count { it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON }
        val directCount = merged.count {
            it.source != HtmlMediaCandidate.Source.JSON_LD &&
                it.source != HtmlMediaCandidate.Source.OEMBED &&
                it.source != HtmlMediaCandidate.Source.EMBEDDED_JSON &&
                it.source != HtmlMediaCandidate.Source.HLS_MANIFEST &&
                it.source != HtmlMediaCandidate.Source.DASH_MANIFEST &&
                it.source != HtmlMediaCandidate.Source.OG_IMAGE &&
                it.source != HtmlMediaCandidate.Source.IMG_TAG &&
                it.source != HtmlMediaCandidate.Source.IMG_SRCSET
        }
        val streamingCount = merged.count {
            it.source == HtmlMediaCandidate.Source.HLS_MANIFEST ||
                it.source == HtmlMediaCandidate.Source.DASH_MANIFEST
        }
        val imageCount = merged.count {
            it.source == HtmlMediaCandidate.Source.OG_IMAGE ||
                it.source == HtmlMediaCandidate.Source.IMG_TAG ||
                it.source == HtmlMediaCandidate.Source.IMG_SRCSET
        }
        LinkDownloadTrace.tag(
            "html-sniffer harvested ${merged.size} candidates (structured=$structuredCount, " +
                "embedded=$embeddedCount, direct=$directCount, streaming=$streamingCount, image=$imageCount) " +
                "for ${LinkDownloadTrace.truncateUrl(baseUri)}",
        )
        return merged
    }

    private fun harvestStaticCandidates(html: String, baseUri: String): List<HtmlMediaCandidate> {
        val doc = Jsoup.parse(html, baseUri)
        val out = mutableListOf<HtmlMediaCandidate>()

        fun add(source: HtmlMediaCandidate.Source, raw: String?) {
            if (raw.isNullOrBlank()) return
            val absolute = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else null
            val resolved = absolute ?: runCatching {
                doc.baseUri().toHttpUrlOrNull()?.resolve(raw)?.toString()
            }.getOrNull()
            if (resolved.isNullOrBlank()) return
            if (resolved.startsWith("data:", true) || resolved.startsWith("blob:", true)) return
            out += HtmlMediaCandidate(
                url = resolved,
                source = source,
                tentativeMime = null,
                tentativeSizeBytes = null,
            )
        }

        doc.select("meta[property=og:video], meta[property=og:video:url], meta[property=og:video:secure_url]")
            .forEach { add(HtmlMediaCandidate.Source.OG_VIDEO, it.attr("content")) }
        doc.select("meta[property=og:image], meta[property=og:image:url], meta[property=og:image:secure_url]")
            .forEach { add(HtmlMediaCandidate.Source.OG_IMAGE, it.attr("content")) }
        doc.select("meta[name=twitter:player:stream]")
            .forEach { add(HtmlMediaCandidate.Source.TWITTER_PLAYER_STREAM, it.attr("content")) }
        doc.select("video[src]")
            .forEach { add(HtmlMediaCandidate.Source.VIDEO_TAG, it.attr("abs:src")) }
        doc.select("video > source[src]")
            .forEach { add(HtmlMediaCandidate.Source.SOURCE_TAG, it.attr("abs:src")) }
        doc.select("audio[src]")
            .forEach { add(HtmlMediaCandidate.Source.AUDIO_TAG, it.attr("abs:src")) }
        doc.select("audio > source[src]")
            .forEach { add(HtmlMediaCandidate.Source.SOURCE_TAG, it.attr("abs:src")) }
        doc.select("img[src]")
            .forEach { add(HtmlMediaCandidate.Source.IMG_TAG, it.attr("abs:src")) }
        doc.select("img[srcset]").forEach { img ->
            img.attr("srcset").split(',').forEach { entry ->
                val token = entry.trim().substringBefore(' ')
                if (token.isNotBlank()) add(HtmlMediaCandidate.Source.IMG_SRCSET, token)
            }
        }
        doc.select("a[href]").forEach { anchor ->
            val href = anchor.attr("abs:href")
            val ext = href.substringAfterLast('.', "").substringBefore('?').lowercase()
            if (ext.isNotBlank() && ext.length <= 5 && MediaMimeWhitelist.mimeForExtension(ext) != null) {
                add(HtmlMediaCandidate.Source.INLINE_LINK, href)
            }
        }

        out += streamingSniffer.sniff(html, baseUri = doc.baseUri())
        return out.distinctBy { it.url }
    }

    // S0197: mirrors InvisibleWebViewExtractionStrategy.isImageCandidate — image MIME, OG/IMG
    // source, or known image extension. Used by the Batch trigger and (Step 03.4) the
    // SocialPreviewOnly bypass for embedded-JSON-driven carousels.
    private fun isImageCandidate(candidate: HtmlMediaCandidate): Boolean {
        val mime = candidate.tentativeMime?.substringBefore(';')?.trim()?.lowercase()
        if (mime?.startsWith("image/") == true) return true
        return when (candidate.source) {
            HtmlMediaCandidate.Source.OG_IMAGE,
            HtmlMediaCandidate.Source.IMG_TAG,
            HtmlMediaCandidate.Source.IMG_SRCSET,
            -> true

            else -> {
                val ext = candidate.url.toHttpUrlOrNull()?.encodedPath
                    ?.substringAfterLast('.', "")
                    ?.substringBefore('?')
                    ?.lowercase()
                    .orEmpty()
                ext in IMAGE_EXTENSIONS
            }
        }
    }

    private fun looksLikeSoftLoginWall(html: String, finalUrl: String): Boolean {
        val doc = runCatching { Jsoup.parse(html, finalUrl) }.getOrNull() ?: return false
        var signals = 0

        val finalPath = finalUrl.toHttpUrlOrNull()?.encodedPath.orEmpty()
        if (LOGIN_MARKERS.any { marker -> finalPath.contains(marker, ignoreCase = true) }) {
            signals += 1
        }

        val hasLoginForm = doc.select("input[type=password]").isNotEmpty() ||
            doc.select("form[action]").any { form ->
                LOGIN_MARKERS.any { marker -> form.attr("action").contains(marker, ignoreCase = true) }
            }
        val hasLoginLinks = doc.select("a[href]").any { anchor ->
            LOGIN_MARKERS.any { marker -> anchor.attr("href").contains(marker, ignoreCase = true) }
        }
        if (hasLoginForm || hasLoginLinks) {
            signals += 1
        }

        val hasMediaIntent = doc.select("meta[property=og:type], meta[property=og:video], meta[name=twitter:card]")
            .any { meta ->
                val content = meta.attr("content")
                content.contains("video", ignoreCase = true) || content.contains("player", ignoreCase = true)
            }
        if (hasMediaIntent) {
            signals += 1
        }

        return signals >= MIN_LOGIN_WALL_SIGNALS
    }

    private companion object {
        const val MAX_HTML_BYTES: Long = 2L * 1024L * 1024L
        const val MAX_HEAD_PROBES: Int = 8
        const val CANDIDATE_BUDGET_MS: Long = 4_000L
        const val MIN_LOGIN_WALL_SIGNALS: Int = 2
        const val MAX_BATCH_ITEMS: Int = 12

        val LOGIN_MARKERS = listOf("login", "signin", "auth")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")
    }
}

private sealed interface HtmlFetchResult {
    data class Body(val value: String, val finalUrl: String) : HtmlFetchResult
    data object AuthRequired : HtmlFetchResult
    data object Failed : HtmlFetchResult
}
package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
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

/**
 * S0003 — strategic §5.1 pillar D, sub-strategy 2: HTML page parsing.
 *
 * Probes by Content-Type sniff (text/html → Applicable). Open downloads the page
 * (max 2 MiB), extracts media candidates by source priority, runs ≤ 8 parallel HEAD
 * probes within a 4 s budget, applies [CandidateSelectionPolicy], then delegates the
 * actual download to [DirectFileExtractionStrategy] for the chosen URL.
 *
 * `data:`/`blob:` candidates and script-loaded content are out of scope per §5.1 D.3.
 */
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
            httpClient.newCall(request).execute().use { resp ->
                val contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
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
        val httpUrl = url.toHttpUrlOrNull()
            ?: return OpenResult.Blocked(com.sza.fastmediasorter.domain.usecase.link.BlockedReason.NonHttpScheme)

        val fetchResult: HtmlFetchResult = try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(httpUrl).get().build()
                httpClient.newCall(request).execute().use { resp ->
                    when {
                        // S0116 §5.1 pillar L: surface auth requirement for the WebView flow.
                        resp.code == 401 || resp.code == 403 -> {
                            LinkDownloadTrace.verbose(
                                "auth-required for ${LinkDownloadTrace.truncateUrl(url)} status=${resp.code} strategy=$id",
                            )
                            HtmlFetchResult.AuthRequired
                        }
                        !resp.isSuccessful -> HtmlFetchResult.Failed
                        else -> {
                            val limit = MAX_HTML_BYTES
                            HtmlFetchResult.Body(
                                value = resp.peekBody(limit).string(),
                                finalUrl = resp.request.url.toString(),
                            )
                        }
                    }
                }
            }
        } catch (io: IOException) {
            Timber.w(io, "HtmlPageExtractionStrategy: html fetch failed")
            return OpenResult.Error(io)
        }

        val rawHtml: String = when (fetchResult) {
            is HtmlFetchResult.Body -> fetchResult.value
            HtmlFetchResult.AuthRequired ->
                return OpenResult.Blocked(com.sza.fastmediasorter.domain.usecase.link.BlockedReason.AuthRequired)
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
                return OpenResult.Blocked(com.sza.fastmediasorter.domain.usecase.link.BlockedReason.AuthRequired)
            }
            return OpenResult.NotFound("no_media_in_html")
        }

        // HEAD-probe (capped fan-out + total budget).
        val probed = probeCandidates(candidates.take(MAX_HEAD_PROBES))
        // Carry through any candidates we didn't probe (kept in input order).
        val full = probed + candidates.drop(MAX_HEAD_PROBES)
        val filtered = full.filter { c ->
            // S0116 §5.1 pillar G: streaming manifests carry non-media MIME
            // (`application/vnd.apple.mpegurl` / `application/dash+xml`) and must
            // bypass the direct-file MIME whitelist.
            if (c.source == HtmlMediaCandidate.Source.HLS_MANIFEST ||
                c.source == HtmlMediaCandidate.Source.DASH_MANIFEST) return@filter true
            val mime = c.tentativeMime
            mime == null || MediaMimeWhitelist.isAllowed(mime)
        }

        val chosen = CandidateSelectionPolicy.choose(filtered)
            ?: return OpenResult.NotFound("no_media_in_html")

        // S0116 §5.1 pillar G: streaming candidates short-circuit to OpenResult.Streaming;
        // the coordinator routes them into the streaming pipeline (Phase 03).
        if (chosen.source == HtmlMediaCandidate.Source.HLS_MANIFEST ||
            chosen.source == HtmlMediaCandidate.Source.DASH_MANIFEST
        ) {
            val manifest = chosen.manifest
                ?: return OpenResult.NotFound("streaming_candidate_missing_manifest")
            return OpenResult.Streaming(
                manifest = manifest,
                tentativeFileName = deriveStreamingFileName(chosen.url),
            )
        }

        // S0151: for known video-first social hosts, OG-image-only results are not real content.
        val host = httpUrl.host
        if (KnownAuthResources.isPreviewSensitiveHost(host)) {
            val hasRealContent = filtered.any { c ->
                c.source != HtmlMediaCandidate.Source.OG_IMAGE &&
                    c.source != HtmlMediaCandidate.Source.IMG_TAG &&
                    c.source != HtmlMediaCandidate.Source.IMG_SRCSET
            }
            if (!hasRealContent) {
                LinkDownloadTrace.verbose(
                    "html-strategy social-preview-only host=${LinkDownloadTrace.truncateUrl(httpUrl.toString())}",
                )
                Timber.d("S0151: html-strategy social-preview-only host=$host")
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
                input.map { c ->
                    async(Dispatchers.IO) {
                        runCatching {
                            val req = Request.Builder().url(c.url).head().build()
                            httpClient.newCall(req).execute().use { resp ->
                                val mime = resp.header("Content-Type")?.substringBefore(';')?.trim()
                                val size = resp.header("Content-Length")?.toLongOrNull()
                                c.copy(tentativeMime = mime ?: c.tentativeMime, tentativeSizeBytes = size ?: c.tentativeSizeBytes)
                            }
                        }.getOrElse { c }
                    }
                }.awaitAll()
            }
        }
        return timed ?: input
    }

    private suspend fun harvestCandidates(html: String, baseUri: String): List<HtmlMediaCandidate> {
        val structured = structuredMediaSniffer.sniff(html, baseUri = baseUri)
        val staticCandidates = harvestStaticCandidates(html, baseUri)
        val merged = (structured + staticCandidates).distinctBy { it.url }

        val structuredCount = merged.count {
            it.source == HtmlMediaCandidate.Source.JSON_LD ||
                it.source == HtmlMediaCandidate.Source.OEMBED
        }
        val directCount = merged.count {
            it.source != HtmlMediaCandidate.Source.JSON_LD &&
                it.source != HtmlMediaCandidate.Source.OEMBED &&
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
            "html-sniffer harvested ${merged.size} candidates (structured=$structuredCount, direct=$directCount, " +
                "streaming=$streamingCount, image=$imageCount) for ${LinkDownloadTrace.truncateUrl(baseUri)}",
        )
        return merged
    }

    private fun harvestStaticCandidates(html: String, baseUri: String): List<HtmlMediaCandidate> {
        val doc = Jsoup.parse(html, baseUri)
        val out = mutableListOf<HtmlMediaCandidate>()

        fun add(source: HtmlMediaCandidate.Source, raw: String?) {
            if (raw.isNullOrBlank()) return
            val abs = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else null
            val resolved = abs ?: runCatching { doc.baseUri().toHttpUrlOrNull()?.resolve(raw)?.toString() }.getOrNull()
            if (resolved.isNullOrBlank()) return
            if (resolved.startsWith("data:", true) || resolved.startsWith("blob:", true)) return
            out.add(HtmlMediaCandidate(resolved, source, tentativeMime = null, tentativeSizeBytes = null))
        }

        // 1. Open Graph video / image
        doc.select("meta[property=og:video], meta[property=og:video:url], meta[property=og:video:secure_url]")
            .forEach { add(HtmlMediaCandidate.Source.OG_VIDEO, it.attr("content")) }
        doc.select("meta[property=og:image], meta[property=og:image:url], meta[property=og:image:secure_url]")
            .forEach { add(HtmlMediaCandidate.Source.OG_IMAGE, it.attr("content")) }
        // 2. Twitter player stream
        doc.select("meta[name=twitter:player:stream]")
            .forEach { add(HtmlMediaCandidate.Source.TWITTER_PLAYER_STREAM, it.attr("content")) }
        // 3. Native media tags
        doc.select("video[src]")
            .forEach { add(HtmlMediaCandidate.Source.VIDEO_TAG, it.attr("abs:src")) }
        doc.select("video > source[src]")
            .forEach { add(HtmlMediaCandidate.Source.SOURCE_TAG, it.attr("abs:src")) }
        doc.select("audio[src]")
            .forEach { add(HtmlMediaCandidate.Source.AUDIO_TAG, it.attr("abs:src")) }
        doc.select("audio > source[src]")
            .forEach { add(HtmlMediaCandidate.Source.SOURCE_TAG, it.attr("abs:src")) }
        // 4. <img>
        doc.select("img[src]")
            .forEach { add(HtmlMediaCandidate.Source.IMG_TAG, it.attr("abs:src")) }
        doc.select("img[srcset]").forEach { img ->
            img.attr("srcset").split(',').forEach { entry ->
                val token = entry.trim().substringBefore(' ')
                if (token.isNotBlank()) add(HtmlMediaCandidate.Source.IMG_SRCSET, token)
            }
        }
        // 5. Standalone anchors with whitelisted extensions
        doc.select("a[href]").forEach { a ->
            val href = a.attr("abs:href")
            val ext = href.substringAfterLast('.', "").substringBefore('?').lowercase()
            if (ext.isNotBlank() && ext.length <= 5 && MediaMimeWhitelist.mimeForExtension(ext) != null) {
                add(HtmlMediaCandidate.Source.INLINE_LINK, href)
            }
        }

        // S0116 §5.1 pillar G: append HLS/DASH manifest candidates harvested from the
        // same HTML body. distinctBy below dedups overlapping URLs (same manifest reachable
        // via multiple sources).
        out.addAll(streamingSniffer.sniff(html, baseUri = doc.baseUri()))

        return out.distinctBy { it.url }
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

        val hasMediaIntent = doc.select("meta[property=og:type], meta[property=og:video], meta[name=twitter:card]").any { meta ->
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

        val LOGIN_MARKERS = listOf("login", "signin", "auth")
    }
}

/**
 * S0116 §5.1 pillar L: tri-state outcome of the initial HTML GET so the strategy
 * can distinguish "auth required" (401/403 → BlockedReason.AuthRequired) from a
 * generic non-2xx failure (→ NotFound) without relying on null-as-signal.
 */
private sealed interface HtmlFetchResult {
    data class Body(val value: String, val finalUrl: String) : HtmlFetchResult
    data object AuthRequired : HtmlFetchResult
    data object Failed : HtmlFetchResult
}

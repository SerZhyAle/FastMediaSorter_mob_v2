package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

        val rawHtml: String = try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(httpUrl).get().build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val limit = MAX_HTML_BYTES
                    resp.peekBody(limit).string()
                }
            } ?: return OpenResult.NotFound("html_fetch_failed")
        } catch (io: IOException) {
            Timber.w(io, "HtmlPageExtractionStrategy: html fetch failed")
            return OpenResult.Error(io)
        }

        val candidates = harvestCandidates(rawHtml, baseUri = httpUrl.toString())
        if (candidates.isEmpty()) return OpenResult.NotFound("no_media_in_html")

        // HEAD-probe (capped fan-out + total budget).
        val probed = probeCandidates(candidates.take(MAX_HEAD_PROBES))
        // Carry through any candidates we didn't probe (kept in input order).
        val full = probed + candidates.drop(MAX_HEAD_PROBES)
        val filtered = full.filter { c ->
            val mime = c.tentativeMime
            mime == null || MediaMimeWhitelist.isAllowed(mime)
        }

        val chosen = CandidateSelectionPolicy.choose(filtered)
            ?: return OpenResult.NotFound("no_media_in_html")

        return direct.open(chosen.url, onProgress)
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

    private fun harvestCandidates(html: String, baseUri: String): List<HtmlMediaCandidate> {
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

        // De-duplicate by URL while preserving first occurrence (and its source ordinal).
        return out.distinctBy { it.url }
    }

    private companion object {
        const val MAX_HTML_BYTES: Long = 2L * 1024L * 1024L
        const val MAX_HEAD_PROBES: Int = 8
        const val CANDIDATE_BUDGET_MS: Long = 4_000L
    }
}

package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar G: extracts HLS / DASH manifest URLs from a static HTML page.
 *
 * Four discovery sources, each wrapped in its own try/catch so a parser exception
 * in one source never aborts the others:
 *
 * 1. `<meta>` / `<link>` / `<source>` element src/href ending in `.m3u8` / `.mpd`.
 * 2. JSON-LD `<script type="application/ld+json">` blocks - `VideoObject.contentUrl`
 *    or `embedUrl` ending in `.m3u8` / `.mpd`.
 * 3. Plain-text regex over the raw HTML (catches manifests embedded in inline JS).
 * 4. `data-*` attributes whose name contains `hls`, `dash`, or `manifest`.
 *
 * No JavaScript execution; no site-specific reverse engineering. Pages that gate
 * the manifest behind JS rendering or POST/XHR requests are out of scope.
 */
@Singleton
class StreamingManifestSniffer @Inject constructor() {

    fun sniff(rawHtml: String, baseUri: String): List<HtmlMediaCandidate> = try {
        sniffInternal(rawHtml, baseUri)
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        LinkDownloadTrace.verbose("fallback=html-sniffer-baseline reason=${t::class.simpleName}")
        emptyList()
    }

    private fun sniffInternal(rawHtml: String, baseUri: String): List<HtmlMediaCandidate> {
        val out = mutableListOf<HtmlMediaCandidate>()
        val doc: Document? = try {
            Jsoup.parse(rawHtml, baseUri)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LinkDownloadTrace.verbose("streaming-sniffer jsoup-parse failed: ${t::class.simpleName}")
            null
        }

        // Source 1: meta/link/source/track elements with manifest URL.
        try {
            doc?.select("meta[content], link[href], source[src], track[src]")?.forEach { el ->
                val raw = when {
                    el.hasAttr("abs:content") -> el.attr("abs:content")
                    el.hasAttr("abs:href") -> el.attr("abs:href")
                    el.hasAttr("abs:src") -> el.attr("abs:src")
                    el.hasAttr("content") -> el.attr("content")
                    el.hasAttr("href") -> el.attr("href")
                    else -> el.attr("src")
                }
                addIfManifest(out, raw)
            }
        } catch (t: Throwable) {
            LinkDownloadTrace.verbose("streaming-sniffer meta/link source failed: ${t::class.simpleName}")
        }

        // Source 2: JSON-LD VideoObject.contentUrl / embedUrl.
        try {
            doc?.select("script[type=application/ld+json]")?.forEach { script ->
                harvestJsonLd(script.data(), out)
            }
        } catch (t: Throwable) {
            LinkDownloadTrace.verbose("streaming-sniffer json-ld source failed: ${t::class.simpleName}")
        }

        // Source 3: plain-text regex across the raw HTML (covers manifests embedded
        // in inline scripts that JSON-LD parsing won't see).
        try {
            REGEX_MANIFEST_URL.findAll(rawHtml).forEach { match ->
                addIfManifest(out, match.value)
            }
        } catch (t: Throwable) {
            LinkDownloadTrace.verbose("streaming-sniffer regex source failed: ${t::class.simpleName}")
        }

        // Source 4: data-* attributes with hls/dash/manifest in the name.
        try {
            doc?.allElements?.forEach { el ->
                el.attributes()?.forEach { attr ->
                    val key = attr.key.lowercase()
                    if (!key.startsWith("data-")) return@forEach
                    if (!key.contains("hls") && !key.contains("dash") && !key.contains("manifest")) return@forEach
                    addIfManifest(out, attr.value)
                }
            }
        } catch (t: Throwable) {
            LinkDownloadTrace.verbose("streaming-sniffer data-attr source failed: ${t::class.simpleName}")
        }

        return out.distinctBy { it.url }
    }

    private fun harvestJsonLd(payload: String, out: MutableList<HtmlMediaCandidate>) {
        // JSON-LD scripts can be a single object or an array of objects.
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return
        val nodes: List<JSONObject> = try {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                }
                else -> listOf(JSONObject(trimmed))
            }
        } catch (t: Throwable) {
            return
        }
        nodes.forEach { node ->
            val type = node.optString("@type")
            if (!type.equals("VideoObject", ignoreCase = true)) return@forEach
            addIfManifest(out, node.optString("contentUrl"))
            addIfManifest(out, node.optString("embedUrl"))
        }
    }

    private fun addIfManifest(out: MutableList<HtmlMediaCandidate>, raw: String?) {
        if (raw.isNullOrBlank()) return
        val url = raw.trim().trim('"', '\'')
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val pathLower = httpUrl.encodedPath.lowercase()
        val (source, manifest) = when {
            pathLower.endsWith(".m3u8") -> HtmlMediaCandidate.Source.HLS_MANIFEST to
                StreamingManifest.Hls(manifestUrl = httpUrl.toString())
            pathLower.endsWith(".mpd") -> HtmlMediaCandidate.Source.DASH_MANIFEST to
                StreamingManifest.Dash(manifestUrl = httpUrl.toString())
            else -> return
        }
        out.add(
            HtmlMediaCandidate(
                url = httpUrl.toString(),
                source = source,
                tentativeMime = if (manifest is StreamingManifest.Hls) "application/vnd.apple.mpegurl" else "application/dash+xml",
                tentativeSizeBytes = null,
                manifest = manifest,
            ),
        )
    }

    private companion object {
        // Match http(s) URL ending in .m3u8 or .mpd, with optional query string. Stops at
        // whitespace / quote / closing paren so embedded URLs in JS are recovered cleanly.
        private val REGEX_MANIFEST_URL = Regex("https?://[^\"'\\s)>]+\\.(?:m3u8|mpd)(?:\\?[^\"'\\s)>]*)?", RegexOption.IGNORE_CASE)
    }
}

package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * S0140 pillars R/S: cheap structured-data harvesters that run before the heavier
 * static HTML candidate sweep. JSON-LD is fully local; oEmbed is best-effort and
 * silent on provider-specific failures so the generic pipeline can continue.
 */
@Singleton
class StructuredMediaSniffer @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
) {

    suspend fun sniff(rawHtml: String, baseUri: String): List<HtmlMediaCandidate> = withContext(Dispatchers.IO) {
        try {
            sniffInternal(rawHtml = rawHtml, baseUri = baseUri)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LinkDownloadTrace.verbose("structured-sniffer failed: ${t::class.simpleName}")
            emptyList()
        }
    }

    /**
     * S0223: Parse the Instagram private API response (`/api/v1/media/{id}/info/`) and extract
     * image/video candidates from the `items[]` array. The response schema is identical to the
     * Threads `data-sjs` schema: `image_versions2.candidates[]` + `carousel_media[]`. Reuses
     * the existing `collectThreadPost` traversal so no logic is duplicated.
     */
    fun sniffInstagramApiResponse(json: String, baseUri: String): List<HtmlMediaCandidate> {
        val out = mutableListOf<HtmlMediaCandidate>()
        try {
            val root = JSONObject(json)
            val items = root.optJSONArray("items") ?: return emptyList()
            var itemCount = 0
            for (i in 0 until items.length()) {
                val post = items.optJSONObject(i) ?: continue
                itemCount++
                collectThreadPost(post, baseUri, out)
            }
            val unique = out.distinctBy { extractMetaAssetKey(it.url) }
            LinkDownloadTrace.verbose(
                "ig-api-sniffer harvested ${unique.size} unique assets from $itemCount items" +
                    " baseUri=${LinkDownloadTrace.truncateUrl(baseUri)}",
            )
            return unique
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LinkDownloadTrace.verbose("ig-api-sniffer failed: ${t::class.simpleName}")
            return emptyList()
        }
    }

    fun sniffEmbeddedJson(rawHtml: String, baseUri: String): List<HtmlMediaCandidate> {
        val doc = try {
            Jsoup.parse(rawHtml, baseUri)
        } catch (t: Throwable) {
            LinkDownloadTrace.verbose("structured-sniffer embedded-json jsoup-parse failed: ${t::class.simpleName}")
            return emptyList()
        }

        // S0197: deduplicate by filename (last URL path segment) rather than raw URL.
        // collectJsonObjects() deep-traverses the entire data-sjs JSON tree; the same carousel
        // slide can be emitted multiple times via different traversal paths. Meta CDN URLs for
        // the same asset differ only in edge node and query-signing params — the last path
        // segment ({assetId}_{photoId}_{shardId}_n.{ext}) is stable across all edge variants.
        val result = buildList {
            harvestEmbeddedJson(doc, this)
        }.distinctBy { extractMetaAssetKey(it.url) }
        LinkDownloadTrace.verbose(
            "structured-sniffer embedded-json harvested ${result.size} unique assets" +
                " baseUri=${LinkDownloadTrace.truncateUrl(baseUri)}",
        )
        return result
    }

    private fun sniffInternal(rawHtml: String, baseUri: String): List<HtmlMediaCandidate> {
        val doc = try {
            Jsoup.parse(rawHtml, baseUri)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LinkDownloadTrace.verbose("structured-sniffer jsoup-parse failed: ${t::class.simpleName}")
            return emptyList()
        }

        val out = mutableListOf<HtmlMediaCandidate>()
        harvestJsonLd(doc, out)
        harvestOEmbed(doc, out)
        return out.distinctBy { it.url }
    }

    private fun harvestJsonLd(doc: Document, out: MutableList<HtmlMediaCandidate>) {
        try {
            doc.select("script")
                .filter { it.attr("type").equals("application/ld+json", ignoreCase = true) }
                .forEach { script ->
                collectJsonObjects(scriptPayload(script)).forEach { node ->
                    if (!hasSupportedType(node)) return@forEach
                    addCandidate(out, HtmlMediaCandidate.Source.JSON_LD, node.optString("contentUrl"), doc.baseUri())
                    addCandidate(
                        out,
                        HtmlMediaCandidate.Source.JSON_LD,
                        node.optString("embedUrl"),
                        doc.baseUri(),
                        requireMediaLikeUrl = true,
                    )
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LinkDownloadTrace.verbose("structured-sniffer json-ld failed: ${t::class.simpleName}")
        }
    }

    private fun harvestEmbeddedJson(doc: Document, out: MutableList<HtmlMediaCandidate>) {
        try {
            doc.select("script")
                .filter {
                    it.attr("type").equals("application/json", ignoreCase = true) &&
                        it.hasAttr("data-sjs")
                }
                .forEach { script ->
                val payload = scriptPayload(script)
                collectJsonObjects(payload).forEach { node ->
                    collectThreadPostMedia(node, doc.baseUri(), out)
                }
            }
        } catch (t: Throwable) {
            LinkDownloadTrace.verbose("structured-sniffer embedded-json failed: ${t::class.simpleName}")
        }
    }

    private fun harvestOEmbed(doc: Document, out: MutableList<HtmlMediaCandidate>) {
        try {
            doc.select("link[href]")
                .filter { it.attr("type").equals("application/json+oembed", ignoreCase = true) }
                .mapNotNull { resolveHttpUrl(it.attr("href"), doc.baseUri()) }
                .distinct()
                .forEach { endpoint ->
                    val request = Request.Builder().url(endpoint).get().build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.code == 401 || response.code == 403 || !response.isSuccessful) return@use
                        val payload = response.body?.string().orEmpty()
                        if (payload.isBlank()) return@use
                        val root = runCatching { JSONObject(payload) }.getOrElse {
                            LinkDownloadTrace.verbose("structured-sniffer oembed-json failed: ${it::class.simpleName}")
                            return@use
                        }
                        val oembedType = root.optString("type")
                        addCandidate(
                            out,
                            HtmlMediaCandidate.Source.OEMBED,
                            root.optString("url"),
                            doc.baseUri(),
                            requireMediaLikeUrl = !oembedType.equals("photo", ignoreCase = true),
                        )
                        harvestOEmbedHtml(root.optString("html"), doc.baseUri(), out)
                    }
                }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LinkDownloadTrace.verbose("structured-sniffer oembed failed: ${t::class.simpleName}")
        }
    }

    private fun harvestOEmbedHtml(
        snippet: String,
        baseUri: String,
        out: MutableList<HtmlMediaCandidate>,
    ) {
        if (snippet.isBlank()) return
        val doc = runCatching { Jsoup.parseBodyFragment(snippet, baseUri) }.getOrElse { return }

        doc.select("video[src], audio[src], img[src]").forEach { element ->
            addCandidate(out, HtmlMediaCandidate.Source.OEMBED, element.attr("src"), baseUri)
        }
        doc.select("video source[src], audio source[src]").forEach { element ->
            addCandidate(out, HtmlMediaCandidate.Source.OEMBED, element.attr("src"), baseUri)
        }
        doc.select("img[srcset]").forEach { img ->
            img.attr("srcset").split(',').forEach { entry ->
                val token = entry.trim().substringBefore(' ')
                if (token.isNotBlank()) {
                    addCandidate(out, HtmlMediaCandidate.Source.OEMBED, token, baseUri)
                }
            }
        }
        doc.select("a[href]").forEach { anchor ->
            addCandidate(
                out,
                HtmlMediaCandidate.Source.OEMBED,
                anchor.attr("href"),
                baseUri,
                requireMediaLikeUrl = true,
            )
        }
    }

    private fun addCandidate(
        out: MutableList<HtmlMediaCandidate>,
        source: HtmlMediaCandidate.Source,
        raw: String?,
        baseUri: String,
        requireMediaLikeUrl: Boolean = false,
    ) {
        val resolved = resolveHttpUrl(raw, baseUri) ?: return
        if (requireMediaLikeUrl && !looksLikeMediaUrl(resolved)) return
        out += HtmlMediaCandidate(
            url = resolved,
            source = source,
            tentativeMime = null,
            tentativeSizeBytes = null,
            pageOrigin = originOf(baseUri),
        )
    }

    private fun collectThreadPostMedia(
        node: JSONObject,
        baseUri: String,
        out: MutableList<HtmlMediaCandidate>,
    ) {
        node.optJSONArray("thread_items")?.let { threadItems ->
            for (index in 0 until threadItems.length()) {
                val item = threadItems.optJSONObject(index) ?: continue
                collectThreadPost(item.optJSONObject("post"), baseUri, out)
            }
        }
        collectThreadPost(node.optJSONObject("post"), baseUri, out)
    }

    private fun collectThreadPost(
        post: JSONObject?,
        baseUri: String,
        out: MutableList<HtmlMediaCandidate>,
    ) {
        if (post == null) return

        addCandidate(
            out,
            HtmlMediaCandidate.Source.EMBEDDED_JSON,
            firstUrl(post.optJSONObject("image_versions2")?.optJSONArray("candidates")),
            baseUri,
        )
        addCandidate(
            out,
            HtmlMediaCandidate.Source.EMBEDDED_JSON,
            firstUrl(post.optJSONArray("video_versions")),
            baseUri,
        )

        val carousel = post.optJSONArray("carousel_media") ?: return
        for (index in 0 until carousel.length()) {
            val item = carousel.optJSONObject(index) ?: continue
            // Keep one canonical URL per slide so the batch path sees slides, not every rendition.
            addCandidate(
                out,
                HtmlMediaCandidate.Source.EMBEDDED_JSON,
                firstUrl(item.optJSONObject("image_versions2")?.optJSONArray("candidates")),
                baseUri,
            )
            addCandidate(
                out,
                HtmlMediaCandidate.Source.EMBEDDED_JSON,
                firstUrl(item.optJSONArray("video_versions")),
                baseUri,
            )
        }
    }

    private fun firstUrl(candidates: JSONArray?): String? {
        if (candidates == null) return null
        for (index in 0 until candidates.length()) {
            val node = candidates.optJSONObject(index) ?: continue
            val url = node.optString("url")
            if (url.isNotBlank()) return url
        }
        return null
    }

    private fun scriptPayload(script: Element): String =
        script.data().ifBlank {
            script.dataNodes().joinToString(separator = "") { it.wholeData }.ifBlank { script.html() }
        }

    private fun resolveHttpUrl(raw: String?, baseUri: String): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().trim('"', '\'')
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed.takeIf { it.toHttpUrlOrNull() != null }
        }
        return baseUri.toHttpUrlOrNull()?.resolve(trimmed)?.toString()
    }

    private fun looksLikeMediaUrl(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val path = httpUrl.encodedPath.lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return true
        val ext = path.substringAfterLast('.', "").substringBefore('?')
        return ext.isNotBlank() && MediaMimeWhitelist.mimeForExtension(ext) != null
    }

    private fun hasSupportedType(node: JSONObject): Boolean {
        val rawType = node.opt("@type") ?: return false
        return when (rawType) {
            is String -> SUPPORTED_TYPES.any { it.equals(rawType, ignoreCase = true) }
            is JSONArray -> (0 until rawType.length()).any { index ->
                val value = rawType.optString(index)
                SUPPORTED_TYPES.any { it.equals(value, ignoreCase = true) }
            }
            else -> false
        }
    }

    private fun collectJsonObjects(payload: String): List<JSONObject> {
        val trimmed = payload.trim()
        if (trimmed.isBlank()) return emptyList()

        val root = runCatching<Any> {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
        }.getOrElse { return emptyList() }

        val out = mutableListOf<JSONObject>()
        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    out += node
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        visit(node.opt(keys.next()))
                    }
                }
                is JSONArray -> {
                    for (index in 0 until node.length()) {
                        visit(node.opt(index))
                    }
                }
            }
        }
        visit(root)
        return out
    }

    private fun originOf(baseUri: String): String? =
        baseUri.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }

    /**
     * S0197: key for deduplicating embedded-JSON candidates by asset identity rather than raw URL.
     * Meta CDN URLs for the same asset differ only in edge node, signing tokens, and `_nc_*`
     * params — but the path's last segment is stable: `{assetId}_{photoId}_{shardId}_n.{ext}`.
     * Two URLs with the same last segment are the same physical asset. Falls back to the full URL
     * for non-Meta / path-less URLs so that the key is always defined.
     */
    private fun extractMetaAssetKey(url: String): String =
        url.toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull { it.isNotBlank() }
            ?: url

    private companion object {
        val SUPPORTED_TYPES = setOf("VideoObject", "MediaObject", "ImageObject")
    }
}
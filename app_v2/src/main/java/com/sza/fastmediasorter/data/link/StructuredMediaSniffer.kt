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
            doc.select("script[type=application/ld+json]").forEach { script ->
                collectJsonObjects(script.data()).forEach { node ->
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

    private fun harvestOEmbed(doc: Document, out: MutableList<HtmlMediaCandidate>) {
        try {
            doc.select("link[type=application/json+oembed][href]")
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
        )
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

    private companion object {
        val SUPPORTED_TYPES = setOf("VideoObject", "MediaObject", "ImageObject")
    }
}
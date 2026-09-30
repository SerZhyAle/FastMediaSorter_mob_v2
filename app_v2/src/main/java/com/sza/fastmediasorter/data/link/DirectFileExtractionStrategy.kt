package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.domain.usecase.link.BlockedReason
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * S0003 - strategic §5.1, pillar D, sub-strategy 1: direct file URL.
 *
 * Probe semantics: HEAD (fallback to ranged GET) decides whether the URL points at a
 * media/document file. Open semantics: GET, validate redirects stay on `http(s)`,
 * derive a sane filename, and return a streaming [OpenResult.Stream].
 */
@Singleton
class DirectFileExtractionStrategy @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
) : UrlExtractionStrategy {

    override val id: String = "direct"

    override suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull() ?: return@withContext ProbeResult.NotApplicable
        try {
            val headRequest = Request.Builder().url(httpUrl).head().build()
            httpClient.newCall(headRequest).execute().use { resp ->
                if (resp.isSuccessful) {
                    val mime = resp.header("Content-Type")
                    val size = resp.header("Content-Length")?.toLongOrNull()
                    if (isDownloadable(httpUrl, mime)) {
                        return@withContext ProbeResult.Applicable(mime, size)
                    }
                    return@withContext ProbeResult.NotApplicable
                }
            }
            // Some servers reject HEAD - try a 1-byte ranged GET.
            val rangedGet = Request.Builder().url(httpUrl)
                .header("Range", "bytes=0-0")
                .get()
                .build()
            httpClient.newCall(rangedGet).execute().use { resp ->
                val mime = resp.header("Content-Type")
                val size = resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: resp.header("Content-Length")?.toLongOrNull()
                if (isDownloadable(httpUrl, mime)) {
                    ProbeResult.Applicable(mime, size)
                } else {
                    ProbeResult.NotApplicable
                }
            }
        } catch (io: IOException) {
            Timber.w(io, "DirectFileExtractionStrategy: probe failed for %s", url)
            ProbeResult.TransientError(io)
        }
    }

    override suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult = open(url, onProgress, extraHeaders = emptyMap())

    /**
     * S0171 overload: re-fetch with additional request headers. The hidden-WebView strategy
     * uses this to replay the page context (`Referer`, browser `User-Agent`, `Range`) when
     * downloading a signed CDN URL it intercepted - without these the CDN returns a tiny error
     * stub. Not part of [UrlExtractionStrategy] (which keeps the 2-arg contract for the registry).
     */
    suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
        extraHeaders: Map<String, String>,
    ): OpenResult {
        var opened: OpenResult.Stream? = null
        return try {
            openOnIo(url, extraHeaders) { opened = it }
        } catch (cancelled: CancellationException) {
            // withContext drops a finished result when the caller was cancelled meanwhile, and that
            // result holds an open response nobody else will ever close.
            opened?.close?.invoke()
            throw cancelled
        }
    }

    private suspend fun openOnIo(
        url: String,
        extraHeaders: Map<String, String>,
        onStreamOpened: (OpenResult.Stream) -> Unit,
    ): OpenResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull()
            ?: return@withContext OpenResult.Blocked(BlockedReason.NonHttpScheme)

        try {
            val request = Request.Builder().url(httpUrl).get().apply {
                extraHeaders.forEach { (name, value) -> header(name, value) }
            }.build()
            val response: Response = httpClient.newCall(request).execute()
            // OkHttp's HttpOnlyRedirectInterceptor rejects non-http(s) hops at request build time;
            // double-check the final url anyway in case of internal redirect handling differences.
            val finalScheme = response.request.url.scheme.lowercase()
            if (finalScheme != "http" && finalScheme != "https") {
                response.close()
                return@withContext OpenResult.Blocked(BlockedReason.RedirectToNonHttp)
            }
            // S0116 §5.1 pillar L: surface authentication-required outcomes for the WebView flow.
            if (response.code == 401 || response.code == 403) {
                LinkDownloadTrace.verbose(
                    "auth-required for ${LinkDownloadTrace.truncateUrl(url)} status=${response.code} strategy=$id",
                )
                response.close()
                return@withContext OpenResult.Blocked(BlockedReason.AuthRequired)
            }
            val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
            // An HLS / DASH manifest is a playlist, not the media: the streaming pipeline fetches and
            // remuxes its segments, so the playlist body itself is never saved.
            val finalUrl = response.request.url
            StreamingManifestSniffer.manifestFor(finalUrl.toString(), mime)?.let { manifest ->
                response.close()
                return@withContext OpenResult.Streaming(
                    manifest = manifest,
                    tentativeFileName = streamingFileName(finalUrl),
                )
            }
            if (mime == null || !MediaMimeWhitelist.isAllowed(mime)) {
                response.close()
                return@withContext OpenResult.Blocked(BlockedReason.MimeNotAllowed)
            }
            val body = response.body
            if (body == null) {
                response.close()
                return@withContext OpenResult.Error(IOException("empty body"))
            }
            val fileName = deriveFileName(response, httpUrl, mime)
            OpenResult.Stream(
                body = body.byteStream(),
                contentLength = body.contentLength().takeIf { it > 0 },
                mime = mime,
                fileName = fileName,
                close = { runCatching { response.close() } },
            ).also(onStreamOpened)
        } catch (io: IOException) {
            Timber.w(io, "DirectFileExtractionStrategy: open failed for %s", url)
            OpenResult.Error(io)
        }
    }

    private fun deriveFileName(response: Response, httpUrl: okhttp3.HttpUrl, mime: String): String {
        // 1. Content-Disposition: try filename* (RFC 5987) then plain filename=
        val disposition = response.header("Content-Disposition")
        val fromDisposition = disposition?.let(::extractDispositionFilename)
        if (!fromDisposition.isNullOrBlank()) return sanitise(fromDisposition)

        // 2. Last URL path segment.
        val segment = httpUrl.pathSegments.lastOrNull { it.isNotBlank() }
        if (!segment.isNullOrBlank()) {
            val decoded = runCatching { URLDecoder.decode(segment, Charsets.UTF_8.name()) }.getOrNull() ?: segment
            if (decoded.contains('.')) return sanitise(decoded)
            // No extension - append from MIME.
            MediaMimeWhitelist.extensionFor(mime)?.let { return sanitise("$decoded.$it") }
            return sanitise(decoded)
        }

        // 3. Fallback synthetic name.
        val ext = MediaMimeWhitelist.extensionFor(mime) ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    private fun extractDispositionFilename(header: String): String? {
        // Numbered groups only: a named-group lookup is Matcher#start(String), API 26, and legacy ships to API 23.
        // filename*=UTF-8''something.jpg
        val starMatch = Regex("filename\\*\\s*=\\s*[A-Za-z0-9_-]+''([^;]+)").find(header)
        if (starMatch != null) {
            val raw = starMatch.groupValues[1].trim().trim('"')
            return runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrNull() ?: raw
        }
        // filename="value"
        val plainMatch = Regex("filename\\s*=\\s*\"?([^\";]+)\"?").find(header)
        return plainMatch?.groupValues?.get(1)?.trim()
    }

    private fun sanitise(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_.\\-]"), "_").take(120).ifBlank { "download.bin" }
    }

    private fun isDownloadable(httpUrl: HttpUrl, mime: String?): Boolean =
        MediaMimeWhitelist.isAllowed(mime) ||
            pathHasMediaExtension(httpUrl.encodedPath) ||
            StreamingManifestSniffer.manifestFor(httpUrl.toString(), mime) != null

    private fun streamingFileName(httpUrl: HttpUrl): String {
        val segment = httpUrl.pathSegments.lastOrNull { it.isNotBlank() }
            ?: return "download_${System.currentTimeMillis()}.mp4"
        return sanitise(segment.substringBeforeLast('.')) + ".mp4"
    }

    private fun pathHasMediaExtension(encodedPath: String): Boolean {
        val ext = encodedPath.substringAfterLast('.', "").substringBefore('?').lowercase()
        if (ext.isBlank() || ext.length > 5) return false
        return MediaMimeWhitelist.mimeForExtension(ext) != null
    }
}

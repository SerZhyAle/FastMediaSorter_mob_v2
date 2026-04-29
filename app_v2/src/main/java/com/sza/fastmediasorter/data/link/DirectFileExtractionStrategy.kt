package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.domain.usecase.link.BlockedReason
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * S0003 — strategic §5.1, pillar D, sub-strategy 1: direct file URL.
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
                    if (MediaMimeWhitelist.isAllowed(mime) || pathHasMediaExtension(httpUrl.encodedPath)) {
                        return@withContext ProbeResult.Applicable(mime, size)
                    }
                    return@withContext ProbeResult.NotApplicable
                }
            }
            // Some servers reject HEAD — try a 1-byte ranged GET.
            val rangedGet = Request.Builder().url(httpUrl)
                .header("Range", "bytes=0-0")
                .get()
                .build()
            httpClient.newCall(rangedGet).execute().use { resp ->
                val mime = resp.header("Content-Type")
                val size = resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: resp.header("Content-Length")?.toLongOrNull()
                if (MediaMimeWhitelist.isAllowed(mime) || pathHasMediaExtension(httpUrl.encodedPath)) {
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
    ): OpenResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull()
            ?: return@withContext OpenResult.Blocked(BlockedReason.NonHttpScheme)

        try {
            val request = Request.Builder().url(httpUrl).get().build()
            val response: Response = httpClient.newCall(request).execute()
            // OkHttp's HttpOnlyRedirectInterceptor rejects non-http(s) hops at request build time;
            // double-check the final url anyway in case of internal redirect handling differences.
            val finalScheme = response.request.url.scheme.lowercase()
            if (finalScheme != "http" && finalScheme != "https") {
                response.close()
                return@withContext OpenResult.Blocked(BlockedReason.RedirectToNonHttp)
            }
            val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
            if (!MediaMimeWhitelist.isAllowed(mime)) {
                response.close()
                return@withContext OpenResult.Blocked(BlockedReason.MimeNotAllowed)
            }
            val body = response.body
            if (body == null) {
                response.close()
                return@withContext OpenResult.Error(IOException("empty body"))
            }
            val fileName = deriveFileName(response, httpUrl, mime!!)
            OpenResult.Stream(
                body = body.byteStream(),
                contentLength = body.contentLength().takeIf { it > 0 },
                mime = mime,
                fileName = fileName,
                close = { runCatching { response.close() } },
            )
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
            // No extension — append from MIME.
            MediaMimeWhitelist.extensionFor(mime)?.let { return sanitise("$decoded.$it") }
            return sanitise(decoded)
        }

        // 3. Fallback synthetic name.
        val ext = MediaMimeWhitelist.extensionFor(mime) ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    private fun extractDispositionFilename(header: String): String? {
        // filename*=UTF-8''something.jpg
        val starMatch = Regex("filename\\*\\s*=\\s*[A-Za-z0-9_-]+''(?<v>[^;]+)").find(header)
        if (starMatch != null) {
            val raw = starMatch.groups["v"]?.value?.trim()?.trim('"')
            return raw?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull() ?: it }
        }
        // filename="value"
        val plainMatch = Regex("filename\\s*=\\s*\"?(?<v>[^\";]+)\"?").find(header)
        return plainMatch?.groups?.get("v")?.value?.trim()
    }

    private fun sanitise(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_.\\-]"), "_").take(120).ifBlank { "download.bin" }
    }

    private fun pathHasMediaExtension(encodedPath: String): Boolean {
        val ext = encodedPath.substringAfterLast('.', "").substringBefore('?').lowercase()
        if (ext.isBlank() || ext.length > 5) return false
        return MediaMimeWhitelist.mimeForExtension(ext) != null
    }
}

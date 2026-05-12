package com.sza.fastmediasorter.data.link.nolegal

import com.chaquo.python.Python
import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.SiteBatchItem
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0174: yt-dlp backed URL extraction strategy for the noLegal flavor.
 *
 * Runs yt-dlp via Chaquopy Python bridge to probe and open URLs that other strategies
 * cannot handle (social platforms, playlist hosts, sites with JavaScript-gated media).
 * Positioned first in [LinkExtractionRegistry.CANONICAL_ORDER] so it is tried before
 * direct/html/dynamic strategies.
 *
 * Thread safety: yt-dlp's YoutubeDL class is not thread-safe — a new instance is created
 * per call on a dedicated single-thread executor (ADR-4 in S0174 strategic spec).
 */
@Singleton
class YtDlpExtractionStrategy @Inject constructor(
    private val runtimeHolder: ChaquopyRuntimeHolder,
    private val cookieWriter: CookieFileWriter,
    private val direct: DirectFileExtractionStrategy,
) : UrlExtractionStrategy {

    override val id: String = "ytdlp"

    override suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        // Skip direct CDN media URLs — extension check is O(1) and avoids Python startup cost.
        val ext = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull()?.substringAfterLast('.', "")
        if (ext.isNullOrBlank().not() && MediaMimeWhitelist.mimeForExtension(ext) != null) {
            return@withContext ProbeResult.NotApplicable
        }

        if (!runtimeHolder.ensureInitialized()) {
            return@withContext ProbeResult.NotApplicable
        }

        try {
            withTimeout(PROBE_TIMEOUT_MS) {
                val result = EXECUTOR.submit<Boolean> {
                    runCatching {
                        val py = Python.getInstance()
                        val ytdlp = py.getModule("yt_dlp")
                        val opts = mapOf(
                            "quiet" to true,
                            "no_warnings" to true,
                            "socket_timeout" to 8,
                            "extract_flat" to "in_playlist",
                        )
                        val ydl = ytdlp.callAttr("YoutubeDL", opts)
                        val info = ydl.callAttr("extract_info", url, false)
                        info != null
                    }.getOrElse { false }
                }.get()
                if (result) {
                    Timber.d("YtDlpExtractionStrategy: probe applicable url=%s", url)
                    ProbeResult.Applicable(tentativeMime = null, tentativeSizeBytes = null)
                } else {
                    ProbeResult.NotApplicable
                }
            }
        } catch (_: TimeoutCancellationException) {
            ProbeResult.NotApplicable
        } catch (e: Exception) {
            Timber.w(e, "YtDlpExtractionStrategy: probe error url=%s", url)
            ProbeResult.NotApplicable
        }
    }

    override suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult = withContext(Dispatchers.IO) {
        if (!runtimeHolder.ensureInitialized()) {
            return@withContext OpenResult.NotFound("ytdlp_runtime_unavailable")
        }

        val targetHost = url.toHttpUrlOrNull()?.host ?: ""
        val cookieFile = cookieWriter.writeCookieFile(targetHost)

        try {
            val result = EXECUTOR.submit<Any> {
                runCatching {
                    val py = Python.getInstance()
                    val ytdlp = py.getModule("yt_dlp")
                    val opts = buildMap<String, Any> {
                        put("quiet", true)
                        put("no_warnings", true)
                        put("socket_timeout", 8)
                        // TikTok watermark filter + best available video+audio fallback
                        put("format", "bv[format_id!*=watermark]+ba/bv*+ba/best")
                        cookieFile?.let { put("cookiefile", it.absolutePath) }
                    }
                    val ydl = ytdlp.callAttr("YoutubeDL", opts)
                    val info = ydl.callAttr("extract_info", url, false)

                    if (info == null) {
                        return@runCatching OpenResult.NotFound("ytdlp_extract_failed")
                    }

                    val infoType = info.callAttr("get", "_type")?.toString()
                    if (infoType == "playlist" || infoType == "multi_video") {
                        // Carousel / playlist — return batch of URLs
                        val entries = info.callAttr("get", "entries")
                        val items = mutableListOf<SiteBatchItem>()
                        if (entries != null) {
                            val entriesIter = entries.callAttr("__iter__")
                            repeat(MAX_BATCH_ITEMS) {
                                val entry = runCatching {
                                    entriesIter.callAttr("__next__")
                                }.getOrNull() ?: return@repeat
                                val itemUrl = entry.callAttr("get", "webpage_url")?.toString()
                                    ?: entry.callAttr("get", "url")?.toString()
                                    ?: return@repeat
                                val itemTitle = entry.callAttr("get", "title")?.toString()
                                items += SiteBatchItem(itemUrl, itemTitle)
                            }
                        }
                        val label = info.callAttr("get", "title")?.toString()
                        return@runCatching OpenResult.Batch(items, label)
                    }

                    // Single video — find best format URL
                    val formats = info.callAttr("get", "formats")
                    var cdnUrl: String? = null
                    var ext = "mp4"

                    if (formats != null) {
                        val formatsList = mutableListOf<Pair<String, String>>() // url, ext
                        val fmtIter = formats.callAttr("__iter__")
                        while (true) {
                            val fmt = runCatching { fmtIter.callAttr("__next__") }.getOrNull()
                                ?: break
                            val fmtUrl = fmt.callAttr("get", "url")?.toString() ?: continue
                            val fmtVcodec = fmt.callAttr("get", "vcodec")?.toString() ?: ""
                            val fmtExt = fmt.callAttr("get", "ext")?.toString() ?: "mp4"
                            formatsList += fmtUrl to fmtExt
                            // Prefer format with video stream
                            if (cdnUrl == null && fmtVcodec.isNotEmpty() && fmtVcodec != "none") {
                                cdnUrl = fmtUrl
                                ext = fmtExt
                            }
                        }
                        // Fall back to first available URL if no video-bearing format found
                        if (cdnUrl == null && formatsList.isNotEmpty()) {
                            cdnUrl = formatsList.first().first
                            ext = formatsList.first().second
                        }
                    } else {
                        // No formats list — try top-level url
                        cdnUrl = info.callAttr("get", "url")?.toString()
                    }

                    if (cdnUrl == null) {
                        return@runCatching OpenResult.NotFound("ytdlp_no_format_url")
                    }

                    val rawTitle = info.callAttr("get", "title")?.toString() ?: "download"
                    val safeTitle = rawTitle.replace(Regex("[^A-Za-z0-9._\\- ]"), "_")
                        .trim().take(120)

                    val userAgent = info.callAttr("get", "http_headers")
                        ?.callAttr("get", "User-Agent")?.toString()
                        ?: BROWSER_UA

                    val extraHeaders = mapOf(
                        "Referer" to url,
                        "User-Agent" to userAgent,
                    )

                    // Return delegation params — resolved outside the executor after .get()
                    DelegateParams(cdnUrl, safeTitle, ext, extraHeaders)
                }.getOrElse { error ->
                    Timber.e(error, "YtDlpExtractionStrategy: open failed url=%s", url)
                    OpenResult.Error(error)
                }
            }.get()

            when (result) {
                is DelegateParams -> {
                    val delegated = direct.open(result.cdnUrl, onProgress, result.extraHeaders)
                    if (delegated is OpenResult.Stream) {
                        delegated.copy(fileName = "${result.safeTitle}.${result.ext}")
                    } else {
                        delegated
                    }
                }
                is OpenResult -> result
                else -> OpenResult.NotFound("ytdlp_unexpected_result")
            }
        } finally {
            cookieFile?.let { cookieWriter.deleteCookieFile(it) }
        }
    }

    /** Internal carrier for CDN delegation params — avoids returning mixed types from executor. */
    private data class DelegateParams(
        val cdnUrl: String,
        val safeTitle: String,
        val ext: String,
        val extraHeaders: Map<String, String>,
    )

    companion object {
        private const val PROBE_TIMEOUT_MS = 10_000L
        private const val MAX_BATCH_ITEMS = 50

        /** Generic browser UA — sent as Referer context header to CDN servers. */
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * Single-thread executor for yt-dlp calls.
         * YoutubeDL instances are not thread-safe — a new instance is created per call,
         * but Chaquopy's GIL wrapper serialises Python execution regardless.
         * Using a dedicated thread avoids interference with IO thread pool task scheduling.
         */
        private val EXECUTOR = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ytdlp-worker").apply { isDaemon = true }
        }
    }
}

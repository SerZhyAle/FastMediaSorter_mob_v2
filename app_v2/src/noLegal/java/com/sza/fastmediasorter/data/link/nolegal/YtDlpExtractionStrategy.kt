package com.sza.fastmediasorter.data.link.nolegal

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.data.link.LinkDownloadUserAgents
import com.sza.fastmediasorter.data.link.cookie.LinkDownloadSessionContext
import com.sza.fastmediasorter.domain.usecase.link.BlockedReason
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.SiteBatchItem
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Thread safety: yt-dlp's YoutubeDL class is not thread-safe - a new instance is created
 * per call on a dedicated single-thread executor (ADR-4 in S0174 strategic spec).
 */
@Singleton
class YtDlpExtractionStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeHolder: ChaquopyRuntimeHolder,
    private val cookieWriter: CookieFileWriter,
    private val direct: DirectFileExtractionStrategy,
    private val sessionContext: LinkDownloadSessionContext,
) : UrlExtractionStrategy {

    override val id: String = "ytdlp"

    override suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        // Skip direct CDN media URLs - extension check is O(1) and avoids Python startup cost.
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
                        // ytdlp_utils.probe_url() iterates yt-dlp extractors and calls
                        // ie.suitable(url) - pure URL pattern matching, zero network calls.
                        // Returns True if a non-generic extractor matches, None otherwise.
                        // This avoids the auth-required failure: extract_info(download=False)
                        // still makes real HTTP calls, which fail for Instagram/TikTok/Facebook
                        // without cookies and silently return NotApplicable even when supported.
                        val utils = py.getModule("ytdlp_utils")
                        utils.callAttr("probe_url", url) != null
                    }.getOrElse { e ->
                        Timber.w(e, "YtDlpExtractionStrategy: probe inner error url=%s", url)
                        false
                    }
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
        // S0182: replay the User-Agent captured during WebView login on every cookie-bound
        // request. Falls back to a mobile UA (matches our actual Android device) instead
        // of the legacy desktop UA so the fingerprint matches what Meta first observed.
        val sessionUa = sessionContext.userAgentFor(targetHost) ?: LinkDownloadUserAgents.MOBILE_BROWSER_UA
        Timber.d(
            "YtDlpExtractionStrategy: open host=%s ua=%s%s",
            targetHost,
            sessionUa.take(60),
            if (sessionContext.userAgentFor(targetHost) != null) " [pinned]" else " [fallback]"
        )

        try {
            val result = EXECUTOR.submit<Any> {
                runCatching {
                    val py = Python.getInstance()
                    val ytdlp = py.getModule("yt_dlp")
                    // Must be a native Python dict - yt-dlp calls opts.get(key, default)
                    // with 2 args internally. Kotlin Map.get() only accepts 1 arg, causing
                    // PyException: TypeError: MapBuilder.get takes 1 argument (2 given).
                    val opts = py.builtins.callAttr("dict")
                    opts.callAttr("__setitem__", "quiet", true)
                    opts.callAttr("__setitem__", "no_warnings", true)
                    opts.callAttr("__setitem__", "socket_timeout", 8)
                    // Single-stream format chain - no ffmpeg required for merge.
                    // YouTube without ffmpeg: pick best progressive MP4 (typically 720p
                    // format 22, fallback to 360p format 18); higher resolutions are
                    // DASH/HLS-only and would require merge. The Kotlin-side format
                    // selection below still iterates `formats` to find the best stream
                    // and routes manifest URLs to Python download (which yt-dlp can
                    // handle natively for single-stream HLS).
                    opts.callAttr("__setitem__", "format", "best[ext=mp4]/best")
                    // S0182: pin the same UA the cookies were saved with so the server
                    // sees identical login+API fingerprint.
                    opts.callAttr("__setitem__", "user_agent", sessionUa)
                    cookieFile?.let { opts.callAttr("__setitem__", "cookiefile", it.absolutePath) }
                    val ydl = ytdlp.callAttr("YoutubeDL", opts)
                    val info = ydl.callAttr("extract_info", url, false)

                    if (info == null) {
                        return@runCatching OpenResult.NotFound("ytdlp_extract_failed")
                    }

                    val infoType = info.callAttr("get", "_type")?.toString()
                    if (infoType == "playlist" || infoType == "multi_video") {
                        // Carousel / playlist - return batch of URLs
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
                        // Empty batch means yt-dlp couldn't access media (image-only post,
                        // restricted carousel, etc.) - fall through to html/dynamic strategy.
                        if (items.isEmpty()) {
                            return@runCatching OpenResult.NotFound("ytdlp_empty_batch")
                        }
                        return@runCatching OpenResult.Batch(items, label)
                    }

                    // Single video - split formats into progressive (direct OkHttp download
                    // is possible) and manifest (HLS/DASH - must go via yt-dlp Python).
                    // yt-dlp returns formats in ASCENDING quality order so we must iterate
                    // all and pick best per-bucket.
                    //
                    // S0166 audio-loss fix: progressive picker now prefers COMBINED streams
                    // (video + audio in one file) over video-only streams of higher resolution.
                    // Instagram exposes both via yt-dlp:
                    //   - video_versions[] → progressive mp4 WITH audio, but yt-dlp leaves
                    //     vcodec/acodec empty (it didn't probe codecs for these)
                    //   - DASH segments → split video-only (vcodec=vp09, acodec="none") and
                    //     audio-only (vcodec="none", acodec=mp4a) - needs muxing (ffmpeg-only)
                    // The old picker filtered out the combined stream because vcodec was empty
                    // and selected the highest-resolution video-only DASH variant - file saved
                    // without sound. We now treat empty vcodec/acodec as "unknown but present"
                    // and rank combined progressive above video-only progressive at any quality.
                    val formats = info.callAttr("get", "formats")
                    var combinedUrl: String? = null
                    var combinedExt = "mp4"
                    var combinedHeaders: PyObject? = null
                    var combinedQuality = Long.MIN_VALUE
                    var combinedProtocol: String? = null
                    var videoOnlyUrl: String? = null
                    var videoOnlyExt = "mp4"
                    var videoOnlyHeaders: PyObject? = null
                    var videoOnlyQuality = Long.MIN_VALUE
                    var videoOnlyProtocol: String? = null
                    var manifestSeen = false
                    var manifestBestProtocol: String? = null
                    var manifestBestQuality = Long.MIN_VALUE
                    // Absolute fallback: first URL in list regardless of video stream
                    var firstUrl: String? = null
                    var firstExt = "mp4"
                    var firstHeaders: PyObject? = null

                    if (formats != null) {
                        val fmtIter = formats.callAttr("__iter__")
                        while (true) {
                            val fmt = runCatching { fmtIter.callAttr("__next__") }.getOrNull()
                                ?: break
                            val fmtUrl = fmt.callAttr("get", "url")?.toString() ?: continue
                            val fmtVcodec = fmt.callAttr("get", "vcodec")?.toString() ?: ""
                            val fmtExt = fmt.callAttr("get", "ext")?.toString() ?: "mp4"
                            val fmtProtocol = runCatching {
                                fmt.callAttr("get", "protocol")?.toString()
                            }.getOrNull() ?: ""
                            val fmtHeaders: PyObject? = runCatching {
                                fmt.callAttr("get", "http_headers")
                            }.getOrNull()
                            // Quality score: height px × 10 000 + bitrate kbps
                            val fmtHeight = runCatching {
                                fmt.callAttr("get", "height")?.toString()?.toLongOrNull()
                            }.getOrNull() ?: 0L
                            val fmtTbr = runCatching {
                                fmt.callAttr("get", "tbr")?.toString()?.toDoubleOrNull()?.toLong()
                            }.getOrNull() ?: 0L
                            val fmtId = runCatching {
                                fmt.callAttr("get", "format_id")?.toString()
                            }.getOrNull() ?: "?"
                            val fmtAcodec = runCatching {
                                fmt.callAttr("get", "acodec")?.toString()
                            }.getOrNull() ?: ""
                            val quality = fmtHeight * 10_000L + fmtTbr
                            // Manifest protocols cannot be served by OkHttp as a single MP4 -
                            // routes via yt-dlp Python downloader instead.
                            val isManifest = fmtProtocol == "m3u8" ||
                                fmtProtocol == "m3u8_native" ||
                                fmtProtocol == "http_dash_segments" ||
                                fmtProtocol == "dash"
                            // Progressive = direct HTTP single-file download. yt-dlp uses
                            // "https" / "http" / "" / "rtmp" (rtmp we treat as non-progressive).
                            val isProgressive = !isManifest &&
                                (fmtProtocol == "https" || fmtProtocol == "http" || fmtProtocol.isEmpty())
                            Timber.d(
                                "YtDlpExtractionStrategy: fmt id=%s ext=%s vcodec=%s acodec=%s proto=%s h=%d tbr=%d q=%d %s",
                                fmtId, fmtExt, fmtVcodec.take(8), fmtAcodec.take(8),
                                fmtProtocol, fmtHeight, fmtTbr, quality,
                                if (isManifest) "[manifest]" else if (isProgressive) "[progressive]" else "[other]"
                            )
                            if (firstUrl == null) {
                                firstUrl = fmtUrl; firstExt = fmtExt; firstHeaders = fmtHeaders
                            }
                            // S0166 fix: treat empty vcodec as "video present but codec unknown"
                            // (Instagram's progressive video_versions land here). Only an explicit
                            // "none" means the stream truly has no video.
                            val hasVideo = fmtVcodec != "none"
                            // Same semantics for audio: "none" = explicitly no audio (DASH video
                            // stream), empty = unknown but likely present (Instagram progressive).
                            // We treat empty as having audio so the combined-bucket picker wins.
                            val hasAudio = fmtAcodec != "none"
                            val isVideoOnly = hasVideo && !hasAudio
                            val isCombined = hasVideo && hasAudio
                            when {
                                isProgressive && isCombined && quality > combinedQuality -> {
                                    combinedUrl = fmtUrl
                                    combinedExt = fmtExt
                                    combinedHeaders = fmtHeaders
                                    combinedQuality = quality
                                    combinedProtocol = fmtProtocol
                                }
                                isProgressive && isVideoOnly && quality > videoOnlyQuality -> {
                                    videoOnlyUrl = fmtUrl
                                    videoOnlyExt = fmtExt
                                    videoOnlyHeaders = fmtHeaders
                                    videoOnlyQuality = quality
                                    videoOnlyProtocol = fmtProtocol
                                }
                                isManifest && hasVideo -> {
                                    manifestSeen = true
                                    if (quality > manifestBestQuality) {
                                        manifestBestProtocol = fmtProtocol
                                        manifestBestQuality = quality
                                    }
                                }
                            }
                        }
                    } else {
                        // No formats list - try top-level url (single direct media).
                        // Treated as combined: a direct media URL with no format list is
                        // almost always a self-contained file (mp4/webm with audio inside).
                        combinedUrl = info.callAttr("get", "url")?.toString()
                    }
                    // Pick combined progressive first; only fall back to video-only when no
                    // combined stream exists. Combined of ANY quality beats video-only of any
                    // quality - audible 720p is better UX than silent 1080p for the noLegal
                    // share flow.
                    val progressiveUrl: String?
                    val progressiveExt: String
                    val progressiveHeaders: PyObject?
                    val progressiveQuality: Long
                    val progressiveProtocol: String?
                    val pickedBucket: String
                    if (combinedUrl != null) {
                        progressiveUrl = combinedUrl
                        progressiveExt = combinedExt
                        progressiveHeaders = combinedHeaders
                        progressiveQuality = combinedQuality
                        progressiveProtocol = combinedProtocol
                        pickedBucket = "combined"
                    } else {
                        progressiveUrl = videoOnlyUrl
                        progressiveExt = videoOnlyExt
                        progressiveHeaders = videoOnlyHeaders
                        progressiveQuality = videoOnlyQuality
                        progressiveProtocol = videoOnlyProtocol
                        pickedBucket = if (videoOnlyUrl != null) "video-only" else "none"
                    }
                    Timber.d(
                        "ytdlp pick bucket=%s progressive=%s q=%d proto=%s | combinedSeen=%b videoOnlySeen=%b manifestSeen=%b bestProto=%s q=%d",
                        pickedBucket,
                        progressiveUrl?.take(60) ?: "(none)", progressiveQuality, progressiveProtocol ?: "?",
                        combinedUrl != null, videoOnlyUrl != null,
                        manifestSeen, manifestBestProtocol ?: "?", manifestBestQuality
                    )

                    val rawTitle = info.callAttr("get", "title")?.toString() ?: "download"
                    val safeTitle = rawTitle.replace(Regex("[^A-Za-z0-9._\\- ]"), "_")
                        .trim().take(120)

                    // Decision tree:
                    // 1. Progressive http URL found → DelegateParams → direct.open via OkHttp.
                    //    Falls through to Python on AuthRequired (TikTok signed CDN URL) or
                    //    MimeNotAllowed (returned MIME mismatched whitelist).
                    // 2. Only manifest formats (HLS/DASH) → PythonOnly → yt-dlp downloads
                    //    natively (single-stream HLS variant is supported without ffmpeg).
                    // 3. Last-resort: first format URL even if not progressive (legacy fallback).
                    if (progressiveUrl != null) {
                        val extraHeaders = mutableMapOf<String, String>()
                        val headersSource = progressiveHeaders
                            ?: info.callAttr("get", "http_headers")
                        if (headersSource != null) {
                            val keysIter = runCatching {
                                headersSource.callAttr("keys").callAttr("__iter__")
                            }.getOrNull()
                            if (keysIter != null) {
                                while (true) {
                                    val k = runCatching { keysIter.callAttr("__next__") }
                                        .getOrNull()?.toString() ?: break
                                    val v = runCatching { headersSource.callAttr("get", k) }
                                        .getOrNull()?.toString() ?: continue
                                    extraHeaders[k] = v
                                }
                            }
                        }
                        // Always override Referer; add UA fallback if source didn't provide one.
                        extraHeaders["Referer"] = url
                        // S0182: always override UA with the session-pinned one - the
                        // headers yt-dlp put on the format come from yt-dlp's own UA
                        // which may not match the cookies' origin UA.
                        extraHeaders["User-Agent"] = sessionUa
                        DelegateParams(progressiveUrl, safeTitle, progressiveExt, extraHeaders)
                    } else if (manifestSeen) {
                        Timber.d(
                            "ytdlp route=python-manifest-only url=%s",
                            url
                        )
                        PythonOnly(safeTitle, "mp4")
                    } else if (firstUrl != null) {
                        // Legacy: no progressive, no manifest with video - try first URL
                        // (audio-only formats land here). direct.open will handle MIME check.
                        val extraHeaders = mutableMapOf<String, String>(
                            "Referer" to url,
                        )
                        if (firstHeaders != null) {
                            val keysIter = runCatching {
                                firstHeaders.callAttr("keys").callAttr("__iter__")
                            }.getOrNull()
                            if (keysIter != null) {
                                while (true) {
                                    val k = runCatching { keysIter.callAttr("__next__") }
                                        .getOrNull()?.toString() ?: break
                                    val v = runCatching { firstHeaders.callAttr("get", k) }
                                        .getOrNull()?.toString() ?: continue
                                    extraHeaders[k] = v
                                }
                            }
                        }
                        // S0182: always pin session UA (after copying yt-dlp's headers).
                        extraHeaders["User-Agent"] = sessionUa
                        DelegateParams(firstUrl, safeTitle, firstExt, extraHeaders)
                    } else {
                        OpenResult.NotFound("ytdlp_no_format_url")
                    }
                }.getOrElse { error ->
                    val msg = error.message ?: ""
                    // These yt-dlp errors signal the URL is not handleable by this strategy.
                    // Return NotFound so the chain falls through to html/dynamic/site strategies.
                    if (msg.contains("ytmusic_no_audio_format_available", ignoreCase = true)) {
                        // S0260: keep the selector-miss distinct from generic yt-dlp fallthrough
                        // so the coordinator can differentiate YTMusic audio-only failures.
                        Timber.d(
                            "YtDlpExtractionStrategy: ytmusic no-audio format url=%s reason=%s",
                            url,
                            msg.take(120),
                        )
                        OpenResult.NotFound("ytmusic_no_audio_format_available")
                    } else if (msg.contains("There is no video in this post", ignoreCase = true) ||
                        msg.contains("Unsupported URL:", ignoreCase = true) ||
                        msg.contains("Instagram sent an empty media response", ignoreCase = true) ||
                        // S0187: YouTube PoToken/JS-challenge failure - format selection raises
                        // DownloadError instead of returning an empty list. Return NotFound so
                        // the extraction cascade continues to NewPipeSiteExtractionStrategy.
                        msg.contains("Requested format is not available", ignoreCase = true) ||
                        // S0935: open() runs extract_info(download=false), so ANY yt-dlp
                        // extraction failure here means "this strategy could not get the media"
                        // - the correct signal is NotFound (cascade tries site/html/dynamic),
                        // never Error (which terminates via LinkAutoDownloadCoordinator ->
                        // mapIoError). Device test 2026-07-04 confirmed: the downstream cascade
                        // recovers real Instagram reels that yt-dlp 404s AND 500s on. Match every
                        // HTTP status (403/404/410/429/5xx) and yt-dlp's own DownloadError/
                        // ExtractorError. Genuinely unexpected exceptions carry none of these
                        // markers and still fall to the else branch as Error.
                        msg.contains("HTTP Error", ignoreCase = true) ||
                        msg.contains("DownloadError", ignoreCase = true) ||
                        msg.contains("ExtractorError", ignoreCase = true)) {
                        Timber.d(
                            "YtDlpExtractionStrategy: not applicable url=%s reason=%s",
                            url, msg.take(100)
                        )
                        OpenResult.NotFound("ytdlp_not_applicable")
                    } else {
                        Timber.e(error, "YtDlpExtractionStrategy: open failed url=%s", url)
                        OpenResult.Error(error)
                    }
                }
            }.get()

            when (result) {
                is DelegateParams -> {
                    val cdnHost = result.cdnUrl.toHttpUrlOrNull()?.host.orEmpty().lowercase()
                    val originHost = url.toHttpUrlOrNull()?.host.orEmpty().lowercase()
                    val audioOnly = sessionContext.audioOnlyFor(originHost)
                    if (cdnHost.endsWith(".googlevideo.com") || cdnHost == "googlevideo.com") {
                        // S0190 Phase D: googlevideo throttles non-player linear reads → use yt-dlp
                        // internal downloader (range-chunked, retry, throttle-aware).
                        Timber.d(
                            "ytdlp route=python-googlevideo url=%s audioOnly=%b",
                            url, audioOnly
                        )
                        downloadViaPython(url, cookieFile, result.safeTitle, result.ext, sessionUa, audioOnly) { bytes -> onProgress(bytes, null) }
                    } else {
                        val delegated = direct.open(result.cdnUrl, onProgress, result.extraHeaders)
                        when {
                            delegated is OpenResult.Stream -> {
                                Timber.d(
                                    "ytdlp route=direct-okhttp url=%s ext=%s",
                                    url, result.ext
                                )
                                delegated.copy(fileName = "${result.safeTitle}.${result.ext}")
                            }
                            delegated is OpenResult.Blocked &&
                                    delegated.reason == BlockedReason.AuthRequired -> {
                                // CDN URL is session-bound (e.g., TikTok signed URLs): the URL
                                // was generated by yt-dlp's session and cannot be replayed by
                                // OkHttp even with the same cookies. Fall back to Python download.
                                Timber.d(
                                    "ytdlp route=python-auth-fallback url=%s",
                                    url
                                )
                                downloadViaPython(url, cookieFile, result.safeTitle, result.ext, sessionUa, sessionContext.audioOnlyFor(targetHost)) { bytes -> onProgress(bytes, null) }
                            }
                            delegated is OpenResult.Blocked &&
                                    delegated.reason == BlockedReason.MimeNotAllowed -> {
                                // CDN returned non-media MIME (e.g., HLS manifest application/x-mpegURL).
                                // Fall back to Python download which handles HLS/DASH natively.
                                Timber.d(
                                    "ytdlp route=python-mime-fallback url=%s",
                                    url
                                )
                                downloadViaPython(url, cookieFile, result.safeTitle, result.ext, sessionUa, sessionContext.audioOnlyFor(targetHost)) { bytes -> onProgress(bytes, null) }
                            }
                            else -> delegated
                        }
                    }
                }
                is PythonOnly -> {
                    // No progressive URL - use yt-dlp Python download (HLS/DASH native).
                    downloadViaPython(url, cookieFile, result.safeTitle, result.ext, sessionUa, sessionContext.audioOnlyFor(targetHost)) { bytes -> onProgress(bytes, null) }
                }
                is OpenResult -> result
                else -> OpenResult.NotFound("ytdlp_unexpected_result")
            }
        } finally {
            cookieFile?.let { cookieWriter.deleteCookieFile(it) }
        }
    }

    /**
     * Downloads media via Python/yt-dlp to a temp file and returns a streaming result.
     *
     * Called when:
     * - [direct] returns 403 (TikTok session-bound CDN URL)
     * - [direct] returns MimeNotAllowed (e.g., HLS manifest content-type)
     * - Format selection found only HLS/DASH manifests (no progressive URL)
     * - CDN is *.googlevideo.com (throttled by range-request detection - S0190 Phase D)
     *
     * yt-dlp's Python HTTP client downloads the file directly (re-using the same session
     * that generated the CDN URL, handling HLS/DASH natively without ffmpeg). The temp file
     * is deleted when [OpenResult.Stream.close] fires.
     *
     * Blocks the calling thread (Dispatchers.IO) for the duration of the download.
     * Progress is forwarded via [onProgress] through [ProgressBridge] and yt-dlp's progress_hooks.
     */
    private fun downloadViaPython(
        url: String,
        cookieFile: java.io.File?,
        fallbackTitle: String,
        fallbackExt: String,
        userAgent: String,
        audioOnly: Boolean,
        onProgress: (Long) -> Unit,           // S0190 Phase 03: forwarded to yt-dlp progress_hooks
    ): OpenResult {
        val cacheDir = context.cacheDir
        val stem = "ytdlp_${System.currentTimeMillis()}"
        Timber.d(
            "YtDlpExtractionStrategy: Python download start url=%s stem=%s ua=%s",
            url, stem, userAgent.take(60)
        )

        val progressBridge = ProgressBridge { downloaded, _ -> onProgress(downloaded) }
        val pyResult: PyObject? = runCatching {
            EXECUTOR.submit<PyObject?> {
                runCatching {
                    val py = Python.getInstance()
                    val utils = py.getModule("ytdlp_utils")
                    utils.callAttr(
                        "download_to_file",
                        url,
                        cookieFile?.absolutePath,
                        cacheDir.absolutePath,
                        stem,
                        userAgent,
                        audioOnly,           // S0190: hint propagated from LinkDownloadSessionContext
                        progressBridge,      // S0190 Phase 03: yt-dlp progress_hooks bridge
                    )
                }.getOrElse { e ->
                    Timber.e(e, "YtDlpExtractionStrategy: Python download error url=%s", url)
                    null
                }
            }.get()
        }.getOrElse { e ->
            Timber.e(e, "YtDlpExtractionStrategy: Python download executor error url=%s", url)
            null
        }

        if (pyResult == null) return OpenResult.NotFound("ytdlp_python_download_failed")

        val filePath = pyResult.callAttr("get", "path")?.toString()
            ?: return OpenResult.NotFound("ytdlp_python_download_no_path")
        val ext = pyResult.callAttr("get", "ext")?.toString() ?: fallbackExt
        val rawTitle = pyResult.callAttr("get", "title")?.toString() ?: fallbackTitle
        val safeTitle = rawTitle.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").trim().take(120)

        val file = java.io.File(filePath)
        if (!file.exists()) return OpenResult.NotFound("ytdlp_python_download_file_missing")

        Timber.d(
            "YtDlpExtractionStrategy: Python download done size=%d url=%s",
            file.length(), url
        )
        val mime = when (ext.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            else -> "video/mp4"
        }
        Timber.i(
            "ytdlp python result file=%s ext=%s mime=%s size=%d",
            file.name, ext, mime, file.length()
        )
        return OpenResult.Stream(
            body = file.inputStream(),
            contentLength = file.length().takeIf { it > 0 },
            mime = mime,
            fileName = "$safeTitle.$ext",
            close = { runCatching { file.delete() } },
        )
    }

    /**
     * Chaquopy-compatible progress bridge: Python calls progress_callback(downloaded, total)
     * → invoke(downloaded, total) → onProgress(downloaded).
     * SAM interface maps to Python __call__ via Chaquopy's Java proxy.
     */
    private fun interface ProgressBridge {
        fun invoke(downloaded: Long, total: Long)
    }

    /** Internal carrier for CDN delegation params - direct.open will handle the download. */
    private data class DelegateParams(
        val cdnUrl: String,
        val safeTitle: String,
        val ext: String,
        val extraHeaders: Map<String, String>,
    )

    /** Marker - extraction found only manifest formats; skip direct.open and use Python. */
    private data class PythonOnly(
        val safeTitle: String,
        val ext: String,
    )

    companion object {
        private const val PROBE_TIMEOUT_MS = 10_000L
        private const val MAX_BATCH_ITEMS = 50

        /**
         * Single-thread executor for yt-dlp calls.
         * YoutubeDL instances are not thread-safe - a new instance is created per call,
         * but Chaquopy's GIL wrapper serialises Python execution regardless.
         * Using a dedicated thread avoids interference with IO thread pool task scheduling.
         */
        private val EXECUTOR = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ytdlp-worker").apply { isDaemon = true }
        }
    }
}

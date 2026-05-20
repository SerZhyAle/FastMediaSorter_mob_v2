package com.sza.fastmediasorter.data.link

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.data.link.cookie.LinkDownloadSessionContext
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.usecase.link.BlockedReason
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.SiteBatchItem
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class InvisibleWebViewExtractionStrategy @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @Named("linkDownload") private val httpClient: OkHttpClient,
    private val direct: DirectFileExtractionStrategy,
    private val structuredMediaSniffer: StructuredMediaSniffer,
    private val cookieStore: EncryptedCookieStore,
    private val sessionContext: LinkDownloadSessionContext,
) : UrlExtractionStrategy {

    override val id: String = "dynamic"

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
            Timber.w(io, "InvisibleWebViewExtractionStrategy: probe failed for %s", url)
            ProbeResult.TransientError(io)
        }
    }

    override suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult {
        Timber.d("S0140: pillar-P dynamic invisible-WebView extractor open() entry")
        val httpUrl = url.toHttpUrlOrNull() ?: return OpenResult.Blocked(BlockedReason.NonHttpScheme)

        // YouTube (and youtu.be) are handled exclusively by yt-dlp and NewPipe. The WebView
        // extractor renders YouTube's error/consent pages and picks up static assets such as
        // failure.mp3 - never a real video. Exclude to avoid false-positive downloads.
        val hostLower = httpUrl.host.lowercase()
        if (EXCLUDED_HOSTS.any { hostLower == it || hostLower.endsWith(".$it") }) {
            Timber.d("InvisibleWebViewExtractionStrategy: host excluded url=%s", url.take(80))
            return OpenResult.NotFound("dynamic_excluded_host")
        }

        val renderedCandidates = renderCandidates(httpUrl.toString())
        if (renderedCandidates.isEmpty()) {
            return OpenResult.NotFound("dynamic_no_candidates")
        }

        val probed = probeCandidates(renderedCandidates.take(MAX_PROBED_CANDIDATES))
        val allCandidates = (probed + renderedCandidates.drop(MAX_PROBED_CANDIDATES)).distinctBy { it.url }
        val accepted = allCandidates.filter(::isAcceptedCandidate)
        // TikTok's <video> currentSrc resolves to www.tiktok.com/aweme/v1/play/ - a server-side
        // API redirect that 302s to /404. The real CDN URL comes from the SSR rehydration JSON.
        // Filter it out so CandidateSelectionPolicy picks the v16-webapp CDN entry instead.
        val merged = accepted.filterNot { isTikTokApiRedirect(it.url) }.ifEmpty { accepted }
        if (merged.isEmpty()) {
            return OpenResult.NotFound("dynamic_no_media")
        }

        val nonImageCandidates = merged.filterNot(::isImageCandidate)
        // S0197: a candidate harvested from an authoritative data-sjs payload is itself the
        // signal that this is a legitimate image post (Threads/IG photo single or carousel).
        // Treat that signal as overriding the preview-only guard - without this bypass,
        // Instagram photo posts always return SocialPreviewOnly because they contain no
        // <video> element and only OG/IMG/EMBEDDED_JSON candidates.
        val hasEmbeddedJsonImage = merged.any {
            it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON && isImageCandidate(it)
        }
        if (nonImageCandidates.isEmpty() && !hasEmbeddedJsonImage) {
            val host = httpUrl.host
            if (KnownAuthResources.isPreviewSensitiveHost(host)) {
                LinkDownloadTrace.verbose(
                    "dynamic-strategy social-preview-only host=${LinkDownloadTrace.truncateUrl(url)}",
                )
                return OpenResult.SocialPreviewOnly(host = host)
            }
        } else if (nonImageCandidates.isEmpty() && hasEmbeddedJsonImage) {
            LinkDownloadTrace.verbose(
                "dynamic-strategy embedded-json-image-present bypass-preview-only " +
                    "host=${LinkDownloadTrace.truncateUrl(url)}",
            )
        }

        val preferred = nonImageCandidates.ifEmpty { merged }
        // S0224: batch result notifications show batch.items.size. Restrict the batch pool to
        // authoritative embedded-json slides when they exist, otherwise request-sniffed preview
        // assets inflate the visible total without producing additional saved files.
        val batchCandidates = CandidateSelectionPolicy.batchPool(preferred)
        if (shouldReturnBatch(batchCandidates)) {
            return OpenResult.Batch(
                items = batchCandidates.take(MAX_BATCH_ITEMS).map { SiteBatchItem(url = it.url) },
            )
        }

        val chosen = CandidateSelectionPolicy.choose(preferred)
            ?: return OpenResult.NotFound("dynamic_no_media")
        val manifest = chosen.manifest
        if (manifest != null) {
            return OpenResult.Streaming(
                manifest = manifest,
                tentativeFileName = deriveStreamingFileName(chosen.url),
            )
        }
        // S0171: replay the page request context on the CDN re-fetch - Instagram's CDN edge
        // rejects requests without a `Referer`, and a real browser `User-Agent` is needed because
        // the link-download OkHttp client otherwise sends `okhttp/4.x` (rejected by many CDNs).
        val replayHeaders = cdnReplayHeaders(chosen)
        return direct.open(chosen.url, onProgress, replayHeaders)
    }

    /**
     * S0171: headers to attach when re-fetching a CDN URL [candidate] harvested from the page.
     * - browser `User-Agent` always (replaces OkHttp's default `okhttp` UA);
     * - `Referer: <pageOrigin>/` when the page origin is known (required by the Instagram CDN);
     * - `Accept` for video content types to nudge content negotiation;
     * - `Range: bytes=0-` for TikTok CDN hosts (TikTok edge 403s some requests without it).
     */
    private fun cdnReplayHeaders(candidate: HtmlMediaCandidate): Map<String, String> = buildMap {
        put("User-Agent", DESKTOP_CHROME_UA)
        put("Accept", "video/mp4,video/webm,video/*;q=0.9,*/*;q=0.8")
        candidate.pageOrigin?.takeIf { it.isNotBlank() }?.let { put("Referer", "$it/") }
        val host = candidate.url.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
        if (isTikTokHost(host) || host.contains("tiktokcdn") || host.contains("byteoversea") || host.contains("muscdn")) {
            put("Range", "bytes=0-")
            // S0171 Step 9: TikTok CDN edge validates browser security headers - without these
            // it returns 403 even when session cookies are correctly forwarded.
            put("Sec-Fetch-Dest", "video")
            put("Sec-Fetch-Mode", "no-cors")
            put("Sec-Fetch-Site", "cross-site")
        }
    }

    private suspend fun probeCandidates(input: List<HtmlMediaCandidate>): List<HtmlMediaCandidate> {
        if (input.isEmpty()) return input
        val timed = withTimeoutOrNull(CANDIDATE_BUDGET_MS) {
            coroutineScope {
                input.map { candidate ->
                    async(Dispatchers.IO) {
                        if (candidate.manifest != null || isLikelySegment(candidate.url)) return@async candidate
                        runCatching {
                            // S0171: replay the page context on the HEAD probe too - the Instagram
                            // CDN edge serves a bogus tiny 200 (with a `video/mp4` Content-Type) when
                            // the Referer is missing, which would otherwise poison the size heuristics.
                            val request = Request.Builder().url(candidate.url).head().apply {
                                cdnReplayHeaders(candidate).forEach { (name, value) -> header(name, value) }
                            }.build()
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

    private fun isAcceptedCandidate(candidate: HtmlMediaCandidate): Boolean {
        if (isLikelySegment(candidate.url)) return false
        if (candidate.manifest != null) return true
        // S0171: a candidate that came from an explicit media element (<video>/<source>/<audio>)
        // or a structured-data field (og:video, JSON-LD, the TikTok rehydration JSON pushed as
        // VIDEO_TAG) is media by construction - accept it even when the URL has no media extension
        // and we haven't probed its MIME (TikTok playAddr URLs are extension-less, signed paths).
        if (candidate.source in TRUSTED_MEDIA_SOURCES) return true
        val mime = candidate.tentativeMime?.substringBefore(';')?.trim()
        return MediaMimeWhitelist.isAllowed(mime) || pathHasAllowedMediaExtension(candidate.url)
    }

    private fun isImageCandidate(candidate: HtmlMediaCandidate): Boolean {
        val mime = candidate.tentativeMime?.substringBefore(';')?.trim()?.lowercase()
        if (mime?.startsWith("image/") == true) return true
        return when (candidate.source) {
            HtmlMediaCandidate.Source.OG_IMAGE,
            HtmlMediaCandidate.Source.IMG_TAG,
            HtmlMediaCandidate.Source.IMG_SRCSET,
            -> true

            else -> imageExtension(candidate.url)
        }
    }

    private fun shouldReturnBatch(candidates: List<HtmlMediaCandidate>): Boolean {
        if (candidates.size < 2) return false
        // S0197: an EMBEDDED_JSON candidate is authoritative (e.g. a Threads/IG carousel slide
        // emitted from data-sjs). Treat it as substantial regardless of probed size - sizes for
        // these CDN URLs often arrive after the HEAD-probe budget expires.
        val substantial = candidates.count {
            it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON ||
                it.manifest != null ||
                (it.tentativeSizeBytes ?: 0L) >= MIN_BATCH_BYTES
        }
        return substantial >= 2
    }

    private fun deriveStreamingFileName(url: String): String {
        val lastSegment = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull { it.isNotBlank() }
        if (lastSegment.isNullOrBlank()) return "download_${System.currentTimeMillis()}.mp4"
        val withoutExt = lastSegment.substringBeforeLast('.', missingDelimiterValue = lastSegment)
        val safe = withoutExt.replace(Regex("[^a-zA-Z0-9_.\\-]"), "_").take(80).ifBlank { "download" }
        return "$safe.mp4"
    }

    private fun pathHasAllowedMediaExtension(url: String): Boolean {
        val ext = url.toHttpUrlOrNull()?.encodedPath
            ?.substringAfterLast('.', "")
            ?.substringBefore('?')
            ?.lowercase()
            .orEmpty()
        if (ext.isBlank() || ext.length > 5) return false
        return MediaMimeWhitelist.mimeForExtension(ext) != null
    }

    private fun imageExtension(url: String): Boolean {
        val ext = url.toHttpUrlOrNull()?.encodedPath
            ?.substringAfterLast('.', "")
            ?.substringBefore('?')
            ?.lowercase()
            .orEmpty()
        return ext in IMAGE_EXTENSIONS
    }

    private fun isLikelySegment(url: String): Boolean {
        val ext = url.toHttpUrlOrNull()?.encodedPath
            ?.substringAfterLast('.', "")
            ?.substringBefore('?')
            ?.lowercase()
            .orEmpty()
        return ext in SEGMENT_EXTENSIONS
    }

    private suspend fun renderCandidates(url: String): List<HtmlMediaCandidate> =
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                val observedRequests = LinkedHashMap<String, Pair<HtmlMediaCandidate.Source, String>>()
                // S0171: the page URL the WebView is currently on - read from the WebView's worker
                // thread in shouldInterceptRequest, written on the main thread in onPageStarted /
                // onPageFinished; tracked so each candidate can carry its page origin for the
                // Referer header on the CDN re-fetch (and to follow short-URL redirects).
                val currentPageUrl = AtomicReference(url)
                val finished = AtomicBoolean(false)
                var webView: WebView? = null
                lateinit var hardStop: Runnable

                fun destroyWebView() {
                    runCatching { mainHandler.removeCallbacks(hardStop) }
                    val current = webView ?: return
                    runCatching { current.stopLoading() }
                    runCatching { current.loadUrl("about:blank") }
                    runCatching { current.clearHistory() }
                    runCatching { current.removeAllViews() }
                    runCatching { current.destroy() }
                    webView = null
                }

                fun rememberCandidate(rawUrl: String?, source: HtmlMediaCandidate.Source) {
                    val normalized = rawUrl?.toHttpUrlOrNull()?.toString() ?: return
                    if (normalized == url || isLikelySegment(normalized)) return
                    synchronized(observedRequests) {
                        if (observedRequests.size >= MAX_INTERCEPTED_REQUESTS) return
                        if (!observedRequests.containsKey(normalized)) {
                            observedRequests[normalized] = manifestAwareSource(normalized, source) to currentPageUrl.get()
                        }
                    }
                }

                fun finish(pageCandidates: List<HtmlMediaCandidate>) {
                    if (!finished.compareAndSet(false, true)) return
                    val intercepted = synchronized(observedRequests) {
                        observedRequests.mapNotNull { (candidateUrl, pair) ->
                            candidateFor(candidateUrl, pair.first, originOf(pair.second))
                        }
                    }
                    destroyWebView()
                    if (continuation.isActive) {
                        continuation.resume(mergePageAndInterceptedCandidates(pageCandidates, intercepted))
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        if (finished.compareAndSet(false, true)) {
                            destroyWebView()
                        }
                    }
                }

                val web = WebView(appContext)
                webView = web
                injectSavedCookies(url)
                configureWebView(
                    webView = web,
                    pageUrl = url,
                    currentPageUrl = currentPageUrl,
                    mainHandler = mainHandler,
                    rememberCandidate = ::rememberCandidate,
                    finish = ::finish,
                )
                hardStop = Runnable { finish(emptyList()) }
                mainHandler.postDelayed(hardStop, HARD_TIMEOUT_MS)
                LinkDownloadTrace.verbose(
                    "dynamic-extractor start url=${LinkDownloadTrace.truncateUrl(url)} timeoutMs=$HARD_TIMEOUT_MS",
                )
                web.loadUrl(url)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(
        webView: WebView,
        pageUrl: String,
        currentPageUrl: AtomicReference<String>,
        mainHandler: Handler,
        rememberCandidate: (String?, HtmlMediaCandidate.Source) -> Unit,
        finish: (List<HtmlMediaCandidate>) -> Unit,
    ) {
        val domScheduled = AtomicBoolean(false)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        // S0171: TikTok mobile web immediately tries to deep-link into the native app
        // (snssdk1233://…, bytedance://…) and serves an "open in app" stub with no video CDN
        // URLs. A desktop Chrome UA makes TikTok render the full SSR page, which embeds the
        // video URL in the __UNIVERSAL_DATA_FOR_REHYDRATION__ JSON (parsed by DOM_DISCOVERY_SCRIPT).
        val pageHost = pageUrl.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
        if (isTikTokHost(pageHost)) {
            webView.settings.userAgentString = DESKTOP_CHROME_UA
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase()
                return scheme != null && scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView?, loadedUrl: String?, favicon: Bitmap?) {
                super.onPageStarted(view, loadedUrl, favicon)
                if (!loadedUrl.isNullOrBlank()) currentPageUrl.set(loadedUrl)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                super.shouldInterceptRequest(view, request).also {
                    if (request?.isForMainFrame == true) return@also
                    rememberCandidate(request?.url?.toString(), HtmlMediaCandidate.Source.INLINE_LINK)
                }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) currentPageUrl.set(url)
                if (!domScheduled.compareAndSet(false, true)) return
                mainHandler.postDelayed(
                    {
                        val target = view ?: return@postDelayed finish(emptyList())
                        runCatching {
                            target.evaluateJavascript(DOM_DISCOVERY_SCRIPT) { raw ->
                                val pageUrl = currentPageUrl.get()
                                val domCandidates = parseDomCandidates(raw, pageUrl)
                                if (shouldInspectEmbeddedJson(pageUrl)) {
                                    runCatching {
                                        target.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                            val decodedHtml = decodeEvaluatedHtml(html)
                                            val baseUri = pageUrl ?: url ?: pageUrl
                                            CoroutineScope(Dispatchers.IO).launch {
                                                // Threads hydrates the authoritative post media in data-sjs;
                                                // prefer that over noisy preview assets captured via request sniffing.
                                                val embeddedCandidates = decodedHtml
                                                    ?.takeIf { !baseUri.isNullOrBlank() }
                                                    ?.let { structuredMediaSniffer.sniffEmbeddedJson(it, baseUri!!) }
                                                    .orEmpty()
                                                mainHandler.post {
                                                    if (BuildConfig.DEBUG) {
                                                        dumpDecodedPageHtml(decodedHtml, currentPageUrl.get())
                                                    }
                                                    finish(embeddedCandidates + domCandidates)
                                                }
                                            }
                                        }
                                    }.onFailure {
                                        LinkDownloadTrace.verbose(
                                            "dynamic-extractor embedded-json-eval failed reason=${it::class.simpleName}",
                                        )
                                        finishWithOptionalDebugDump(target, domCandidates, currentPageUrl.get(), finish)
                                    }
                                } else {
                                    finishWithOptionalDebugDump(target, domCandidates, pageUrl, finish)
                                }
                            }
                        }.onFailure {
                            LinkDownloadTrace.verbose(
                                "dynamic-extractor dom-eval failed reason=${it::class.simpleName}",
                            )
                            finish(emptyList())
                        }
                    },
                    DOM_SETTLE_MS,
                )
            }
        }
    }

    private fun injectSavedCookies(url: String) {
        val cookieManager = CookieManager.getInstance()
        val host = url.toHttpUrlOrNull()?.host ?: return
        val contextCookies = sessionContext.cookiesFor(host)
        if (contextCookies != null) {
            contextCookies.forEach { cookie ->
                cookieManager.setCookie(url, buildCookieHeader(cookie, host))
            }
        } else {
            cookieDomainsFor(host).forEach { domain ->
                @Suppress("DEPRECATION")
                cookieStore.loadFor(domain).forEach { cookie ->
                    cookieManager.setCookie(url, buildCookieHeader(cookie, domain))
                }
            }
        }
        cookieManager.flush()
    }

    private fun buildCookieHeader(cookie: java.net.HttpCookie, fallbackDomain: String): String {
        val parts = mutableListOf("${cookie.name}=${cookie.value}")
        parts += "Domain=${cookie.domain ?: fallbackDomain}"
        parts += "Path=${cookie.path ?: "/"}"
        if (cookie.secure) parts += "Secure"
        if (cookie.isHttpOnly) parts += "HttpOnly"
        return parts.joinToString("; ")
    }

    private fun cookieDomainsFor(host: String): List<String> {
        val parts = host.split('.').filter { it.isNotBlank() }
        if (parts.size <= 2) return listOf(host)
        val parent = parts.takeLast(2).joinToString(".")
        return listOf(host, parent).distinct()
    }

    private fun parseDomCandidates(raw: String?, pageUrl: String?): List<HtmlMediaCandidate> {
        val payload = raw?.takeIf { it.isNotBlank() && it != "null" } ?: return emptyList()
        val pageOrigin = originOf(pageUrl)
        return runCatching {
            val json = JSONArray(payload)
            buildList {
                for (index in 0 until json.length()) {
                    val node = json.optJSONObject(index) ?: continue
                    val url = node.optString("url")
                    val mappedSource = when (node.optString("source")) {
                        "VIDEO_TAG" -> HtmlMediaCandidate.Source.VIDEO_TAG
                        "AUDIO_TAG" -> HtmlMediaCandidate.Source.AUDIO_TAG
                        "SOURCE_TAG" -> HtmlMediaCandidate.Source.SOURCE_TAG
                        "IMG_TAG" -> HtmlMediaCandidate.Source.IMG_TAG
                        "IMG_SRCSET" -> HtmlMediaCandidate.Source.IMG_SRCSET
                        else -> HtmlMediaCandidate.Source.INLINE_LINK
                    }
                    candidateFor(url, mappedSource, pageOrigin)?.let(::add)
                }
            }
        }.getOrElse {
            LinkDownloadTrace.verbose("dynamic-extractor dom-parse failed reason=${it::class.simpleName}")
            emptyList()
        }
    }

    private fun finishWithOptionalDebugDump(
        webView: WebView,
        pageCandidates: List<HtmlMediaCandidate>,
        pageUrl: String?,
        finish: (List<HtmlMediaCandidate>) -> Unit,
    ) {
        if (BuildConfig.DEBUG && pageCandidates.isEmpty()) {
            runCatching {
                webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
                    dumpPageHtml(html, pageUrl)
                    finish(pageCandidates)
                }
            }.onFailure { finish(pageCandidates) }
            return
        }
        finish(pageCandidates)
    }

    private fun mergePageAndInterceptedCandidates(
        pageCandidates: List<HtmlMediaCandidate>,
        intercepted: List<HtmlMediaCandidate>,
    ): List<HtmlMediaCandidate> {
        val prioritizedPage = pageCandidates.filter { it.source in PRIORITIZED_PAGE_SOURCES }
        val fallbackPage = pageCandidates.filterNot { it.source in PRIORITIZED_PAGE_SOURCES }
        return (prioritizedPage + intercepted + fallbackPage).distinctBy { it.url }
    }

    private fun shouldInspectEmbeddedJson(pageUrl: String?): Boolean {
        val host = pageUrl?.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
        // S0197: host gating delegated to KnownAuthResources so Instagram (instagram.com)
        // is covered alongside Threads without duplicating a host list.
        return KnownAuthResources.supportsEmbeddedJson(host)
    }

    private fun originOf(pageUrl: String?): String? =
        pageUrl?.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }

    private fun isTikTokHost(host: String): Boolean =
        TIKTOK_HOSTS.any { host == it || host.endsWith(".$it") }

    // Detects TikTok server-side API redirect candidates - www.tiktok.com/aweme/v1/play/ returns
    // 302 → /404, never a real media file. The CDN URL is always on a v16-webapp-prime subdomain.
    private fun isTikTokApiRedirect(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.host == "www.tiktok.com" && parsed.encodedPath.startsWith("/aweme/v1/play/")
    }

    private fun manifestAwareSource(url: String, fallback: HtmlMediaCandidate.Source): HtmlMediaCandidate.Source {
        val lowerPath = url.toHttpUrlOrNull()?.encodedPath?.lowercase().orEmpty()
        return when {
            lowerPath.endsWith(".m3u8") -> HtmlMediaCandidate.Source.HLS_MANIFEST
            lowerPath.endsWith(".mpd") -> HtmlMediaCandidate.Source.DASH_MANIFEST
            else -> fallback
        }
    }

    private fun candidateFor(
        url: String,
        source: HtmlMediaCandidate.Source,
        pageOrigin: String? = null,
    ): HtmlMediaCandidate? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val normalized = httpUrl.toString()
        val lowerPath = httpUrl.encodedPath.lowercase()
        return when {
            lowerPath.endsWith(".m3u8") -> HtmlMediaCandidate(
                url = normalized,
                source = HtmlMediaCandidate.Source.HLS_MANIFEST,
                tentativeMime = "application/vnd.apple.mpegurl",
                tentativeSizeBytes = null,
                manifest = StreamingManifest.Hls(normalized),
                pageOrigin = pageOrigin,
            )

            lowerPath.endsWith(".mpd") -> HtmlMediaCandidate(
                url = normalized,
                source = HtmlMediaCandidate.Source.DASH_MANIFEST,
                tentativeMime = "application/dash+xml",
                tentativeSizeBytes = null,
                manifest = StreamingManifest.Dash(normalized),
                pageOrigin = pageOrigin,
            )

            else -> HtmlMediaCandidate(
                url = normalized,
                source = source,
                tentativeMime = null,
                tentativeSizeBytes = null,
                pageOrigin = pageOrigin,
            )
        }
    }

    // S0171: writes a truncated page HTML snapshot for post-hoc diagnosis when DOM extraction
    // produces no candidates. Only called in DEBUG builds. File lands in external app storage.
    private fun dumpPageHtml(html: String?, pageUrl: String?) {
        dumpDecodedPageHtml(decodeEvaluatedHtml(html), pageUrl)
    }

    private fun dumpDecodedPageHtml(decodedHtml: String?, pageUrl: String?) {
        if (decodedHtml.isNullOrBlank()) return
        val host = pageUrl?.toHttpUrlOrNull()?.host ?: "unknown"
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(appContext.getExternalFilesDir(null), "link_debug_${host}_$ts.html")
        runCatching {
            val limited = if (decodedHtml.length > 512 * 1024) decodedHtml.substring(0, 512 * 1024) else decodedHtml
            file.writeText(limited, Charsets.UTF_8)
        }.onFailure {
            Timber.w("InvisibleWebViewExtractionStrategy: page-html-dump failed - %s", it.message)
        }
    }

    private fun decodeEvaluatedHtml(rawHtml: String?): String? {
        val payload = rawHtml?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        return try {
            JSONArray("[$payload]").getString(0)
        } catch (_: Exception) {
            payload
        }
    }

    private companion object {
        // Instagram and Threads are heavy React SPAs - hydration alone takes 10+ seconds.
        // 8 s was consistently triggering hard-stop before onPageFinished, yielding no candidates.
        const val HARD_TIMEOUT_MS = 22_000L
        const val DOM_SETTLE_MS = 4_000L
        const val CANDIDATE_BUDGET_MS = 4_000L
        const val MAX_INTERCEPTED_REQUESTS = 50
        const val MAX_PROBED_CANDIDATES = 12
        const val MAX_BATCH_ITEMS = 12
        const val MIN_BATCH_BYTES = 1_048_576L

        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")
        val SEGMENT_EXTENSIONS = setOf("m4s", "ts", "cmf", "cmfa", "cmfv", "mp4a", "part")

        // Hosts where the dynamic WebView extractor must not run. These hosts have dedicated
        // extraction strategies (yt-dlp + NewPipe for YouTube). The WebView renders their
        // error/consent pages instead of real content and produces false-positive media hits
        // (e.g. YouTube's failure.mp3 error sound asset).
        val EXCLUDED_HOSTS = setOf("youtube.com", "youtu.be")

        // S0171: hosts whose mobile web serves an "open in app" interstitial - load with a desktop UA.
        val TIKTOK_HOSTS = setOf("tiktok.com", "vm.tiktok.com", "vt.tiktok.com")
        const val DESKTOP_CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        // S0171: candidate sources that are media by construction - accepted without a MIME probe.
        val TRUSTED_MEDIA_SOURCES = setOf(
            HtmlMediaCandidate.Source.VIDEO_TAG,
            HtmlMediaCandidate.Source.SOURCE_TAG,
            HtmlMediaCandidate.Source.AUDIO_TAG,
            HtmlMediaCandidate.Source.OG_VIDEO,
            HtmlMediaCandidate.Source.JSON_LD,
            HtmlMediaCandidate.Source.EMBEDDED_JSON,
            HtmlMediaCandidate.Source.TWITTER_PLAYER_STREAM,
            HtmlMediaCandidate.Source.HLS_MANIFEST,
            HtmlMediaCandidate.Source.DASH_MANIFEST,
        )

        val PRIORITIZED_PAGE_SOURCES = TRUSTED_MEDIA_SOURCES

        val DOM_DISCOVERY_SCRIPT = """
            (function() {
              const out = [];
              const push = function(url, source) {
                if (typeof url !== 'string') return;
                const value = url.trim();
                if (!value) return;
                out.push({ url: value, source: source });
              };

              document.querySelectorAll('video').forEach(function(node) {
                push(node.currentSrc || node.src, 'VIDEO_TAG');
                node.querySelectorAll('source').forEach(function(source) {
                  push(source.src, 'SOURCE_TAG');
                });
              });

              document.querySelectorAll('audio').forEach(function(node) {
                push(node.currentSrc || node.src, 'AUDIO_TAG');
                node.querySelectorAll('source').forEach(function(source) {
                  push(source.src, 'SOURCE_TAG');
                });
              });

              document.querySelectorAll('img').forEach(function(node) {
                push(node.currentSrc || node.src, 'IMG_TAG');
                if (node.srcset) {
                  node.srcset.split(',').forEach(function(entry) {
                    const token = entry.trim().split(/\s+/)[0];
                    push(token, 'IMG_SRCSET');
                  });
                }
              });

              // S0171: TikTok embeds the playable video URL in the SSR rehydration JSON.
              try {
                const el = document.getElementById('__UNIVERSAL_DATA_FOR_REHYDRATION__');
                if (el && el.textContent) {
                  const data = JSON.parse(el.textContent);
                  const scope = data && data.__DEFAULT_SCOPE__;
                  const detail = scope && scope['webapp.video-detail'];
                  const item = detail && detail.itemInfo && detail.itemInfo.itemStruct;
                  const video = item && item.video;
                  if (video) {
                    // Prioritise direct CDN URLs - downloadAddr and bitrateInfo carry v16-webapp*
                    // hosts; playAddr sometimes resolves to a www.tiktok.com API redirect which
                    // triggers MimeBlocked after the 302 (observed build 333, ZNRsTRFXg/).
                    if (video.downloadAddr && video.downloadAddr !== video.playAddr) push(video.downloadAddr, 'VIDEO_TAG');
                    const bitrateInfo = video.bitrateInfo;
                    if (Array.isArray(bitrateInfo) && bitrateInfo.length > 0) {
                      const playAddr = bitrateInfo[0].PlayAddr;
                      const urlList = playAddr && playAddr.UrlList;
                      if (Array.isArray(urlList) && urlList.length > 0) push(urlList[0], 'VIDEO_TAG');
                    }
                    if (video.playAddr) push(video.playAddr, 'VIDEO_TAG');
                  }
                }
              } catch (e) { /* schema drift - fall back to intercepted requests */ }

              return out;
            })();
        """.trimIndent()
    }
}
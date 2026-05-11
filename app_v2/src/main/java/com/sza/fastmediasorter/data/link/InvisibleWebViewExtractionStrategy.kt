package com.sza.fastmediasorter.data.link

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.data.link.cookie.LinkDownloadSessionContext
import com.sza.fastmediasorter.domain.usecase.link.BlockedReason
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.SiteBatchItem
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * S0140 pillar P: headless WebView fallback for pages where media URLs appear only
 * after JavaScript runs. The strategy stays generic: it observes live DOM sources
 * and subresource requests, then hands the chosen candidate back to the existing
 * direct/streaming pipeline instead of downloading inside WebView itself.
 */
@Singleton
class InvisibleWebViewExtractionStrategy @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @Named("linkDownload") private val httpClient: OkHttpClient,
    private val direct: DirectFileExtractionStrategy,
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
        val httpUrl = url.toHttpUrlOrNull()
            ?: return OpenResult.Blocked(BlockedReason.NonHttpScheme)

        val renderedCandidates = renderCandidates(httpUrl.toString())
        if (renderedCandidates.isEmpty()) {
            return OpenResult.NotFound("dynamic_no_candidates")
        }

        val probed = probeCandidates(renderedCandidates.take(MAX_PROBED_CANDIDATES))
        val merged = (probed + renderedCandidates.drop(MAX_PROBED_CANDIDATES))
            .filter(::isAcceptedCandidate)
            .distinctBy { it.url }

        if (merged.isEmpty()) {
            return OpenResult.NotFound("dynamic_no_media")
        }

        val nonImageCandidates = merged.filterNot(::isImageCandidate)
        // S0151: if the dynamic render produced only image candidates on a known video-first
        // social host, signal SocialPreviewOnly — do not fall back to downloading the preview.
        if (nonImageCandidates.isEmpty()) {
            val host = httpUrl.host
            if (KnownAuthResources.isPreviewSensitiveHost(host)) {
                LinkDownloadTrace.verbose(
                    "dynamic-strategy social-preview-only host=${LinkDownloadTrace.truncateUrl(url)}",
                )
                Timber.d("S0151: dynamic-strategy social-preview-only host=$host")
                return OpenResult.SocialPreviewOnly(host = host)
            }
        }
        val preferred = nonImageCandidates.ifEmpty { merged }
        if (shouldReturnBatch(preferred)) {
            return OpenResult.Batch(
                items = preferred
                    .take(MAX_BATCH_ITEMS)
                    .map { SiteBatchItem(url = it.url) },
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
        return direct.open(chosen.url, onProgress)
    }

    private suspend fun probeCandidates(input: List<HtmlMediaCandidate>): List<HtmlMediaCandidate> {
        if (input.isEmpty()) return input
        val timed = withTimeoutOrNull(CANDIDATE_BUDGET_MS) {
            coroutineScope {
                input.map { candidate ->
                    async(Dispatchers.IO) {
                        if (candidate.manifest != null || isLikelySegment(candidate.url)) return@async candidate
                        runCatching {
                            val request = Request.Builder().url(candidate.url).head().build()
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
        val substantial = candidates.count {
            it.manifest != null || (it.tentativeSizeBytes ?: 0L) >= MIN_BATCH_BYTES
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
                val observedRequests = LinkedHashMap<String, HtmlMediaCandidate.Source>()
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
                            observedRequests[normalized] = manifestAwareSource(normalized, source)
                        }
                    }
                }

                fun finish(domCandidates: List<HtmlMediaCandidate>) {
                    if (!finished.compareAndSet(false, true)) return
                    val intercepted = synchronized(observedRequests) {
                        observedRequests.mapNotNull { (candidateUrl, source) ->
                            candidateFor(candidateUrl, source)
                        }
                    }
                    destroyWebView()
                    if (continuation.isActive) {
                        continuation.resume((intercepted + domCandidates).distinctBy { it.url })
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
        mainHandler: Handler,
        rememberCandidate: (String?, HtmlMediaCandidate.Source) -> Unit,
        finish: (List<HtmlMediaCandidate>) -> Unit,
    ) {
        val domScheduled = AtomicBoolean(false)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase()
                return scheme != null && scheme != "http" && scheme != "https"
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                super.shouldInterceptRequest(view, request).also {
                    if (request?.isForMainFrame == true) return@also
                    rememberCandidate(request?.url?.toString(), HtmlMediaCandidate.Source.INLINE_LINK)
                }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!domScheduled.compareAndSet(false, true)) return
                mainHandler.postDelayed(
                    {
                        val target = view ?: return@postDelayed finish(emptyList())
                        runCatching {
                            target.evaluateJavascript(DOM_DISCOVERY_SCRIPT) { raw ->
                                finish(parseDomCandidates(raw))
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
        // S0155: prefer session-context cookies when an account was selected for this run.
        val contextCookies = sessionContext.cookiesFor(host)
        if (contextCookies != null) {
            contextCookies.forEach { cookie ->
                val header = buildCookieHeader(cookie, host)
                cookieManager.setCookie(url, header)
            }
        } else {
            cookieDomainsFor(host).forEach { domain ->
                @Suppress("DEPRECATION")
                cookieStore.loadFor(domain).forEach { cookie ->
                    val header = buildCookieHeader(cookie, domain)
                    cookieManager.setCookie(url, header)
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

    private fun parseDomCandidates(raw: String?): List<HtmlMediaCandidate> {
        val payload = raw?.takeIf { it.isNotBlank() && it != "null" } ?: return emptyList()
        return runCatching {
            val json = JSONArray(payload)
            buildList {
                for (index in 0 until json.length()) {
                    val node = json.optJSONObject(index) ?: continue
                    val url = node.optString("url")
                    val source = node.optString("source")
                    val mappedSource = when (source) {
                        "VIDEO_TAG" -> HtmlMediaCandidate.Source.VIDEO_TAG
                        "AUDIO_TAG" -> HtmlMediaCandidate.Source.AUDIO_TAG
                        "SOURCE_TAG" -> HtmlMediaCandidate.Source.SOURCE_TAG
                        "IMG_TAG" -> HtmlMediaCandidate.Source.IMG_TAG
                        "IMG_SRCSET" -> HtmlMediaCandidate.Source.IMG_SRCSET
                        else -> HtmlMediaCandidate.Source.INLINE_LINK
                    }
                    candidateFor(url, mappedSource)?.let(::add)
                }
            }
        }.getOrElse {
            LinkDownloadTrace.verbose("dynamic-extractor dom-parse failed reason=${it::class.simpleName}")
            emptyList()
        }
    }

    private fun manifestAwareSource(
        url: String,
        fallback: HtmlMediaCandidate.Source,
    ): HtmlMediaCandidate.Source {
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
                manifest = com.sza.fastmediasorter.domain.model.link.StreamingManifest.Hls(normalized),
            )

            lowerPath.endsWith(".mpd") -> HtmlMediaCandidate(
                url = normalized,
                source = HtmlMediaCandidate.Source.DASH_MANIFEST,
                tentativeMime = "application/dash+xml",
                tentativeSizeBytes = null,
                manifest = com.sza.fastmediasorter.domain.model.link.StreamingManifest.Dash(normalized),
            )

            else -> HtmlMediaCandidate(
                url = normalized,
                source = source,
                tentativeMime = null,
                tentativeSizeBytes = null,
            )
        }
    }

    private companion object {
        const val HARD_TIMEOUT_MS = 8_000L
        const val DOM_SETTLE_MS = 3_500L
        const val CANDIDATE_BUDGET_MS = 4_000L
        const val MAX_INTERCEPTED_REQUESTS = 50
        const val MAX_PROBED_CANDIDATES = 12
        const val MAX_BATCH_ITEMS = 12
        const val MIN_BATCH_BYTES = 1_048_576L

        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")
        val SEGMENT_EXTENSIONS = setOf("m4s", "ts", "cmf", "cmfa", "cmfv", "mp4a", "part")

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

              return out;
            })();
        """.trimIndent()
    }
}
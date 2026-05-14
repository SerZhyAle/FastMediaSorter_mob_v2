package com.sza.fastmediasorter.ui.share.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.data.link.auth.AccountNameHintExtractor
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.net.HttpCookie
import java.util.UUID

@AndroidEntryPoint
class WebViewAuthDialogFragment : DialogFragment() {

    private val viewModel: WebViewAuthViewModel by viewModels()
    private var webView: WebView? = null
    private var saveButton: MaterialButton? = null
    private var targetHost: String = ""
    // In harvest mode the dialog opens the content URL directly and auto-closes once a CDN
    // video URL is intercepted. The user just needs to scroll/tap play on the page.
    private var harvestMode: Boolean = false
    // Guards against emitting multiple results when several CDN requests fire at once.
    private val mediaHarvested = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_webview_auth, container, false)
        val targetUrl = arguments?.getString(ARG_TARGET_URL).orEmpty()
        harvestMode = arguments?.getBoolean(ARG_HARVEST_MODE, false) ?: false
        targetHost = Uri.parse(targetUrl).host.orEmpty()

        val web = view.findViewById<WebView>(R.id.webViewAuth)
        webView = web
        configureWebView(web)
        saveButton = view.findViewById(R.id.btnWebviewAuthSave)
        if (targetUrl.isNotBlank()) {
            val initialCookies = CookieManager.getInstance().getCookie(targetUrl)?.split(';')?.size ?: 0
            LinkDownloadTrace.tag(
                "webview-auth opened for ${LinkDownloadTrace.truncateUrl(targetUrl)}, cookies-before=$initialCookies, harvest=$harvestMode",
            )
            web.loadUrl(targetUrl)
        }

        view.findViewById<MaterialButton>(R.id.btnWebviewAuthCancel).setOnClickListener {
            emitResultAndDismiss(saved = false)
        }
        if (harvestMode) {
            // In harvest mode there's nothing for the user to save manually — hide the button.
            saveButton?.visibility = View.GONE
        } else {
            saveButton?.setOnClickListener { harvestAndDismiss() }
            refreshSaveButtonState()
        }
        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun configureWebView(web: WebView) {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (!harvestMode) refreshSaveButtonState()
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") return false
                LinkDownloadTrace.verbose("webview-auth blocked-redirect scheme=$scheme host=${uri.host.orEmpty()}")
                if (scheme == "intent") {
                    runCatching {
                        val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        intent.getStringExtra("browser_fallback_url")?.takeIf { it.isNotBlank() }
                    }.getOrNull()?.let { fallbackUrl ->
                        view?.loadUrl(fallbackUrl)
                    }
                }
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): android.webkit.WebResourceResponse? {
                // In harvest mode: intercept CDN video/HLS/DASH requests and auto-close
                // as soon as the first media URL is found. The page is fully rendered
                // because it’s a real visible WebView with real user gestures.
                if (harvestMode && !request?.isForMainFrame!!) {
                    val url = request?.url?.toString()
                    if (url != null && isMediaCandidateUrl(url)) {
                        if (mediaHarvested.compareAndSet(false, true)) {
                            Timber.i(
                                "[S0166] harvest-mode: intercepted CDN media url=%s",
                                LinkDownloadTrace.truncateUrl(url),
                            )
                            // shouldInterceptRequest is called on a background thread — post dismiss to main.
                            view?.handler?.post {
                                if (isAdded && !isDetached) {
                                    emitResultAndDismiss(saved = false, mediaUrl = url)
                                }
                            }
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!harvestMode) refreshSaveButtonState()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    LinkDownloadTrace.verbose(
                        "fallback=webview-auth-load-error code=${error?.errorCode} desc=${error?.description}",
                    )
                }
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
    }

    /**
     * Returns true for URLs that look like streamable video content — used by harvest mode
     * to auto-detect when the page requests a CDN video file.
     * We check path extensions only (no response headers available in shouldInterceptRequest).
     */
    private fun isMediaCandidateUrl(url: String): Boolean {
        val path = android.net.Uri.parse(url).path?.lowercase() ?: return false
        return path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".mpd") ||
            path.endsWith(".webm") || path.endsWith(".mkv") || path.contains(".mp4?")
    }

    private fun harvestAndDismiss() {
        if (targetHost.isBlank()) {
            emitResultAndDismiss(saved = false)
            return
        }
        val cookies = currentCookies()
        Timber.d("S0170: webview auth cookies collected (deduped) — host=%s count=%d", targetHost, cookies.size)
        if (cookies.isEmpty()) {
            refreshSaveButtonState()
            view?.let {
                Snackbar.make(it, R.string.s0140_webview_auth_sign_in_first, Snackbar.LENGTH_LONG).show()
            }
            return
        }
        val hint = AccountNameHintExtractor.extract(cookies)
        val defaultAccountName = getString(R.string.s0157_account_default_name)
        val nameInput = TextInputEditText(requireContext()).apply {
            setText(hint ?: defaultAccountName)
            setHint(R.string.s0155_name_account_hint)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.s0155_name_account_title)
            .setView(nameInput)
            .setPositiveButton(R.string.s0155_name_account_positive) { _, _ ->
                val typed = nameInput.text?.toString()?.trim()
                val displayName = when {
                    !typed.isNullOrBlank() -> typed
                    !hint.isNullOrBlank() -> hint
                    else -> defaultAccountName
                }
                val accountId = UUID.randomUUID().toString()
                // S0182: pin the WebView's User-Agent at login time. Re-played later by
                // yt-dlp/OkHttp on every cookie-bearing request so the same `sessionid`
                // never appears under more than one UA.
                val capturedUa = webView?.settings?.userAgentString?.takeIf { it.isNotBlank() }
                viewModel.saveSession(targetHost, accountId, displayName, cookies, capturedUa)
                scrubWebViewState()
                Timber.i(
                    "[S0166] browser login saved: account=%s host=%s",
                    displayName,
                    targetHost,
                )
                Timber.d(
                    "S0155: WebView auth saved host=%s accountId=%s ua=%s",
                    targetHost, accountId, capturedUa?.take(60) ?: "(none)"
                )
                emitResultAndDismiss(saved = true, accountId = accountId)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                emitResultAndDismiss(saved = false)
            }
            .setOnCancelListener {
                emitResultAndDismiss(saved = false)
            }
            .show()
    }

    private fun scrubWebViewState() {
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { CookieManager.getInstance().flush() }
        runCatching {
            webView?.clearCache(true)
            webView?.clearHistory()
            webView?.clearFormData()
        }
    }

    private fun refreshSaveButtonState() {
        saveButton?.isEnabled = currentCookies().isNotEmpty()
    }

    private fun currentCookies(): List<HttpCookie> {
        if (targetHost.isBlank()) return emptyList()
        val raw = try {
            CookieManager.getInstance().getCookie("https://$targetHost") ?: ""
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            LinkDownloadTrace.verbose("fallback=webview-auth-cookie-read reason=${throwable::class.simpleName}")
            return emptyList()
        }
        return parseCookieHeader(raw, targetHost)
    }

    private fun emitResultAndDismiss(saved: Boolean, accountId: String? = null, mediaUrl: String? = null) {
        runCatching {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf(
                    RESULT_HOST to targetHost,
                    RESULT_SAVED to saved,
                    RESULT_ACCOUNT_ID to accountId,
                    RESULT_MEDIA_URL to mediaUrl,
                ),
            )
        }
        dismissAllowingStateLoss()
    }

    private fun parseCookieHeader(header: String, host: String): List<HttpCookie> {
        if (header.isBlank()) return emptyList()
        // S0170 BUG-6: CookieManager.getCookie() lists a name once per (domain,path) scope —
        // e.g. csrftoken at .instagram.com and at www.instagram.com — so the raw header carries
        // duplicates. Keep the last-seen value per name.
        return header.split(';')
            .mapNotNull { entry ->
                val pair = entry.trim().split('=', limit = 2)
                if (pair.size != 2 || pair[0].isBlank()) return@mapNotNull null
                HttpCookie(pair[0], pair[1]).apply {
                    domain = host
                    path = "/"
                    secure = true
                }
            }
            .associateBy { it.name }
            .values
            .toList()
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        saveButton = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_TARGET_URL = "target_url"
        const val ARG_HARVEST_MODE = "harvest_mode"
        const val RESULT_KEY = "s0144_webview_auth_result"
        const val RESULT_HOST = "host"
        const val RESULT_SAVED = "saved"
        const val RESULT_ACCOUNT_ID = "account_id"
        /** Set when harvest mode intercepts a CDN media URL — use for direct download. */
        const val RESULT_MEDIA_URL = "media_url"

        fun newInstance(targetUrl: String, harvestMode: Boolean = false): WebViewAuthDialogFragment =
            WebViewAuthDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_URL, targetUrl)
                    putBoolean(ARG_HARVEST_MODE, harvestMode)
                }
            }
    }
}
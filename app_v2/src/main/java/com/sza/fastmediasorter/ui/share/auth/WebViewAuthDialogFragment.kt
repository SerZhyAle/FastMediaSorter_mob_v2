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
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.net.HttpCookie

/**
 * S0116 §5.1 pillar L: in-app WebView dialog that lets the user authenticate to
 * any user-supplied domain and harvest the resulting session cookies into
 * [com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore].
 *
 * Lifecycle:
 *
 * 1. Open the requested URL in a fresh WebView.
 * 2. User completes login (CAPTCHA / 2FA / OAuth — whatever the site requires).
 * 3. User taps "Save authorization" — fragment harvests cookies via
 *    [CookieManager], delegates to [WebViewAuthViewModel.saveSession], then
 *    clears WebView state so cookies do not leak to other WebView contexts.
 *
 * Argument: [ARG_TARGET_URL] — http(s) URL to open.
 */
@AndroidEntryPoint
class WebViewAuthDialogFragment : DialogFragment() {

    private val viewModel: WebViewAuthViewModel by viewModels()
    private var webView: WebView? = null
    private var saveButton: MaterialButton? = null
    private var targetHost: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_webview_auth, container, false)
        val targetUrl = arguments?.getString(ARG_TARGET_URL).orEmpty()
        targetHost = Uri.parse(targetUrl).host.orEmpty()

        val web = view.findViewById<WebView>(R.id.webViewAuth)
        webView = web
        configureWebView(web)
        saveButton = view.findViewById(R.id.btnWebviewAuthSave)
        if (targetUrl.isNotBlank()) {
            val initialCookies = CookieManager.getInstance().getCookie(targetUrl)?.split(';')?.size ?: 0
            LinkDownloadTrace.tag(
                "webview-auth opened for ${LinkDownloadTrace.truncateUrl(targetUrl)}, cookies-before=$initialCookies",
            )
            web.loadUrl(targetUrl)
        }

        view.findViewById<MaterialButton>(R.id.btnWebviewAuthCancel).setOnClickListener {
            emitResultAndDismiss(saved = false)
        }
        saveButton?.setOnClickListener {
            harvestAndDismiss()
        }
        refreshSaveButtonState()
        return view
    }

    override fun onStart() {
        super.onStart()
        // S0141: DialogFragment defaults the window to wrap_content, which collapses the
        // embedded WebView to zero pixels before any page renders. Force the dialog window
        // to fill the screen so the WebView is actually visible and receives touch input.
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun configureWebView(web: WebView) {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // S0141: many login flows rely on JS confirm/alert dialogs and progress events;
        // a missing WebChromeClient silently breaks those. A minimal default is enough.
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                refreshSaveButtonState()
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") return false
                // S0144: a site (e.g. Instagram) redirected to a non-web scheme.
                // Never hand intent:// / app schemes to the engine — that yields
                // net::ERR_UNKNOWN_URL_SCHEME and a blank dialog.
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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                refreshSaveButtonState()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                // S0141: surface load failures so we can diagnose blank pages without a debugger.
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

    private fun harvestAndDismiss() {
        if (targetHost.isBlank()) {
            emitResultAndDismiss(saved = false)
            return
        }
        val cookies = currentCookies()
        if (cookies.isEmpty()) {
            refreshSaveButtonState()
            view?.let {
                Snackbar.make(it, R.string.s0140_webview_auth_sign_in_first, Snackbar.LENGTH_LONG).show()
            }
            return
        }
        viewModel.saveSession(targetHost, cookies)

        // S0116 §5.1 pillar L: scrub WebView state so harvested cookies do not
        // leak into other WebView contexts that may share the singleton CookieManager.
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { CookieManager.getInstance().flush() }
        runCatching {
            webView?.clearCache(true)
            webView?.clearHistory()
            webView?.clearFormData()
        }
        emitResultAndDismiss(saved = true)
    }

    private fun refreshSaveButtonState() {
        // WebView's CookieManager does not expose cookie attributes in a structured form,
        // so "at least one cookie visible for this host" is the safest live signal we have
        // that the user has completed enough of the auth flow to save a usable session.
        saveButton?.isEnabled = currentCookies().isNotEmpty()
    }

    private fun currentCookies(): List<HttpCookie> {
        if (targetHost.isBlank()) return emptyList()
        val raw = try {
            // getCookie() requires a full URL with scheme; a bare hostname silently returns null
            // on Chromium-based WebView even when cookies exist for that host.
            CookieManager.getInstance().getCookie("https://$targetHost") ?: ""
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LinkDownloadTrace.verbose("fallback=webview-auth-cookie-read reason=${t::class.simpleName}")
            return emptyList()
        }
        return parseCookieHeader(raw, targetHost)
    }

    // S0144: notify the host (e.g. ReceiveShareActivity) that the auth dialog closed,
    // so a proactive share-auth offer can resume the original download afterwards.
    private fun emitResultAndDismiss(saved: Boolean) {
        runCatching {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf(RESULT_HOST to targetHost, RESULT_SAVED to saved),
            )
        }
        dismissAllowingStateLoss()
    }

    private fun parseCookieHeader(header: String, host: String): List<HttpCookie> {
        if (header.isBlank()) return emptyList()
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
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        saveButton = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_TARGET_URL = "target_url"

        // S0144: fragment-result channel for hosts that need to react to dialog dismissal.
        const val RESULT_KEY = "s0144_webview_auth_result"
        const val RESULT_HOST = "host"
        const val RESULT_SAVED = "saved"

        fun newInstance(targetUrl: String): WebViewAuthDialogFragment =
            WebViewAuthDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_TARGET_URL, targetUrl) }
            }
    }
}

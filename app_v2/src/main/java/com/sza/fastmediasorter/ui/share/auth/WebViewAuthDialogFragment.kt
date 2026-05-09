package com.sza.fastmediasorter.ui.share.auth

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import dagger.hilt.android.AndroidEntryPoint
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
        if (targetUrl.isNotBlank()) {
            val initialCookies = CookieManager.getInstance().getCookie(targetUrl)?.split(';')?.size ?: 0
            LinkDownloadTrace.tag(
                "webview-auth opened for ${LinkDownloadTrace.truncateUrl(targetUrl)}, cookies-before=$initialCookies",
            )
            web.loadUrl(targetUrl)
        }

        view.findViewById<MaterialButton>(R.id.btnWebviewAuthCancel).setOnClickListener {
            dismissAllowingStateLoss()
        }
        view.findViewById<MaterialButton>(R.id.btnWebviewAuthSave).setOnClickListener {
            harvestAndDismiss()
        }
        return view
    }

    private fun configureWebView(web: WebView) {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
    }

    private fun harvestAndDismiss() {
        if (targetHost.isBlank()) {
            dismissAllowingStateLoss()
            return
        }
        val raw = try {
            CookieManager.getInstance().getCookie(targetHost) ?: ""
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LinkDownloadTrace.verbose("fallback=webview-auth-dismiss-no-save reason=${t::class.simpleName}")
            dismissAllowingStateLoss()
            return
        }
        val cookies = parseCookieHeader(raw, targetHost)
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
        super.onDestroyView()
    }

    companion object {
        const val ARG_TARGET_URL = "target_url"

        fun newInstance(targetUrl: String): WebViewAuthDialogFragment =
            WebViewAuthDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_TARGET_URL, targetUrl) }
            }
    }
}

package com.sza.fastmediasorter.ui.launcher.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber

/**
 * The shared machinery of every live-Web gadget frame on the Launcher desktop (S2241, S2285): WebView
 * settings, the progress-driven [WebViewClient], the `intent://` and custom-scheme routing, the touch
 * interception that keeps a gadget's own scrolling out of the desktop pager, the injected CSS that
 * hides the "open in app" banners, and the pause/resume/destroy forwards.
 *
 * Construction is two-phase because a subclass inflates its own ViewBinding: the subclass initializes
 * its binding first, then calls [attachWebView] from its own `init`. The base never calls into the
 * subclass while its own constructor runs, which is what keeps the accessors below safe to read.
 */
abstract class WebGadgetFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** The gadget's WebView, owned by the subclass binding. */
    protected abstract val webView: WebView

    /** The loading indicator shown between page start and page finish. */
    protected abstract val progressView: View

    /** Id of the injected `<style>` element, unique per service so two frames cannot clash. */
    protected abstract val styleElementId: String

    /** Extra CSS selectors this service's banner needs on top of the shared set. */
    protected open val extraHiddenSelectors: List<String> = emptyList()

    /** Extra lowercase button captions that dismiss this service's banner. */
    protected open val extraDismissPhrases: List<String> = emptyList()

    /** Extra delays, in milliseconds, at which the dismiss pass runs again. */
    protected open val extraDismissDelaysMs: List<Int> = emptyList()

    /** Wires the WebView; call from the subclass `init` once its binding exists. */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    protected fun attachWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressView.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressView.isVisible = false
                view?.evaluateJavascript(bannerDismissScript(), null)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                return when {
                    url.startsWith("intent://") -> handleIntentUrl(view, url)
                    url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://") -> {
                        handleExternalScheme(url)
                    }
                    else -> false
                }
            }
        }

        // Intercept touch events so the gadget's own scroll/drag does not page the Launcher desktop.
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            v.onTouchEvent(event)
        }
    }

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    fun onPause() {
        webView.onPause()
    }

    fun onResume() {
        webView.onResume()
    }

    fun onDestroy() {
        webView.destroy()
    }

    private fun bannerDismissScript(): String {
        val selectors = (SHARED_HIDDEN_SELECTORS + extraHiddenSelectors).joinToString(",\n                        ")
        val phrases = (SHARED_DISMISS_PHRASES + extraDismissPhrases)
            .joinToString(" || ") { "txt.indexOf('$it') !== -1" }
        val reClicks = (listOf(FIRST_RECLICK_DELAY_MS) + extraDismissDelaysMs)
            .joinToString("\n                ") { "setTimeout(autoClick, $it);" }
        return """
            (function() {
                var style = document.getElementById('$styleElementId');
                if (!style) {
                    style = document.createElement('style');
                    style.id = '$styleElementId';
                    style.type = 'text/css';
                    style.innerHTML = `
                        $selectors {
                            display: none !important;
                            visibility: hidden !important;
                            height: 0 !important;
                        }
                    `;
                    document.head.appendChild(style);
                }
                var autoClick = function() {
                    var buttons = document.querySelectorAll('button, a, div[role="button"]');
                    for (var i = 0; i < buttons.length; i++) {
                        var txt = (buttons[i].innerText || buttons[i].textContent || '').trim().toLowerCase();
                        if ($phrases) {
                            buttons[i].click();
                            break;
                        }
                    }
                };
                autoClick();
                $reClicks
            })();
        """.trimIndent()
    }

    private fun handleIntentUrl(view: WebView?, url: String): Boolean {
        runCatching {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
            if (!fallbackUrl.isNullOrEmpty()) {
                view?.loadUrl(fallbackUrl)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (context.packageManager.resolveActivityCompat(intent) != null) {
                    context.startActivity(intent)
                }
            }
        }.onFailure { Timber.w(it, "Failed to handle intent URL in live frame: $url") }
        return true
    }

    private fun handleExternalScheme(url: String): Boolean {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivityCompat(intent) != null) {
                context.startActivity(intent)
            }
        }.onFailure { Timber.w(it, "Failed to handle custom scheme in live frame: $url") }
        return true
    }

    private companion object {
        val SHARED_HIDDEN_SELECTORS = listOf(
            ".ml-app-promo, #app-promo, .app-promo",
            "div[aria-label*=\"Open in app\"], div[aria-label*=\"Открыть в приложении\"]",
            "button[aria-label*=\"Open in app\"], button[aria-label*=\"Открыть в приложении\"]"
        )
        val SHARED_DISMISS_PHRASES = listOf("keep using web", "stay on web", "продолжить в браузере")
        const val FIRST_RECLICK_DELAY_MS = 600
    }
}

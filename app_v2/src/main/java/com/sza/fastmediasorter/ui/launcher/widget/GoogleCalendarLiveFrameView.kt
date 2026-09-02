package com.sza.fastmediasorter.ui.launcher.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.WidgetGoogleCalendarLiveFrameBinding
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber

/**
 * S2285: Interactive live Google Calendar frame view for the Launcher desktop.
 *
 * Disallows parent scroll interception for calendar event browsing, and configures JS
 * auto-dismiss for mobile app promotion banners on automotive head units.
 */
class GoogleCalendarLiveFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = WidgetGoogleCalendarLiveFrameBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    init {
        setupWebView()
        setupListeners()
        loadUrl(DEFAULT_CALENDAR_URL)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        with(binding.webViewLiveCalendar.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        binding.webViewLiveCalendar.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progressLiveCalendarLoading.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressLiveCalendarLoading.isVisible = false
                injectCustomCalendarStyles(view)
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

        // Intercept touch events so calendar scroll/drag does not scroll Launcher desktop pages.
        binding.webViewLiveCalendar.setOnTouchListener { v, event ->
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

    private fun injectCustomCalendarStyles(view: WebView?) {
        val js = """
            (function() {
                var style = document.getElementById('fms-calendar-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'fms-calendar-style';
                    style.type = 'text/css';
                    style.innerHTML = `
                        .ml-app-promo, #app-promo, .app-promo,
                        div[aria-label*="Open in app"], div[aria-label*="Открыть в приложении"],
                        button[aria-label*="Open in app"], button[aria-label*="Открыть в приложении"] {
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
                        if (txt === 'keep using web' || txt === 'stay on web' ||
                            txt.indexOf('keep using web') !== -1 || txt.indexOf('stay on web') !== -1 ||
                            txt.indexOf('продолжить в браузере') !== -1) {
                            buttons[i].click();
                            break;
                        }
                    }
                };
                autoClick();
                setTimeout(autoClick, 600);
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
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
        }.onFailure { Timber.w(it, "Failed to handle intent URL in live calendar frame: $url") }
        return true
    }

    private fun handleExternalScheme(url: String): Boolean {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivityCompat(intent) != null) {
                context.startActivity(intent)
            }
        }.onFailure { Timber.w(it, "Failed to handle custom scheme in live calendar frame: $url") }
        return true
    }

    private fun setupListeners() {
        binding.btnReloadCalendar.setOnClickListener {
            loadUrl(DEFAULT_CALENDAR_URL)
        }
    }

    fun loadUrl(url: String) {
        binding.webViewLiveCalendar.loadUrl(url)
    }

    fun onPause() {
        binding.webViewLiveCalendar.onPause()
    }

    fun onResume() {
        binding.webViewLiveCalendar.onResume()
    }

    fun onDestroy() {
        binding.webViewLiveCalendar.destroy()
    }

    companion object {
        const val DEFAULT_CALENDAR_URL = "https://calendar.google.com/"
    }
}

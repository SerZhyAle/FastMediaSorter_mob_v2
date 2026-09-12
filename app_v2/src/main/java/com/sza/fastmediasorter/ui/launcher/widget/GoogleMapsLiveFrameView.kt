package com.sza.fastmediasorter.ui.launcher.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.WebMercatorTile
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber

/**
 * S2241: Interactive live Google Maps frame view for the Launcher desktop.
 *
 * Disallows parent scroll interception for panning/zooming, configures HTML5 geolocation
 * and JS auto-dismiss for "Keep using web" app promotion modals on automotive head units.
 */
class GoogleMapsLiveFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val webView: WebView
    private val progressBar: ProgressBar
    private val btnRecenter: ImageButton

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_google_maps_live_frame, this, true)
        webView = findViewById(R.id.webViewLiveMap)
        progressBar = findViewById(R.id.progressLiveMapLoading)
        btnRecenter = findViewById(R.id.btnRecenterMap)

        setupWebView()
        setupListeners()
        loadMapUrl(DEFAULT_MAP_URL)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            setGeolocationEnabled(false)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            /**
             * S2292: the embedded page never polls GPS on its own schedule. `setGeolocationEnabled`
             * above already disables the API, so this should never fire - it stays as the explicit
             * denial that keeps a later edit of that settings block from silently restoring the
             * grant. Centring does not depend on it: [updateLocation] loads an already-centred URL.
             */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, false, false)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.isVisible = false
                injectCustomMapStyles(view)
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

        // Intercept touch events so map drag/zoom does not scroll the Launcher desktop pages.
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

    /**
     * S2292: the position is coarsened to the centre of its [DEFAULT_ZOOM] Web Mercator tile before
     * anything stores or publishes it - the same precision class the static map gadget already ships
     * through `OsmMapTileProvider`. The precise value is deliberately never written to a field: the
     * recenter button reads [lastLatitude] and [lastLongitude], so keeping it would give the exact
     * coordinate a second route into a Google URL.
     */
    fun updateLocation(latitude: Double, longitude: Double) {
        val tileLatitude = WebMercatorTile.coarseLatitude(latitude, DEFAULT_ZOOM)
        val tileLongitude = WebMercatorTile.coarseLongitude(longitude, DEFAULT_ZOOM)
        lastLatitude = tileLatitude
        lastLongitude = tileLongitude
        loadMapUrl(mapUrlFor(tileLatitude, tileLongitude))
    }

    private fun mapUrlFor(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/@$latitude,$longitude,${DEFAULT_ZOOM}z"

    private fun injectCustomMapStyles(view: WebView?) {
        val js = """
            (function() {
                var style = document.getElementById('fms-map-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'fms-map-style';
                    style.type = 'text/css';
                    style.innerHTML = `
                        .ml-app-promo, #app-promo, .app-promo,
                        div[aria-label*="Open in app"], div[aria-label*="Открыть в приложении"],
                        button[aria-label*="Open in app"], button[aria-label*="Открыть в приложении"],
                        .section-hero-header-app-promo, div[data-section-id="mb"],
                        .promoted-app-banner {
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
                            txt.indexOf('продолжить в браузере') !== -1 || txt.indexOf('использовать веб-версию') !== -1) {
                            buttons[i].click();
                            break;
                        }
                    }
                };
                autoClick();
                setTimeout(autoClick, 600);
                setTimeout(autoClick, 1800);
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

    private fun setupListeners() {
        btnRecenter.setOnClickListener {
            val lat = lastLatitude
            val lng = lastLongitude
            if (lat != null && lng != null) {
                loadMapUrl(mapUrlFor(lat, lng))
            } else {
                loadMapUrl(DEFAULT_MAP_URL)
            }
        }
    }

    fun loadMapUrl(url: String) {
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

    companion object {
        const val DEFAULT_MAP_URL = "https://www.google.com/maps"
        private const val DEFAULT_ZOOM = 15
    }
}

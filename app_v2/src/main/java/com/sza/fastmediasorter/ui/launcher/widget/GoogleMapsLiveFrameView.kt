package com.sza.fastmediasorter.ui.launcher.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R

/**
 * S2241: Interactive live Google Maps frame view for the Launcher desktop.
 *
 * Enforces touch gesture disallowance on the parent scroll container so map panning and zooming
 * do not trigger launcher desktop page swipes. Handles lifecycle pause/resume for lazy resource
 * optimization.
 */
class GoogleMapsLiveFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val webView: WebView
    private val progressBar: ProgressBar
    private val btnRecenter: ImageButton

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
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.isVisible = false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
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

    private fun setupListeners() {
        btnRecenter.setOnClickListener {
            loadMapUrl(DEFAULT_MAP_URL)
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
    }
}

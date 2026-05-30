package com.sza.fastmediasorter.ui.player.helpers

import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import io.documentnode.epub4j.domain.Book
import timber.log.Timber

/** WebView configuration, asset interception, and full destroy/clean-up for [EpubViewerManager]. */
internal class EpubWebViewLifecycle(
    private val binding: ActivityPlayerUnifiedBinding,
    private val resourceContentHelper: EpubResourceContentHelper,
    private val selectionBridge: EpubViewerManager.EpubSelectionBridge,
    private val onPageRendered: () -> Unit,
    private val swipeGestureProvider: () -> android.view.GestureDetector,
    private val bookProvider: () -> Book?,
) {
    private var webView: WebView? = null

    fun current(): WebView? = webView

    fun getOrCreate(): WebView {
        webView?.let { return it }
        return WebView(binding.epubWebView.context).also { wv ->
            binding.epubWebView.addView(
                wv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            webView = wv
            configure(wv)
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun configure(wv: WebView) {
        wv.settings.javaScriptEnabled = true
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        wv.settings.setSupportZoom(true)
        wv.isLongClickable = true
        wv.isClickable = true
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.setOnLongClickListener(null)
        wv.addJavascriptInterface(selectionBridge, "EpubSelectionBridge")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.post { binding.progressBar.isVisible = false }
                onPageRendered()
            }
            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
            ): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.startsWith("file:///android_asset/")) {
                    val resourcePath = url.removePrefix("file:///android_asset/")
                    val book = bookProvider()
                    if (book != null) {
                        resourceContentHelper.assetResponse(resourcePath, book)?.let { return it }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        wv.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            swipeGestureProvider().onTouchEvent(event)
            false
        }
    }

    /** Load "about:blank" - safely (Quest WebView throws SecurityException). */
    fun loadBlank() {
        try {
            webView?.loadUrl("about:blank")
        } catch (e: SecurityException) {
            Timber.w("EpubWebViewLifecycle: about:blank denied (Quest/HorizonOS) - ignored")
        }
    }

    /** Detach + destroy WebView; clear WebViewDatabase HTTP-auth credentials (ML-010). */
    fun destroyAndClear() {
        webView?.let { wv ->
            try {
                (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                wv.removeAllViews()
                wv.clearCache(true)
                wv.destroy()
            } catch (e: Exception) {
                Timber.e(e, "EpubWebViewLifecycle: Error destroying WebView")
            }
        }
        try {
            android.webkit.WebViewDatabase.getInstance(binding.root.context).clearHttpAuthUsernamePassword()
        } catch (e: Exception) {
            Timber.e(e, "EpubWebViewLifecycle: Error clearing WebViewDatabase credentials (ML-010)")
        }
        webView = null
    }
}

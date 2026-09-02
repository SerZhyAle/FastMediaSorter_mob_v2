package com.sza.fastmediasorter.ui.launcher.gadget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherYoutubeMusicBinding
import com.sza.fastmediasorter.util.resolveActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import timber.log.Timber
import javax.inject.Inject

/**
 * S2235: YouTube Music launcher frame gadget.
 * Displays embedded YouTube Music (https://music.youtube.com) in a WebView frame within desktop cell.
 */
class YouTubeMusicGadget @Inject constructor() : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_YOUTUBE_MUSIC
    override val defaultSpanW: Int = DEFAULT_SPAN_W
    override val defaultSpanH: Int = DEFAULT_SPAN_H
    override val minSpanW: Int = MIN_SPAN_W
    override val minSpanH: Int = MIN_SPAN_H
    override val labelRes: Int = R.string.launcher_gadget_youtube_music
    override val iconRes: Int = R.drawable.ic_youtube_music
    override val requiresResourceParam: Boolean = false

    override fun isAvailable(): Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        YouTubeMusicGadgetView(container.context)

    private companion object {
        const val DEFAULT_SPAN_W = 3
        const val DEFAULT_SPAN_H = 2
        const val MIN_SPAN_W = 2
        const val MIN_SPAN_H = 2
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class YouTubeMusicGadgetView(context: Context) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherYoutubeMusicBinding.inflate(LayoutInflater.from(context), this)
    private var isLoaded = false

    init {
        contentDescription = context.getString(R.string.launcher_gadget_youtube_music)
        setupWebView()
        binding.gadgetYouTubeMusicRefresh.setOnClickListener {
            binding.gadgetYouTubeMusicProgress.isVisible = true
            binding.gadgetYouTubeMusicWebView.reload()
        }
        binding.gadgetYouTubeMusicOpen.setOnClickListener {
            openExternal(context, binding.gadgetYouTubeMusicWebView.url ?: TARGET_URL)
        }
    }

    private fun setupWebView() {
        val webView = binding.gadgetYouTubeMusicWebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.gadgetYouTubeMusicProgress.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.gadgetYouTubeMusicProgress.isVisible = false
                isLoaded = true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    binding.gadgetYouTubeMusicProgress.isVisible = false
                }
            }
        }
    }

    override suspend fun CoroutineScope.onActive() {
        if (!isLoaded) {
            binding.gadgetYouTubeMusicProgress.isVisible = true
            binding.gadgetYouTubeMusicWebView.loadUrl(TARGET_URL)
        } else {
            binding.gadgetYouTubeMusicWebView.onResume()
        }
        try {
            awaitCancellation()
        } finally {
            binding.gadgetYouTubeMusicWebView.onPause()
        }
    }

    override fun onDetachedFromWindow() {
        binding.gadgetYouTubeMusicWebView.run {
            stopLoading()
            onPause()
            destroy()
        }
        super.onDetachedFromWindow()
    }

    private fun openExternal(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivityCompat(intent) != null) {
            runCatching { context.startActivity(intent) }
                .onFailure { Timber.w(it, "YouTube Music gadget: failed to open external url=%s", url) }
        }
    }

    companion object {
        private const val TARGET_URL = "https://music.youtube.com"
    }
}

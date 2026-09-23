package com.sza.fastmediasorter.ui.launcher.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import com.sza.fastmediasorter.databinding.WidgetGoogleKeepLiveFrameBinding

/**
 * S2285: Interactive live Google Keep frame view for the Launcher desktop.
 *
 * Everything shared with the other live-Web gadgets lives in [WebGadgetFrameView]; this class owns
 * its binding, its reload button and the Keep URL.
 */
class GoogleKeepLiveFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebGadgetFrameView(context, attrs, defStyleAttr) {

    private val binding = WidgetGoogleKeepLiveFrameBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    override val webView: WebView
        get() = binding.webViewLiveKeep

    override val progressView: View
        get() = binding.progressLiveKeepLoading

    override val styleElementId: String = "fms-keep-style"

    init {
        attachWebView()
        binding.btnReloadKeep.setOnClickListener {
            loadUrl(DEFAULT_KEEP_URL)
        }
        loadUrl(DEFAULT_KEEP_URL)
    }

    companion object {
        const val DEFAULT_KEEP_URL = "https://keep.google.com/"
    }
}

package com.sza.fastmediasorter.ui.launcher.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import com.sza.fastmediasorter.databinding.WidgetGoogleCalendarLiveFrameBinding

/**
 * S2285: Interactive live Google Calendar frame view for the Launcher desktop.
 *
 * Everything shared with the other live-Web gadgets lives in [WebGadgetFrameView]; this class owns
 * its binding, its reload button and the calendar URL.
 */
class GoogleCalendarLiveFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebGadgetFrameView(context, attrs, defStyleAttr) {

    private val binding = WidgetGoogleCalendarLiveFrameBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    override val webView: WebView
        get() = binding.webViewLiveCalendar

    override val progressView: View
        get() = binding.progressLiveCalendarLoading

    override val styleElementId: String = "fms-calendar-style"

    init {
        attachWebView()
        binding.btnReloadCalendar.setOnClickListener {
            loadUrl(DEFAULT_CALENDAR_URL)
        }
        loadUrl(DEFAULT_CALENDAR_URL)
    }

    companion object {
        const val DEFAULT_CALENDAR_URL = "https://calendar.google.com/"
    }
}

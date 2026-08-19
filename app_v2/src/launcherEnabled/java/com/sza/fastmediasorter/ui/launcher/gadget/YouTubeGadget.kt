package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherYoutubeBinding
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber
import javax.inject.Inject

/**
 * S1755: YouTube launcher gadget.
 * Opens the YouTube application or browser URL when tapped.
 */
class YouTubeGadget @Inject constructor() : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_YOUTUBE
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_youtube
    override val iconRes: Int = R.drawable.ic_youtube
    override val requiresResourceParam: Boolean = false

    override fun isAvailable(): Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        YouTubeGadgetView(container.context)
}

private class YouTubeGadgetView(context: Context) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherYoutubeBinding.inflate(LayoutInflater.from(context), this)

    init {
        contentDescription = context.getString(R.string.launcher_gadget_youtube)
        setOnClickListener { openYouTube(context) }
    }

    private fun openYouTube(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivityCompat(intent) == null) {
            Timber.i("YouTube gadget: no app or browser to open YouTube")
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "YouTube gadget: failed to open YouTube") }
    }
}

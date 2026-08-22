package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherYoutubeMusicBinding
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber
import javax.inject.Inject

/**
 * S1755: YouTube Music launcher gadget.
 * Opens the YouTube Music application or browser URL when tapped.
 */
class YouTubeMusicGadget @Inject constructor() : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_YOUTUBE_MUSIC
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_youtube_music
    override val iconRes: Int = R.drawable.ic_youtube_music
    override val requiresResourceParam: Boolean = false

    override fun isAvailable(): Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        YouTubeMusicGadgetView(container.context)
}

private class YouTubeMusicGadgetView(context: Context) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherYoutubeMusicBinding.inflate(LayoutInflater.from(context), this)

    init {
        contentDescription = context.getString(R.string.launcher_gadget_youtube_music)
        setOnClickListener { openYouTubeMusic(context) }
    }

    private fun openYouTubeMusic(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivityCompat(intent) == null) {
            Timber.i("YouTube Music gadget: no app or browser to open YouTube Music")
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "YouTube Music gadget: failed to open YouTube Music") }
    }
}

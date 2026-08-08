package com.sza.fastmediasorter.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.ui.streams.helpers.StreamHeadlessPlayManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamShortcutRouteManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1471 - transparent, no-UI trampoline a pinned stream shortcut targets. Decides "play silently or
 * open the Streams screen" before a single view exists, which is the whole point: the old shortcut
 * intent went straight to [StreamsActivity] and paid for the full screen before any sound started.
 *
 * An Activity, not a BroadcastReceiver: a pinned shortcut must target an Activity, and starting the
 * media foreground service from a visible Activity start stays outside the Android 12+ background-start
 * restriction (strategic §3.2).
 *
 * Every branch is terminal and calls finish(); no content view is ever set, so nothing is drawn.
 */
@AndroidEntryPoint
class StreamPlayLaunchActivity : AppCompatActivity() {

    @Inject lateinit var routeManager: StreamShortcutRouteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra(EXTRA_STREAM_URL)
        if (url.isNullOrBlank()) {
            Timber.w("StreamPlayLaunchActivity: started without a stream url")
            finish()
            return
        }
        lifecycleScope.launch { route(url) }
    }

    /**
     * A URL the manager declines - unknown channel, background audio off, non-audio stream, no network -
     * is routed to the Streams screen rather than dropped, because that screen already owns both the
     * playback path and the "cannot play this" messaging.
     */
    private suspend fun route(url: String) {
        val source = routeManager.headlessSource(url)
        if (source == null) {
            startActivity(StreamsActivity.createPlayShortcutIntent(this, url))
            finish()
            return
        }
        StreamHeadlessPlayManager(this).play(source) { finish() }
    }

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"

        /**
         * The single factory for the screen-less stream entry. NEW_TASK because every caller (a pinned
         * shortcut, a launcher tile) starts this from outside an Activity task of ours.
         */
        fun createIntent(context: Context, url: String): Intent =
            Intent(context, StreamPlayLaunchActivity::class.java).apply {
                putExtra(EXTRA_STREAM_URL, url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}

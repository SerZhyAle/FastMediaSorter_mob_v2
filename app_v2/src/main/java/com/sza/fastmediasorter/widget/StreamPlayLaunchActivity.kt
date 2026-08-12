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

    @Inject lateinit var headlessPlay: StreamHeadlessPlayManager

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
     * playback path and the "cannot play this" messaging. So is a headless attempt that fails or never
     * starts: strategic §4 requires a message, not a trampoline that sits there invisibly.
     *
     * One exit for every arm. An earlier shape finished inside the service callback, which never ran when
     * the connection failed, leaving a transparent Activity resumed on top of the launcher: the user saw
     * their home screen and their taps went nowhere.
     */
    private suspend fun route(url: String) {
        val source = routeManager.headlessSource(url)
        Timber.d("S1471: shortcut trampoline routing, headless=%s", source != null)
        if (source == null || !headlessPlay.play(source)) {
            startActivity(StreamsActivity.createPlayShortcutIntent(this, url))
        }
        finish()
    }

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"

        /**
         * Own action rather than StreamsActivity's ACTION_PLAY_STREAM, so the two entry points stay
         * distinguishable - the migration tells a stale pin from a migrated one, and reusing the old
         * action would have made every already-migrated shortcut look stale again.
         */
        const val ACTION_PLAY_STREAM_HEADLESS = "com.sza.fastmediasorter.action.PLAY_STREAM_HEADLESS"

        /**
         * The single factory for the screen-less stream entry. NEW_TASK because every caller (a pinned
         * shortcut, a launcher tile) starts this from outside an Activity task of ours.
         *
         * The action is not optional and not decoration: a shortcut Intent without one makes
         * ShortcutInfo.Builder.build() throw `intent's action must be set`, which crashed the app the
         * moment a user tried to pin a station (found on device 2026-08-09 - the component being
         * explicit is what makes the action look redundant, and it is not).
         */
        fun createIntent(context: Context, url: String): Intent =
            Intent(context, StreamPlayLaunchActivity::class.java).apply {
                action = ACTION_PLAY_STREAM_HEADLESS
                putExtra(EXTRA_STREAM_URL, url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}

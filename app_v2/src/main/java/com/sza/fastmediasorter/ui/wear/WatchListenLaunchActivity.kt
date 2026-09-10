package com.sza.fastmediasorter.ui.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.service.WatchListenSessionManager
import com.sza.fastmediasorter.service.WearListenState
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S2881 - transparent, no-UI trampoline every "listen to the watch" surface targets.
 *
 * Pillar B: the call starts work rather than opening an interface. Sending the Data Layer command
 * needs no foreground service of its own - a visible Activity start is enough, the watch's answer
 * is received by [com.sza.fastmediasorter.service.PhoneWearListenerService], which the system wakes
 * on its own, and playback binds to the media service that already exists.
 *
 * Two actions, one rule: the plain action starts a session and no-ops while one runs (a second tap
 * on a menu item, a panel tile or a launcher shortcut never restarts), and the widget's toggle
 * action stops the active session instead - the widget's tap must end what it shows running.
 * Every branch is terminal and calls finish(); no content view is ever set, so nothing is drawn.
 */
@AndroidEntryPoint
class WatchListenLaunchActivity : AppCompatActivity() {

    @Inject lateinit var sessionManager: WatchListenSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val record = intent?.getBooleanExtra(EXTRA_RECORD, false) ?: false
        Timber.d("S2881: trampoline entered, action=%s record=%s", intent?.action, record)
        when (intent?.action) {
            ACTION_TOGGLE ->
                when (sessionManager.listenState.value) {
                    is WearListenState.Idle -> sessionManager.start(record = record)
                    else -> sessionManager.stop()
                }
            else -> sessionManager.start(record = record)
        }
        finish()
    }

    companion object {
        const val ACTION_LISTEN_TO_WATCH = "com.sza.fastmediasorter.action.LISTEN_TO_WATCH"

        /** S2881: the widget's action - stop the running session or start one, never a restart. */
        const val ACTION_TOGGLE = "com.sza.fastmediasorter.action.LISTEN_TOGGLE"

        const val EXTRA_RECORD = "extra_record"

        /**
         * The single factory for the screen-less listen entry. NEW_TASK because every caller (a pinned
         * shortcut, a launcher tile, a widget button) starts this from outside an Activity task of ours.
         *
         * The action is not optional: a shortcut Intent without one makes ShortcutInfo.Builder.build()
         * throw `intent's action must be set` (found on device for the stream entry, S1471).
         */
        fun createIntent(context: Context, record: Boolean): Intent =
            Intent(context, WatchListenLaunchActivity::class.java).apply {
                action = ACTION_LISTEN_TO_WATCH
                putExtra(EXTRA_RECORD, record)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}

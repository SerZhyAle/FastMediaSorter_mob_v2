package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity
import com.sza.fastmediasorter.ui.launcher.LauncherStartWindowActivity
import com.sza.fastmediasorter.util.resolveActivityCompat

/**
 * The All apps request can arrive through a gesture extra or a shortcut action.
 *
 * Both ends of that request live here rather than in the activity: the launcher capability seam builds
 * the intent, and the activity only asks whether the one it was handed carries the request.
 */
object LauncherOpenAllAppsRequest {

    private const val EXTRA_OPEN_ALL_APPS = "com.sza.fastmediasorter.launcher.OPEN_ALL_APPS"

    fun intent(context: Context): Intent {
        val homeIntent = Intent(context, LauncherHomeActivity::class.java)
        val hostIntent = if (context.packageManager.resolveActivityCompat(homeIntent) != null) {
            homeIntent
        } else {
            Intent(context, LauncherStartWindowActivity::class.java)
        }
        return hostIntent.putExtra(EXTRA_OPEN_ALL_APPS, true)
    }

    /**
     * True once per request: the home activity is `singleTask`, so the same intent is replayed on every
     * later restart, and a request left in place would reopen the panel long after the gesture that
     * asked for it.
     */
    fun consume(intent: Intent?): Boolean {
        if (intent == null) return false
        val requestedByAction = intent.action == LauncherModeContract.ACTION_OPEN_ALL_APPS
        val requestedByExtra = intent.getBooleanExtra(EXTRA_OPEN_ALL_APPS, false)
        val requested = requestedByAction || requestedByExtra
        if (requested) {
            if (requestedByAction) intent.action = null
            intent.removeExtra(EXTRA_OPEN_ALL_APPS)
        }
        return requested
    }
}

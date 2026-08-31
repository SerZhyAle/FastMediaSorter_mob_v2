package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity

/**
 * S2256: the "bring the home surface forward with All apps open" request, as an intent extra.
 *
 * Both ends of that request live here rather than in the activity: the launcher capability seam builds
 * the intent, and the activity only asks whether the one it was handed carries the request.
 */
object LauncherOpenAllAppsRequest {

    private const val EXTRA_OPEN_ALL_APPS = "com.sza.fastmediasorter.launcher.OPEN_ALL_APPS"

    fun intent(context: Context): Intent =
        Intent(context, LauncherHomeActivity::class.java).putExtra(EXTRA_OPEN_ALL_APPS, true)

    /**
     * True once per request: the home activity is `singleTask`, so the same intent is replayed on every
     * later restart, and a request left in place would reopen the panel long after the gesture that
     * asked for it.
     */
    fun consume(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ALL_APPS, false) != true) return false
        intent.removeExtra(EXTRA_OPEN_ALL_APPS)
        return true
    }
}

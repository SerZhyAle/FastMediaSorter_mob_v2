package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import timber.log.Timber

/**
 * S0920: standalone player hosts run under Theme.FastMediaSorter.FullScreen, whose
 * `android:windowFullscreen=true` (legacy FLAG_FULLSCREEN) hides the status bar for the whole activity
 * while leaving the navigation bar - exactly why the OS clock/battery vanish at the top yet the bottom
 * nav pill stays. In commands mode the OS status bar must stay visible (true fullscreen is entered on
 * demand via StandaloneFullscreenManager), so clear the flag and show the status bar. The hosts also
 * draw edge-to-edge over a permanently dark background without AGP's automatic contrast re-tint, so
 * force light (white) system-bar icons too.
 *
 * The shared FullScreen theme is deliberately left untouched: the in-app PlayerActivity uses it as well
 * and is out of this ticket's scope.
 */
object StandaloneSystemBars {

    @Suppress("DEPRECATION")
    fun showStatusBarWithLightIcons(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        Timber.d("S0920: standalone status bar shown + light system-bar icons")
    }
}

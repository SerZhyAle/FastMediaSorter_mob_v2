package com.sza.fastmediasorter.core.util

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import timber.log.Timber

/**
 * Wraps system Settings intents and supplies `ActivityOptions.setLaunchBounds(..)` so that the
 * system Settings activity opens in a window large enough to display its content on freeform /
 * Android XR / foldable layouts. On phones in full-screen mode the system ignores the supplied
 * bounds and behaviour is unchanged.
 */
object SettingsIntentLauncher {

    /**
     * Compute a centred rectangle covering 80 percent of the display width and 85 percent of the
     * display height. Returned `Rect` is in absolute display coordinates suitable for
     * `ActivityOptions.setLaunchBounds(..)`.
     *
     * Returns `null` if the display metrics cannot be read - in that case the system picks the
     * default size for the launched activity.
     */
    private fun computeCenteredLaunchBounds(activity: Activity): Rect? {
        return try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.windowManager.currentWindowMetrics.bounds
            } else {
                @Suppress("DEPRECATION")
                Rect().also { activity.windowManager.defaultDisplay.getRectSize(it) }
            }
            val w = (display.width() * 0.80).toInt()
            val h = (display.height() * 0.85).toInt()
            val left = display.left + (display.width() - w) / 2
            val top = display.top + (display.height() - h) / 2
            Rect(left, top, left + w, top + h)
        } catch (e: Exception) {
            Timber.w(e, "SettingsIntentLauncher: failed to compute launch bounds")
            null
        }
    }

    /**
     * Launch a system Settings intent with `ActivityOptions.setLaunchBounds(..)` so the dialog
     * opens in a window large enough to show its content on freeform / Android XR / foldable
     * layouts. On API < 24 the bounds API is unavailable and the intent is launched directly.
     *
     * The new-task flag is required for `setLaunchBounds` to take effect and is added
     * unconditionally.
     *
     * S1992: this launcher never returns an activity result and must not be asked for one. A new
     * task cannot carry a result back - the platform drops it at launch time and says so
     * ("Activity is launching as a new task, so cancelling activity result"), which delivered
     * `RESULT_CANCELED` before the Settings screen had even drawn. Read the outcome when the host
     * resumes instead: the user grants on a screen that runs while this app is stopped, so a resume
     * is the only moment the app can observe the new state.
     */
    fun launch(activity: Activity, intent: Intent) {
        Timber.i("SettingsIntentLauncher: launch action=${intent.action}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val opts = computeCenteredLaunchBounds(activity)?.let {
                ActivityOptions.makeBasic().setLaunchBounds(it).toBundle()
            }
            activity.startActivity(intent, opts)
        } else {
            activity.startActivity(intent)
        }
    }
}

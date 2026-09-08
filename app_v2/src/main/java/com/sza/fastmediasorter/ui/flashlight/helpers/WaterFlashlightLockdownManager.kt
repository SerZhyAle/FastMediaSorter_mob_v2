package com.sza.fastmediasorter.ui.flashlight.helpers

import android.app.Activity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import timber.log.Timber
import javax.inject.Inject

/**
 * S2718: keeps the system's own surfaces out of the water flashlight.
 *
 * The screen already swallows every touch of its own, but that left the navigation bar in place and
 * the notification shade one swipe away - so a drop of water on the glass ended the program in
 * exactly the rain-and-shower scenario it exists for.
 *
 * Two independent measures, weakest first, because the strong one may be refused:
 *   - the system bars are hidden and set to reappear only transiently, which no permission gates;
 *   - the task is pinned (lock task mode), which is what actually blocks the shade and the
 *     navigation buttons. Without device-owner rights the platform asks the user to confirm the
 *     first time, and may refuse outright - a refusal leaves the hidden bars in place rather than
 *     taking the light down with it.
 */
class WaterFlashlightLockdownManager @Inject constructor() {

    /** Call from `onResume`: [Activity.startLockTask] throws unless the activity is resumed. */
    fun engage(activity: Activity) {
        hideSystemBars(activity)
        runCatching { activity.startLockTask() }
            .onFailure { error ->
                // The light stays on and the bars stay hidden; only the shade block is lost, so this
                // degrades the screen rather than ending it.
                Timber.w(error, "Water flashlight could not pin the screen; system bars stay hidden")
            }
    }

    /** Call from `onPause`, so the pin never outlives the screen that asked for it. */
    fun release(activity: Activity) {
        runCatching { activity.stopLockTask() }
            .onFailure { error ->
                Timber.w(error, "Water flashlight could not leave the pinned state")
            }
    }

    private fun hideSystemBars(activity: Activity) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

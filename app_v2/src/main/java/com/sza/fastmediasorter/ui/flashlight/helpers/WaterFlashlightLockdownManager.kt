package com.sza.fastmediasorter.ui.flashlight.helpers

import android.app.Activity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import javax.inject.Inject

/**
 * S2718: keeps the system's own surfaces out of the water flashlight.
 *
 * The screen already swallows every touch of its own, but that left the navigation bar in place and
 * the notification shade one swipe away - so a drop of water on the glass ended the program in
 * exactly the rain-and-shower scenario it exists for.
 *
 * System bars are hidden and set to reappear only transiently. This needs no permission and keeps
 * a wet touch from opening a system surface, while a deliberate swipe remains available.
 */
class WaterFlashlightLockdownManager @Inject constructor() {

    /** Call after the activity resumes, when its window is ready to receive inset changes. */
    fun engage(activity: Activity) {
        hideSystemBars(activity)
    }

    private fun hideSystemBars(activity: Activity) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

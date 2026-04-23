package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import timber.log.Timber

/**
 * Toggles immersive fullscreen mode for StandalonePlayerActivity.
 *   Uses modern WindowInsetsControllerCompat to avoid deprecated edge-to-edge APIs.
 */
class StandaloneFullscreenManager(private val activity: Activity) {

    fun enterFullscreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            Timber.d("StandaloneFullscreenManager: entered fullscreen")
        } catch (e: Exception) {
            Timber.w(e, "StandaloneFullscreenManager: enterFullscreen failed")
        }
    }

    fun exitFullscreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            Timber.d("StandaloneFullscreenManager: exited fullscreen")
        } catch (e: Exception) {
            Timber.w(e, "StandaloneFullscreenManager: exitFullscreen failed")
        }
    }
}

package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import timber.log.Timber

/**
 * Toggles immersive fullscreen mode for StandalonePlayerActivity.
 *   API 30+ : WindowInsetsController.hide/show(systemBars)
 *   API 26–29: decorView.systemUiVisibility flags (deprecated but functional)
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
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
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
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility =
                    android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
            Timber.d("StandaloneFullscreenManager: exited fullscreen")
        } catch (e: Exception) {
            Timber.w(e, "StandaloneFullscreenManager: exitFullscreen failed")
        }
    }
}

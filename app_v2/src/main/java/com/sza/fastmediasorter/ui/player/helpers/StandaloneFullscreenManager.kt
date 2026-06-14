package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import timber.log.Timber

/**
 * Toggles immersive fullscreen mode for StandalonePlayerActivity.
 *   Uses modern WindowInsetsControllerCompat to avoid deprecated edge-to-edge APIs.
 */
class StandaloneFullscreenManager(private val activity: Activity) {

    fun toggleFullscreen() {
        val decorView = activity.window.decorView
        val insets = WindowInsetsCompat.toWindowInsetsCompat(decorView.rootWindowInsets, decorView)
        if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
            enterFullscreen()
        } else {
            exitFullscreen()
        }
    }

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

    // Panel-aware variants: hide/show both system bars and the command panel together.
    // Panel visibility is the source of truth for whether the user has entered fullscreen.

    fun enterFullscreenWithPanel(commandPanel: View, onStateChanged: (isActive: Boolean) -> Unit) {
        enterFullscreen()
        commandPanel.isVisible = false
        onStateChanged(true)
    }

    fun exitFullscreenWithPanel(commandPanel: View, onStateChanged: (isActive: Boolean) -> Unit) {
        exitFullscreen()
        commandPanel.isVisible = true
        onStateChanged(false)
    }

    fun toggleFullscreenWithPanel(commandPanel: View, onStateChanged: (isActive: Boolean) -> Unit) {
        if (commandPanel.isVisible) {
            enterFullscreenWithPanel(commandPanel, onStateChanged)
        } else {
            exitFullscreenWithPanel(commandPanel, onStateChanged)
        }
    }

    /**
     * Wires a persistent listener so that when transient system bars appear (edge-swipe in
     * immersive mode) and the command panel is hidden, fullscreen is exited and the panel
     * is restored. Uses the deprecated systemUiVisibility listener because
     * WindowInsetsControllerCompat provides no equivalent callback on API < 30, and
     * the deprecated API still fires correctly on API 30+ via Android's backward-compat layer.
     *
     * Call once from Activity.setupViews(). Guard inside onBarsAppeared prevents
     * re-entry when bars are shown programmatically (i.e. panel already visible).
     */
    @Suppress("DEPRECATION")
    fun setupTransientBarsExitCallback(decorView: View, onBarsAppeared: () -> Unit) {
        decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                onBarsAppeared()
            }
        }
    }
}

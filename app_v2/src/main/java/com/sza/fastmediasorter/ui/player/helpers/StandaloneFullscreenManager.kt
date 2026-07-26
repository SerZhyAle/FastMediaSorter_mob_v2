package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.ViewCompat
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

    // Tracks whether this manager put the window into immersive mode, so the transient-bars callback
    // can tell "the user swiped the bars back" from any other inset dispatch.
    private var fullscreenActive = false

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
            fullscreenActive = true
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
            fullscreenActive = false
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
     * Wires a listener so that when transient system bars appear (edge-swipe in immersive mode)
     * while the command panel is hidden, fullscreen is exited and the panel is restored.
     *
     * S1115: this asks the real window insets, not the legacy systemUiVisibility flags. The API 30+
     * hide path goes through WindowInsetsController, which never sets `SYSTEM_UI_FLAG_FULLSCREEN`,
     * so a flag-based check reads as "bars visible" unconditionally and fired the instant fullscreen
     * was entered - the standalone video host could therefore never stay in panel-hidden fullscreen.
     *
     * The listener sits on the content view rather than the decor view, so it never displaces the
     * decor's own inset handling, and it returns the insets unconsumed so child listeners still run.
     */
    fun setupTransientBarsExitCallback(onBarsAppeared: () -> Unit) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            if (fullscreenActive && insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                onBarsAppeared()
            }
            insets
        }
    }
}

package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
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

    // Uptime of the last enterFullscreen() call, and whether the system-bars animation currently
    // running was started by that call rather than by the user. See setupTransientBarsExitCallback.
    private var fullscreenEnteredAtMs = 0L
    private var selfInitiatedBarsAnimation = false

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
            fullscreenEnteredAtMs = SystemClock.uptimeMillis()
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
     * Wires the signals that mean "the system bars came back" while the command panel is hidden, so
     * fullscreen can be left and the panel restored.
     *
     * S1115: a flag-based check on `SYSTEM_UI_FLAG_FULLSCREEN` is wrong on API 30+, because the hide
     * path goes through WindowInsetsController and never sets that flag, so the check read as "bars
     * visible" unconditionally and fired the instant fullscreen was entered.
     *
     * S1202, measured on a real device (Galaxy S21, API 35): the insets listener alone is not enough
     * either. Under BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE the swipe-revealed bars are drawn over the
     * content without affecting layout, and the platform reports them to the app as *not* visible -
     * `isVisible(systemBars())` stayed false and no inset dispatch arrived at all while the bars were
     * on screen. What does arrive is a system-bars inset *animation*, so that is the real signal.
     *
     * The animation alone would misfire, because this manager's own hide() animates the same types.
     * The two are told apart by when the animation begins: the self-initiated one starts 19 ms after
     * enterFullscreen(), a user swipe arbitrarily later, so anything inside the guard window is ours.
     *
     * The insets listener is kept as the second trigger: it does not see transient bars, but it does
     * see the bars becoming genuinely visible (for example when the window stops being fullscreen),
     * which the animation guard would otherwise ignore. Both callers are idempotent, so a double
     * trigger is harmless.
     *
     * The listener sits on the content view rather than the decor view, so it never displaces the
     * decor's own inset handling, and it returns the insets unconsumed so child listeners still run.
     *
     * S1652: below API 30 neither signal exists, and no version branch can supply one. AndroidX maps
     * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE onto SYSTEM_UI_FLAG_IMMERSIVE_STICKY there
     * (WindowInsetsControllerCompat.Impl20.setSystemBarsBehavior), and under sticky immersive the
     * platform shows the swipe-revealed bars without clearing a single flag and without calling the
     * system-UI visibility callback - so setOnSystemUiVisibilityChangeListener, the obvious candidate
     * for the flag world, never fires. The animation callback cannot fire either: on API 21..29 it is
     * emulated by a proxy OnApplyWindowInsetsListener (WindowInsetsAnimationCompat.Impl21), and sticky
     * transient bars dispatch no insets to diff. The exit button from S1115 is version-independent and
     * stays the only way out below API 30. Do not add a branch here expecting one of these to work.
     */
    fun setupTransientBarsExitCallback(onBarsAppeared: () -> Unit) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            if (fullscreenActive && insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                onBarsAppeared()
            }
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            content,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (!animation.affectsSystemBars()) return
                    selfInitiatedBarsAnimation =
                        SystemClock.uptimeMillis() - fullscreenEnteredAtMs < SELF_HIDE_GUARD_MS
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat = insets

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (!animation.affectsSystemBars()) return
                    if (fullscreenActive && !selfInitiatedBarsAnimation) {
                        onBarsAppeared()
                    }
                }
            }
        )
    }

    private fun WindowInsetsAnimationCompat.affectsSystemBars(): Boolean =
        typeMask and WindowInsetsCompat.Type.systemBars() != 0

    private companion object {
        // Generous margin over the 19 ms measured between enterFullscreen() and its own hide
        // animation; a user cannot meaningfully swipe the bars back inside this window anyway.
        const val SELF_HIDE_GUARD_MS = 500L
    }
}

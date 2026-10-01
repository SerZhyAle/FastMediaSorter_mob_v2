package com.sza.fastmediasorter.ui.welcome.helpers

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * S1377: reacts to a real orientation flip on the Welcome screen.
 *
 * The Welcome Activity declares `configChanges="orientation|screenSize|keyboardHidden"` on purpose -
 * the functionality page runs inline engine downloads and the permissions page runs a stepwise grant
 * flow, both of which a recreation would disrupt. The cost is that nothing re-resolves the page
 * layouts or the width-qualified values (the feature-grid column count) on a rotation in place, so
 * the portrait resolution stayed in view until the app was relaunched.
 *
 * This callback is the missing reaction. The host registers it with `registerComponentCallbacks` and
 * drops it on its destroy edge; keeping the rebuild body here rather than in an Activity override is
 * what keeps the screen free of the logic (CLAUDE.md Rule 3) and off detekt's 40-function ceiling,
 * which this Activity already sits on.
 *
 * It also owns the system-bar visibility: on a phone in landscape the status bar and the navigation
 * bar took about a third of a ~400dp height and left the pages a clipped strip, so the bars are hidden
 * there (a swipe brings them back) and shown again in every other configuration.
 */
class WelcomeRotationManager(
    private val window: Window,
    initialConfiguration: Configuration,
    private val currentPageProvider: () -> Int,
    private val onOrientationChanged: (page: Int) -> Unit,
) : ComponentCallbacks {

    private var lastOrientation = initialConfiguration.orientation

    init {
        applySystemBars(initialConfiguration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (newConfig.orientation == lastOrientation) return
        lastOrientation = newConfig.orientation
        applySystemBars(newConfig)
        val page = currentPageProvider()
        onOrientationChanged(page)
    }

    // Required by the interface and deprecated by the platform; this callback holds nothing to release.
    @Deprecated("ComponentCallbacks.onLowMemory is deprecated on the platform side.")
    override fun onLowMemory() = Unit

    private fun applySystemBars(config: Configuration) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val compactLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            config.screenHeightDp in 1 until COMPACT_HEIGHT_DP
        if (compactLandscape) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        const val COMPACT_HEIGHT_DP = 480
    }
}

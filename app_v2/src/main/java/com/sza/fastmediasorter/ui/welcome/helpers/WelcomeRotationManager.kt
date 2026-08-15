package com.sza.fastmediasorter.ui.welcome.helpers

import android.content.ComponentCallbacks
import android.content.res.Configuration
import timber.log.Timber

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
 */
class WelcomeRotationManager(
    initialOrientation: Int,
    private val currentPageProvider: () -> Int,
    private val onOrientationChanged: (page: Int) -> Unit,
) : ComponentCallbacks {

    private var lastOrientation = initialOrientation

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (newConfig.orientation == lastOrientation) return
        lastOrientation = newConfig.orientation
        val page = currentPageProvider()
        onOrientationChanged(page)
    }

    // Required by the interface and deprecated by the platform; this callback holds nothing to release.
    @Deprecated("ComponentCallbacks.onLowMemory is deprecated on the platform side.")
    override fun onLowMemory() = Unit
}

package com.sza.fastmediasorter.screencapture

import android.app.Activity
import android.content.Intent
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import javax.inject.Inject

/**
 * Menu-triggered screenshot path (S0559): starts the MediaProjection consent activity, which owns
 * the system consent dialog and the post-consent capture service. No gesture direction is passed -
 * the capture service treats an absent direction as a plain save-to-destination, matching the
 * single "take screenshot now" menu intent.
 */
class MenuScreenshotLauncherImpl @Inject constructor() : MenuScreenshotLauncher {

    override fun launch(activity: Activity, action: ScreenshotGestureAction) {
        activity.startActivity(
            Intent(activity, ScreenCaptureConsentActivity::class.java)
                .putExtra(ScreenCaptureConsentActivity.EXTRA_ACTION, action.name),
        )
    }
}

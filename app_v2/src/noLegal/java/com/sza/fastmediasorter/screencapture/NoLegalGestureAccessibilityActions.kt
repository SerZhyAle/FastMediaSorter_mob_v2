package com.sza.fastmediasorter.screencapture

import com.sza.fastmediasorter.core.screencapture.gesture.GestureAccessibilityActions
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import timber.log.Timber
import javax.inject.Inject

/**
 * S1038: noLegal implementation of the SYSTEM-group gesture seam. Routes to the live
 * [ScreenshotAccessibilityService] via its holder; when the service is not currently enabled the action
 * degrades to a logged no-op (the picker still offers the group because the capability is compiled, but
 * the user has to turn the accessibility service on for it to act).
 */
class NoLegalGestureAccessibilityActions @Inject constructor() : GestureAccessibilityActions {

    override fun perform(action: ScreenshotGestureAction): Boolean {
        val service = ScreenshotAccessibilityServiceHolder.instance
        if (service == null) {
            Timber.w("NoLegalGestureAccessibilityActions: accessibility service off, %s ignored", action)
            return false
        }
        return service.performSystemAction(action)
    }
}

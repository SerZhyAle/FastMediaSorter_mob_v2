package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareTargetOutcome
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.core.share.asLaunchOutcome
import com.sza.fastmediasorter.util.GoogleKeepAvailabilityChecker
import javax.inject.Inject

/**
 * Google Keep (text) receiver (S0459): send the current note text to a resolved Keep client. Reuses
 * the existing text-send path and Keep package resolution; returns false when no text or no Keep.
 */
class KeepTextShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override suspend fun send(activity: Activity, content: ShareableContent): ShareTargetOutcome {
        val text = content.text
        // Resolved through the text check so an absent note still costs no package lookup.
        val keepPackage = text?.let { GoogleKeepAvailabilityChecker(activity).resolveTargetPackage() }
        return if (text == null || keepPackage == null) {
            ShareTargetOutcome.Failed()
        } else {
            SystemShareInvoker.invoke(
                context = activity,
                payload = SharePayload.Text(text),
                preferredPackage = keepPackage,
            ).asLaunchOutcome()
        }
    }

    companion object {
        const val ID = "keep_text"
    }
}

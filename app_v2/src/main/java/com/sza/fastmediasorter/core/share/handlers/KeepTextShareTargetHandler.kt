package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.util.GoogleKeepAvailabilityChecker
import javax.inject.Inject

/**
 * Google Keep (text) receiver (S0459): send the current note text to a resolved Keep client. Reuses
 * the existing text-send path and Keep package resolution; returns false when no text or no Keep.
 */
class KeepTextShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override fun send(activity: Activity, content: ShareableContent): Boolean {
        val text = content.text ?: return false
        val keepPackage = GoogleKeepAvailabilityChecker(activity).resolveTargetPackage() ?: return false
        return SystemShareInvoker.invoke(
            context = activity,
            payload = SharePayload.Text(text),
            preferredPackage = keepPackage,
        )
    }

    companion object {
        const val ID = "keep_text"
    }
}

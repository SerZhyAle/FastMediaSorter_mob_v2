package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareTargetOutcome
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.core.share.asLaunchOutcome
import javax.inject.Inject

/**
 * System Share receiver (S0459): the universal catch-all. Hands the file(s) to the Android chooser
 * via the existing invoker - reused as-is, no package targeting (S0459 goal 9).
 */
class SystemShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override suspend fun send(activity: Activity, content: ShareableContent): ShareTargetOutcome {
        // S0631: a text-only payload (e.g. a stream link) has no Uri - share it as plain text via the
        // chooser (EXTRA_TEXT) so messengers / clipboard / SMS / email all receive the link.
        if (content.isTextOnly) {
            return SystemShareInvoker.invoke(
                context = activity,
                payload = SharePayload.Text(content.text!!),
                chooserTitle = content.displayName,
            ).asLaunchOutcome()
        }
        return SystemShareInvoker.invokeFiles(
            context = activity,
            uris = content.uris,
            mime = content.mime,
            chooserTitle = content.displayName,
        ).asLaunchOutcome()
    }

    companion object {
        const val ID = "system_share"
    }
}

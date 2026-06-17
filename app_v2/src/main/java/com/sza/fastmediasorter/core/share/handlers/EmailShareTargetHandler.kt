package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import javax.inject.Inject

/**
 * Email receiver for the unified «Send to..» menu (S0459). Attaches the file(s) via ACTION_SEND
 * with MIME message/rfc822 to bias the chooser toward mail clients, leaving the recipient empty for
 * the user to fill. Never uses mailto: (ACTION_SENDTO drops attachments) - research 05.
 */
class EmailShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override fun send(activity: Activity, content: ShareableContent): Boolean =
        SystemShareInvoker.invokeFiles(
            context = activity,
            uris = content.uris,
            mime = MIME_EMAIL,
            chooserTitle = content.displayName,
            emailAddresses = emptyArray(),
            subject = content.displayName,
        )

    companion object {
        const val ID = "email"
        private const val MIME_EMAIL = "message/rfc822"
    }
}

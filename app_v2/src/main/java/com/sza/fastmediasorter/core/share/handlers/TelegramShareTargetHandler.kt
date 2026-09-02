package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareTargetOutcome
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.core.share.TelegramShareTargets
import com.sza.fastmediasorter.core.share.asLaunchOutcome
import javax.inject.Inject

/**
 * Telegram receiver (S0459): target an installed Telegram client via the existing invoker, falling
 * back to the system chooser when none is installed.
 */
class TelegramShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override suspend fun send(activity: Activity, content: ShareableContent): ShareTargetOutcome =
        SystemShareInvoker.invokeFiles(
            context = activity,
            uris = content.uris,
            mime = content.mime,
            preferredPackage = TelegramShareTargets.firstInstalledPackage(activity.packageManager),
            chooserTitle = content.displayName,
        ).asLaunchOutcome()

    companion object {
        const val ID = "telegram"
    }
}

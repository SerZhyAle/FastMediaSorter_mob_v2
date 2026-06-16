package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import android.content.pm.PackageManager
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import javax.inject.Inject

/**
 * Instagram receiver for the unified «Send to..» menu (S0459). Targets the Instagram app via
 * ACTION_SEND + setPackage (image/video only - enforced by applicableTypes at registration), letting
 * Instagram drive its own share flow; falls back to the chooser when absent - research 04.
 */
class InstagramShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override fun send(activity: Activity, content: ShareableContent): Boolean =
        SystemShareInvoker.invokeFiles(
            context = activity,
            uris = content.uris,
            mime = content.mime,
            preferredPackage = if (isInstalled(activity)) PACKAGE else null,
            chooserTitle = content.displayName,
        )

    private fun isInstalled(activity: Activity): Boolean = try {
        activity.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        // Expected when Instagram is absent; the caller falls back to the chooser.
        false
    }

    companion object {
        const val ID = "instagram"
        private const val PACKAGE = "com.instagram.android"
    }
}

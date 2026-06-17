package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import android.content.pm.PackageManager
import com.sza.fastmediasorter.util.getPackageInfoCompat
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import javax.inject.Inject

/**
 * WhatsApp receiver for the unified «Send to..» menu (S0459). Targets an installed WhatsApp client
 * via ACTION_SEND + setPackage and lets WhatsApp choose the recipient; falls back to the system
 * chooser when none is installed. No programmatic recipient selection (the undocumented
 * direct-recipient extra is deliberately avoided) - research 04.
 */
class WhatsAppShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override fun send(activity: Activity, content: ShareableContent): Boolean {
        val installed = PACKAGES.firstOrNull { isInstalled(activity, it) }
        return SystemShareInvoker.invokeFiles(
            context = activity,
            uris = content.uris,
            mime = content.mime,
            preferredPackage = installed,
            chooserTitle = content.displayName,
        )
    }

    private fun isInstalled(activity: Activity, pkg: String): Boolean = try {
        activity.packageManager.getPackageInfoCompat(pkg)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        // Expected when this WhatsApp variant is absent; the caller falls back to the chooser.
        false
    }

    companion object {
        const val ID = "whatsapp"
        // Standard client first, then WhatsApp Business; the first installed one wins.
        val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    }
}

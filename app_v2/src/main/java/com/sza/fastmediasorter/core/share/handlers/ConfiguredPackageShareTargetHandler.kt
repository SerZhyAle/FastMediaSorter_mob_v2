package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import android.content.pm.PackageManager
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.util.getPackageInfoCompat

class ConfiguredPackageShareTargetHandler(
    override val targetId: String,
    private val packages: List<String>,
) : ShareTargetHandler {

    override fun send(activity: Activity, content: ShareableContent): Boolean =
        SystemShareInvoker.invokeFiles(
            context = activity,
            uris = content.uris,
            mime = content.mime,
            preferredPackage = firstInstalledPackage(activity),
            chooserTitle = content.displayName,
        )

    private fun firstInstalledPackage(activity: Activity): String? = packages.firstOrNull { pkg ->
        try {
            activity.packageManager.getPackageInfoCompat(pkg)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

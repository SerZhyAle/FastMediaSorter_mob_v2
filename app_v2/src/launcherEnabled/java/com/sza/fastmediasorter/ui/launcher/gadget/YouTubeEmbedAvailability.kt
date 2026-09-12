package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.webkit.WebView
import com.sza.fastmediasorter.util.getPackageInfoCompat
import timber.log.Timber

/**
 * S2032: whether this device can host the service's embedded player at all.
 *
 * Strategic §3.2: the embedded view's capability follows the version of the device's system WebView
 * component, not the build's API level, so an unusable embed is a normal state rather than a failure -
 * strategic §5 Столп 4 degrades the cell to a channel shortcut instead of showing an error.
 *
 * The decision is split from the device read so a unit test asserts the shipped rule rather than a copy
 * of it, which is what strategic §7's last risk row asks for.
 */
internal object YouTubeEmbedAvailability {

    /**
     * A device property, never a build one: the app's own minSdk says nothing about which WebView the
     * user has. This floor is the oldest major version whose media pipeline the service's embed still
     * targets; below it the embed loads a page that never plays.
     */
    private const val MIN_MAJOR_VERSION = 80

    /** No WebView package, an unreadable version and a too-old one all mean "use the shortcut". */
    fun isEmbedUsable(versionName: String?): Boolean {
        val major = versionName?.trim()?.substringBefore('.')?.toIntOrNull() ?: return false
        return major >= MIN_MAJOR_VERSION
    }

    fun isEmbedUsable(context: Context): Boolean = isEmbedUsable(webViewVersionName(context))

    private fun webViewVersionName(context: Context): String? {
        val packageName = WebView.getCurrentWebViewPackage()?.packageName ?: return null
        return runCatching { context.packageManager.getPackageInfoCompat(packageName).versionName }
            .onFailure { Timber.i(it, "WebView package %s has no readable version", packageName) }
            .getOrNull()
    }
}

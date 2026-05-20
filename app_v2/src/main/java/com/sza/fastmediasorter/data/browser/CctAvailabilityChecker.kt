package com.sza.fastmediasorter.data.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Resolves whether the device has a Chrome Custom Tabs-capable browser installed (strategic S0200).
 *
 * Used by [GoogleDomainBrowserLauncher] before launching an OAuth flow and by the Settings
 * Google Account card to show the diagnostics row. Returns ANY CCT-capable browser
 * (`ignoreDefault = true`) - the user-default browser only matters for the OS picker, not for
 * unlocking our feature.
 */
@Singleton
class CctAvailabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns the package name of a CCT-capable browser, or null when none is installed.
     */
    fun resolveCctPackage(): String? {
        val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/"))
        val pm = context.packageManager
        val browsers = pm.queryIntentActivities(probeIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
        val pkg = CustomTabsClient.getPackageName(context, browsers, /* ignoreDefault = */ true)
        if (pkg == null) {
            Timber.w("CctAvailabilityChecker: no Chrome Custom Tabs-capable browser found (queried=${browsers.size})")
        }
        return pkg
    }

    fun isAvailable(): Boolean = resolveCctPackage() != null
}

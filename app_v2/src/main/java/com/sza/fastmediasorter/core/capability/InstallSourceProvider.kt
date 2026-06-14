package com.sza.fastmediasorter.core.capability

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells whether the running app was installed through Google Play.
 *
 * The same `standard`/`legacy` binary can be Play-acquired or sideloaded/debug-installed; they share
 * applicationId and BuildConfig, so the only runtime signal is the installer origin. Used by:
 * - S0401 to gate `.so` delivery (Play installs must not fetch executable code from GitHub).
 * - S0400 to hide the translation toggle on dynamic-feature flavors when not Play-acquired.
 *
 * On API 30+ the reliable `getInstallSourceInfo` is used; below 30 the deprecated
 * `getInstallerPackageName` is the only option (spoofable, may be null) - callers on legacy treat a
 * null/unknown installer as non-Play.
 */
@Singleton
class InstallSourceProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun isPlayInstall(): Boolean = installingPackageName() in PLAY_STORE_INSTALLERS

    private fun installingPackageName(): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    private companion object {
        // com.android.vending = Play Store app; com.google.android.feedback = legacy Play installer id.
        val PLAY_STORE_INSTALLERS = setOf("com.android.vending", "com.google.android.feedback")
    }
}

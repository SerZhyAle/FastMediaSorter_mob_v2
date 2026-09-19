package com.sza.fastmediasorter.wear.data.onboarding

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.sza.fastmediasorter.wear.util.getPackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the installed package says about itself: the permissions its merged manifest declares, and
 * whether it has ever been updated.
 *
 * The manifest is the only honest answer to "which permissions can this edition ask for" without a
 * flavor check in shared code (S3186 ADR-1, same reasoning as S2458 ADR-3). Both values are constant
 * for the life of the process, so each is read once.
 */
@Singleton
class WearInstallInfoReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val packageInfo: PackageInfo? by lazy {
        try {
            context.packageManager.getPackageInfoCompat(context.packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w(e, "Own package not found - treating the install as already onboarded")
            null
        }
    }

    val declaredPermissions: Set<String> by lazy {
        packageInfo?.requestedPermissions?.toSet().orEmpty()
    }

    /**
     * True when the package was never updated since it was installed. An update over an existing
     * install reads false, because that user already used the app without the welcome (strategic §6.3).
     */
    val isFreshInstall: Boolean by lazy {
        packageInfo?.let { it.firstInstallTime == it.lastUpdateTime } ?: false
    }
}

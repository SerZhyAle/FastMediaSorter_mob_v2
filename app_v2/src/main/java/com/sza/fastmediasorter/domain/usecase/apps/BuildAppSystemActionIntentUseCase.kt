package com.sza.fastmediasorter.domain.usecase.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.sza.fastmediasorter.util.getApplicationInfoCompat
import com.sza.fastmediasorter.util.resolveActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1401: the two system screens the app-action menu can hand a package to - its details page and the
 * uninstall confirmation.
 *
 * Every accessor answers null when nothing on this device can handle the intent, because manufacturer
 * builds differ on both (strategic 7) and a menu entry that does nothing is worse than an absent one.
 */
class BuildAppSystemActionIntentUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun appInfoIntent(packageName: String): Intent? = resolvable(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(packageName))
    )

    fun uninstallIntent(packageName: String): Intent? {
        if (!canUninstall(packageName)) return null
        return resolvable(Intent(Intent.ACTION_DELETE, packageUri(packageName)))
    }

    /**
     * A system app has no uninstall flow the user can complete, so the entry is dropped rather than
     * offered and then refused by the platform.
     */
    fun canUninstall(packageName: String): Boolean {
        val info = applicationInfoOrNull(packageName) ?: return false
        return info.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    private fun resolvable(intent: Intent): Intent? =
        intent.takeIf { context.packageManager.resolveActivityCompat(it) != null }

    private fun applicationInfoOrNull(packageName: String): ApplicationInfo? = try {
        context.packageManager.getApplicationInfoCompat(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        // The list can name an app that was removed a moment ago; that is a stale row, not a failure.
        Timber.i(e, "Package %s is gone, offering no system action for it", packageName)
        null
    }

    private fun packageUri(packageName: String): Uri = Uri.fromParts(PACKAGE_SCHEME, packageName, null)

    private companion object {
        const val PACKAGE_SCHEME = "package"
    }
}

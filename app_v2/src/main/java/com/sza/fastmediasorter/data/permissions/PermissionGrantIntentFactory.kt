package com.sza.fastmediasorter.data.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.sza.fastmediasorter.domain.model.PermissionEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1436: the single owner of "which system screen grants this permission". The onboarding page and
 * the settings screen used to hold two hand-maintained copies of this mapping, so a permission added
 * to one could be missed by the other; both now ask here.
 *
 * The caller still owns the launch, because `ActivityNotFoundException` is thrown at launch time and
 * only the caller has the launcher - [appDetailsIntent] is the fallback to use when that happens.
 */
@Singleton
class PermissionGrantIntentFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * The system screen that grants [entry], or the app details page when this SDK level has no
     * dedicated screen for it. Never null: a row whose action opens nothing would be a dead control.
     */
    fun grantIntent(entry: PermissionEntry): Intent {
        val dedicated = when (entry.manifestName) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // No try/catch here: building an Intent cannot fail. The route that can fail is the
                    // launch, and the caller already catches ActivityNotFoundException around it.
                    packageScopedIntent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                } else {
                    null
                }
            Manifest.permission.MANAGE_MEDIA ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    packageScopedIntent(Settings.ACTION_REQUEST_MANAGE_MEDIA)
                } else {
                    null
                }
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ->
                packageScopedIntent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            // S0429's registry row. Not package-scoped: the notification-access screen is a global list
            // the user picks this app out of, and handing it a package: uri makes some OEM builds refuse.
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE ->
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            else -> null
        }
        val target = dedicated ?: appDetailsIntent()
        Timber.d("PermissionGrantIntent: %s -> %s", entry.manifestName, target.action)
        return target
    }

    /** The app details page - the route of last resort when a dedicated screen refuses to open. */
    fun appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }

    private fun packageScopedIntent(action: String): Intent = Intent(action).apply {
        data = Uri.parse("package:${context.packageName}")
    }
}

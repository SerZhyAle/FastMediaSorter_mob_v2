package com.sza.fastmediasorter.ui.common

import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.getPackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Shared coordinator for checking and displaying one-time application update notification
 * in the main window or launcher host (S2270).
 */
object AppUpdateNoticeManager {
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version_name"

    @Suppress("TooGenericExceptionCaught")
    @Synchronized
    fun checkForUpdate(activity: ComponentActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val packageInfo = activity.packageManager.getPackageInfoCompat(activity.packageName)
                val versionName = packageInfo.versionName

                val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastSeenVersionName = prefs.getString(KEY_LAST_SEEN_VERSION, null)

                var shouldShowNotice = false
                if (lastSeenVersionName != null && lastSeenVersionName != versionName) {
                    shouldShowNotice = true
                }

                if (lastSeenVersionName != versionName) {
                    prefs.edit().putString(KEY_LAST_SEEN_VERSION, versionName).apply()
                }

                if (shouldShowNotice) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.app_updated_to, versionName),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    Timber.d("S2270: update notice delivered to host (%s)", activity.javaClass.simpleName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get app version in AppUpdateNoticeManager")
            }
        }
    }
}

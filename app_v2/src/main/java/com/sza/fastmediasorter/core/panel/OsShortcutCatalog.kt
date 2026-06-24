package com.sza.fastmediasorter.core.panel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.resolveActivityCompat

/**
 * Curated catalog of common OS system targets offered in the app-launch panel (strategic S0663 §6.3:
 * the fixed set of nine). [available] returns only the targets whose intent actually resolves on the
 * device, so a tile never leads nowhere. Resolution touches the PackageManager - call [available]
 * off the main thread.
 */
object OsShortcutCatalog {

    data class Target(
        val key: String,
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
        val intent: (Context) -> Intent,
    )

    const val KEY_SETTINGS = "settings"
    const val KEY_WIFI = "wifi"
    const val KEY_BLUETOOTH = "bluetooth"
    const val KEY_DISPLAY = "display"
    const val KEY_SOUND = "sound"
    const val KEY_BATTERY = "battery"
    const val KEY_STORAGE = "storage"
    const val KEY_APP_INFO = "app_info"
    const val KEY_DATETIME = "datetime"

    private val targets: List<Target> = listOf(
        Target(KEY_SETTINGS, R.string.app_launch_panel_os_settings, R.drawable.ic_settings) {
            Intent(Settings.ACTION_SETTINGS)
        },
        Target(KEY_WIFI, R.string.app_launch_panel_os_wifi, R.drawable.ic_settings) {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        },
        Target(KEY_BLUETOOTH, R.string.app_launch_panel_os_bluetooth, R.drawable.ic_settings) {
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        },
        Target(KEY_DISPLAY, R.string.app_launch_panel_os_display, R.drawable.ic_settings) {
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        },
        Target(KEY_SOUND, R.string.app_launch_panel_os_sound, R.drawable.ic_settings) {
            Intent(Settings.ACTION_SOUND_SETTINGS)
        },
        Target(KEY_BATTERY, R.string.app_launch_panel_os_battery, R.drawable.ic_settings) {
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        },
        Target(KEY_STORAGE, R.string.app_launch_panel_os_storage, R.drawable.ic_settings) {
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        },
        Target(KEY_APP_INFO, R.string.app_launch_panel_os_app_info, R.drawable.ic_info) { context ->
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            )
        },
        Target(KEY_DATETIME, R.string.app_launch_panel_os_datetime, R.drawable.ic_settings) {
            Intent(Settings.ACTION_DATE_SETTINGS)
        },
    )

    fun byKey(key: String): Target? = targets.firstOrNull { it.key == key }

    /** Targets whose intent resolves on this device. PackageManager work - run off the main thread. */
    fun available(context: Context): List<Target> =
        targets.filter { context.packageManager.resolveActivityCompat(it.intent(context), 0) != null }

    /** Whether [key]'s intent still resolves on this device (used at launch/render time). */
    fun isResolvable(context: Context, key: String): Boolean {
        val target = byKey(key) ?: return false
        return context.packageManager.resolveActivityCompat(target.intent(context), 0) != null
    }
}

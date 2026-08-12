package com.sza.fastmediasorter.core.panel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.radio.RadioKind
import com.sza.fastmediasorter.util.resolveActivityCompat

/**
 * Curated catalog of common OS system targets offered in the app-launch panel (strategic S0663 §6.3:
 * the fixed set of nine). [available] returns only the targets whose intent actually resolves on the
 * device, so a tile never leads nowhere. Resolution touches the PackageManager - call [available]
 * off the main thread.
 */
object OsShortcutCatalog {

    /**
     * S1441: [radio] and [fallbackIntent] are declared before [intent] on purpose - [intent] has to stay the
     * last parameter or the trailing-lambda form every target is written in stops compiling.
     *
     * @param radio non-null when tapping the target should first try to switch a radio; null means the target
     *   can only open a system screen, which is what fifteen of the seventeen do.
     * @param fallbackIntent preferred surface when the attempt did not take - the system Wi-Fi panel keeps the
     *   user inside the launcher, where [intent] would leave it. Falls back to [intent] when absent or when it
     *   does not resolve on this device.
     */
    data class Target(
        val key: String,
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
        val radio: RadioKind? = null,
        val fallbackIntent: ((Context) -> Intent)? = null,
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
    const val KEY_DEV_OPTIONS = "dev_options"
    const val KEY_BATTERY_SAVER = "battery_saver"
    const val KEY_AUTO_ROTATE = "auto_rotate"
    const val KEY_ACCESSIBILITY = "accessibility"
    const val KEY_WIRELESS = "wireless"
    const val KEY_DATA_USAGE = "data_usage"
    const val KEY_NFC = "nfc"
    const val KEY_VPN = "vpn"

    // Settings.ACTION_* are compile-time String constants; the value is inlined so it exists on every
    // API level and [available] can probe it via resolveActivity. InlinedApi (auto-rotate API 31, data
    // usage API 28, VPN API 24) is therefore the intended behaviour, not a bug: an unresolvable target
    // simply never renders. So the suppression is safe here.
    @SuppressLint("InlinedApi")
    private val targets: List<Target> = listOf(
        // S1405: the robot, not a gear. The gear means "this app's own settings" everywhere else in the
        // product, and on the launcher's Start menu the two rows sit next to each other - identical
        // glyphs made the pair readable only by its caption.
        Target(KEY_SETTINGS, R.string.app_launch_panel_os_settings, R.drawable.ic_android) {
            Intent(Settings.ACTION_SETTINGS)
        },
        Target(
            KEY_WIFI,
            R.string.app_launch_panel_os_wifi,
            R.drawable.ic_wifi,
            radio = RadioKind.WIFI,
            // The API 29 panel drops a toggle over the launcher instead of replacing it, so a user whose
            // firmware refused the direct attempt still comes back to where they were.
            fallbackIntent = { Intent(Settings.Panel.ACTION_WIFI) },
        ) {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        },
        // No panel counterpart exists for Bluetooth, so a refused attempt opens the full settings screen.
        Target(
            KEY_BLUETOOTH,
            R.string.app_launch_panel_os_bluetooth,
            R.drawable.ic_bluetooth,
            radio = RadioKind.BLUETOOTH,
        ) {
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        },
        Target(KEY_DISPLAY, R.string.app_launch_panel_os_display, R.drawable.ic_display) {
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        },
        Target(KEY_SOUND, R.string.app_launch_panel_os_sound, R.drawable.ic_volume_up) {
            Intent(Settings.ACTION_SOUND_SETTINGS)
        },
        Target(KEY_BATTERY, R.string.app_launch_panel_os_battery, R.drawable.ic_battery) {
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        },
        Target(KEY_STORAGE, R.string.app_launch_panel_os_storage, R.drawable.ic_storage) {
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        },
        Target(KEY_APP_INFO, R.string.app_launch_panel_os_app_info, R.drawable.ic_info) { context ->
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            )
        },
        Target(KEY_DATETIME, R.string.app_launch_panel_os_datetime, R.drawable.ic_schedule) {
            Intent(Settings.ACTION_DATE_SETTINGS)
        },
        Target(KEY_DEV_OPTIONS, R.string.app_launch_panel_os_dev_options, R.drawable.ic_developer_options) {
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        },
        Target(KEY_BATTERY_SAVER, R.string.app_launch_panel_os_battery_saver, R.drawable.ic_battery) {
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        },
        Target(KEY_AUTO_ROTATE, R.string.app_launch_panel_os_auto_rotate, R.drawable.ic_screen_rotation) {
            Intent(Settings.ACTION_AUTO_ROTATE_SETTINGS)
        },
        Target(KEY_ACCESSIBILITY, R.string.app_launch_panel_os_accessibility, R.drawable.ic_accessibility) {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        },
        Target(KEY_WIRELESS, R.string.app_launch_panel_os_wireless, R.drawable.ic_wifi_tethering) {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        },
        Target(KEY_DATA_USAGE, R.string.app_launch_panel_os_data_usage, R.drawable.ic_data_usage) {
            Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
        },
        Target(KEY_NFC, R.string.app_launch_panel_os_nfc, R.drawable.ic_nfc) {
            Intent(Settings.ACTION_NFC_SETTINGS)
        },
        Target(KEY_VPN, R.string.app_launch_panel_os_vpn, R.drawable.ic_lock) {
            Intent(Settings.ACTION_VPN_SETTINGS)
        },
    )

    fun byKey(key: String): Target? = targets.firstOrNull { it.key == key }

    /** The full curated set, unfiltered by device resolvability. Mirrors [InternalRouteCatalog.all]. */
    fun all(): List<Target> = targets

    /** Targets whose intent resolves on this device. PackageManager work - run off the main thread. */
    fun available(context: Context): List<Target> =
        targets.filter { context.packageManager.resolveActivityCompat(it.intent(context), 0) != null }

    /** Whether [key]'s intent still resolves on this device (used at launch/render time). */
    fun isResolvable(context: Context, key: String): Boolean {
        val target = byKey(key) ?: return false
        return context.packageManager.resolveActivityCompat(target.intent(context), 0) != null
    }
}

package com.sza.fastmediasorter.core.share

import android.content.pm.PackageManager

/**
 * S0303 (Phase 01): catalogue of known Telegram client package ids and installed-client lookup.
 *
 * Used by the share surfaces to target an installed Telegram client directly with `ACTION_SEND`,
 * falling back to the generic chooser when none of these is present. No UI, no network.
 */
object TelegramShareTargets {

    /**
     * Known Telegram client package ids, ordered by preference: official build first, then
     * Telegram X, then the common forks/variants. The first installed one wins.
     */
    val PACKAGE_IDS: List<String> = listOf(
        "org.telegram.messenger",        // official Telegram
        "org.telegram.messenger.web",    // Play Store variant
        "org.thunderdog.challegram",     // Telegram X
        "org.telegram.plus",             // Plus Messenger fork
        "org.telegram.messenger.beta",   // official beta
    )

    /**
     * @return the first [PACKAGE_IDS] entry that is installed, or `null` when no Telegram
     *         client is present on the device.
     */
    fun firstInstalledPackage(packageManager: PackageManager): String? =
        PACKAGE_IDS.firstOrNull { isInstalled(packageManager, it) }

    private fun isInstalled(packageManager: PackageManager, packageId: String): Boolean = try {
        packageManager.getPackageInfo(packageId, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

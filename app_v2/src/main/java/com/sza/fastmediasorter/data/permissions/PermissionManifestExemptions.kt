package com.sza.fastmediasorter.data.permissions

/**
 * S1436: the declared exceptions to "every permission in the manifest is a row of the registry, and
 * every row is declared in the manifest". Both directions are checked mechanically by
 * `PermissionRegistryManifestParityTest`; this file is the only way to be excused from either, and
 * an entry cannot exist without a reason - strategic §7 records that a parity gate which can be
 * relaxed silently is a parity gate that gets switched off the first time it is inconvenient.
 *
 * A reason is prose for the next reader, not a formality: it must say why the user never needs to
 * decide about this permission, or why the row cannot be declared.
 */
object PermissionManifestExemptions {

    /**
     * Declared in a manifest, no registry row. Granted at install with no user decision to make, so
     * a row would be a line the user can neither act on nor turn off.
     */
    val declaredWithoutRow: Map<String, String> = mapOf(
        "android.permission.INTERNET" to
            "Normal permission, granted at install; every remote source depends on it.",
        "android.permission.ACCESS_NETWORK_STATE" to
            "Normal permission; read to tell an offline device from a failed transfer.",
        "android.permission.ACCESS_WIFI_STATE" to
            "Normal permission; read to name the current network on local-network sources.",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE" to
            "Normal permission; needed to discover DLNA and SMB hosts by multicast.",
        "android.permission.WAKE_LOCK" to
            "Normal permission; held while a long transfer or a playback session runs.",
        "android.permission.VIBRATE" to
            "Normal permission; haptic feedback on sort actions.",
        "android.permission.RECEIVE_BOOT_COMPLETED" to
            "Normal permission; re-arms scheduled operations after a restart.",
        "android.permission.FOREGROUND_SERVICE" to
            "Normal permission; the umbrella declaration every foreground service needs.",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" to
            "Foreground-service type, not a user decision - the playback service declares what it is.",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC" to
            "Foreground-service type for the transfer worker; granted with the service, not by the user.",
        "android.permission.FOREGROUND_SERVICE_MICROPHONE" to
            "Foreground-service type for recording; the user decision is RECORD_AUDIO, which has its own row.",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" to
            "Foreground-service type for the overlay host; the user decision is SYSTEM_ALERT_WINDOW, which has " +
            "its own row.",
        "android.permission.NFC" to
            "Declared by the com.yubico.yubikit:android dependency, not by this app; nothing here touches NFC, " +
            "and a row would offer to grant a capability the app does not have.",
        "com.oculus.permission.HAND_TRACKING" to
            "Granted by the headset at install; there is no runtime dialog and no system screen to send the user to.",
        "android.permission.RECORD_AUDIO" to
            "Temporary, and only true where SUPPORT_MIC_RECORDING is false - lite and photos declare it through " +
            "src/main while having no microphone feature. That is a manifest defect tracked as S1442, not a " +
            "permanent exemption: S1442 removes the declaration and this entry with it. Everywhere else the " +
            "permission has a row.",
    )

    /**
     * Exempt by name suffix rather than by exact name, because the full name carries the application
     * id and therefore differs per build type. Kept separate and tiny on purpose: a suffix match is
     * broader than an exact one, so each entry has to earn it.
     */
    val declaredWithoutRowSuffixes: Map<String, String> = mapOf(
        ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" to
            "Defined and used by androidx.core for its own receiver registration; a signature permission this " +
            "app grants itself, prefixed with the application id, so no user ever sees or decides it.",
    )

    /**
     * A registry row whose permission is not a `uses-permission` anywhere. The reverse direction, and
     * rarer: it means the capability is held by something other than a permission request.
     */
    val rowWithoutDeclaration: Map<String, String> = mapOf(
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to
            "S0429: held by the <service> declaration in src/launcherEnabled, never requested. The row exists " +
            "because the user does grant it - on the notification-access screen - so the list would be lying by " +
            "omission without it.",
    )

    /** True when [permission] is excused from having a registry row, by exact name or by suffix. */
    fun isDeclaredWithoutRowAllowed(permission: String): Boolean =
        permission in declaredWithoutRow || declaredWithoutRowSuffixes.keys.any { permission.endsWith(it) }

    init {
        (declaredWithoutRow + declaredWithoutRowSuffixes + rowWithoutDeclaration).forEach { (permission, reason) ->
            require(reason.isNotBlank()) { "Permission exemption without a reason: $permission" }
        }
    }
}

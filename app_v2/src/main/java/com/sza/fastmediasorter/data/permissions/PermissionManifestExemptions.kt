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
        "android.permission.CHANGE_WIFI_STATE" to
            "Normal permission, granted at install; lets the launcher Wi-Fi tile switch the radio itself " +
            "on firmwares that still allow it, falling back to the system screen where they do not.",
        "android.permission.BLUETOOTH" to
            "Install-time permission below API 31, declared by src/networkMonitor with maxSdkVersion 30; it " +
            "is what lets the Monitor read the adapter state on Android 8.0-11. There is no dialog and no " +
            "system screen to send the user to, and from API 31 the decision is BLUETOOTH_CONNECT, which has " +
            "its own row.",
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
        "android.permission.EXPAND_STATUS_BAR" to
            "Normal permission, granted at install; there is no dialog and no system screen to send the user " +
            "to. S2386 declared it for the launcher shade gesture, and DeviceActionHandler consumes it by " +
            "reflecting expandNotificationsPanel / expandSettingsPanel on the statusbar service.",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" to
            "Foreground-service type for the overlay host; the user decision is SYSTEM_ALERT_WINDOW, which has " +
            "its own row.",
        "android.permission.NFC" to
            "Declared by the com.yubico.yubikit:android dependency, not by this app; nothing here touches NFC, " +
            "and a row would offer to grant a capability the app does not have.",
        "com.oculus.permission.HAND_TRACKING" to
            "Granted by the headset at install; there is no runtime dialog and no system screen to send the user to.",
        "org.khronos.openxr.permission.OPENXR" to
            "Merged in from the org.khronos.openxr:openxr_loader_for_android AAR, not declared by this app. The " +
            "loader is on both vrImplementation and noLegalImplementation because S0250 makes noLegal the " +
            "sideload VR-capable surface, so noLegal declares it too - that is deliberate, not a leftover of " +
            "S0241. The runtime consumes it; there is no dialog and no system screen, so a row would offer to " +
            "grant something the user cannot grant (S1475).",
        "org.khronos.openxr.permission.OPENXR_SYSTEM" to
            "Same origin and same reasoning as OPENXR above - the OpenXR loader AAR declares the pair together " +
            "(S1475).",
        "android.permission.POST_NOTIFICATIONS" to
            "Declared and used, row hidden by too narrow a gate - the shape RECORD_AUDIO had until S1459 " +
            "moved its row onto DECLARES_MIC_RECORDING. Builds " +
            "without persistent audio playback still post notifications - ScheduledOperationsWorker creates " +
            "its channel and notifies unconditionally, and onboarding asks for this permission past the gate " +
            "via shownInWelcomeDespiteGates - but the row's only gate is ENABLE_PERSISTENT_AUDIO_PLAYBACK, " +
            "which names one use and not the other. Widening that gate changes what the permissions screen " +
            "lists, so it is a separate change; until then the declaration is legitimate with no row.",
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

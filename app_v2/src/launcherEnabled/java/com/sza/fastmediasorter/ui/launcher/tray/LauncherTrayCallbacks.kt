package com.sza.fastmediasorter.ui.launcher.tray

/**
 * S1767: what a tray placement asks of its host.
 *
 * One holder rather than one parameter each: the strip's bind already carried seven arguments, and the
 * second callback pushed it past detekt's ceiling. Grouping them also keeps the two placements honest -
 * a callback added for the taskbar tray cannot quietly go missing from the status strip.
 */
data class LauncherTrayCallbacks(
    val onRequestPhoneStatePermission: () -> Unit = {},
    val onRequestBluetoothPermission: () -> Unit = {},
    /** Opens the Android settings section an indicator reports on, by [OsShortcutCatalog] key. */
    val onOpenSystemScreen: (String) -> Unit = {},
    /** Opens our Monitor section when it is available, else the Android screen the indicator reports on. */
    val onOpenNetworkSurface: (sectionKey: String, osShortcutKey: String) -> Unit = { _, _ -> },
)

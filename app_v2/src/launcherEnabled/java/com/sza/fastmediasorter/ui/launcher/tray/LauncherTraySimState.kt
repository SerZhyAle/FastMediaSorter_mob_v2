package com.sza.fastmediasorter.ui.launcher.tray

/**
 * Per-slot SIM status carrying signal level, roaming flag, and mobile data network type.
 */
data class LauncherTraySimState(
    val signalLevel: Int,
    val roaming: Boolean = false,
    val dataNetworkType: Int? = null,
    val nrAdvanced: Boolean = false,
)

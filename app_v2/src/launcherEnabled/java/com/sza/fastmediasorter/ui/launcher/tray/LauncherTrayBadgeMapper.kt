package com.sza.fastmediasorter.ui.launcher.tray

/**
 * Pure mapper for launcher tray badges (Bluetooth connected count, SIM data type, transfer speed).
 *
 * Keeps tray badge rules testable on JVM without Android framework dependencies.
 */
object LauncherTrayBadgeMapper {

    // TelephonyManager NETWORK_TYPE_* constants, named locally for JVM portability.
    const val NETWORK_TYPE_GPRS = 1
    const val NETWORK_TYPE_EDGE = 2
    const val NETWORK_TYPE_UMTS = 3
    const val NETWORK_TYPE_CDMA = 4
    const val NETWORK_TYPE_EVDO_0 = 5
    const val NETWORK_TYPE_EVDO_A = 6
    const val NETWORK_TYPE_1xRTT = 7
    const val NETWORK_TYPE_HSDPA = 8
    const val NETWORK_TYPE_HSUPA = 9
    const val NETWORK_TYPE_HSPA = 10
    const val NETWORK_TYPE_EVDO_B = 12
    const val NETWORK_TYPE_LTE = 13
    const val NETWORK_TYPE_EHRPD = 14
    const val NETWORK_TYPE_HSPAP = 15
    const val NETWORK_TYPE_GSM = 16
    const val NETWORK_TYPE_TD_SCDMA = 17
    const val NETWORK_TYPE_NR = 20

    /** Returns connected count string or null when null, zero or negative. */
    fun bluetoothBadge(count: Int?): String? = when {
        count == null || count <= 0 -> null
        else -> count.toString()
    }

    /** Returns data type letter (G, E, 3G, H, H+, 4G, 5G) or null for unknown/none. */
    fun dataTypeBadge(networkType: Int?, nrAdvanced: Boolean = false): String? {
        if (nrAdvanced) return "5G"
        return when (networkType) {
            NETWORK_TYPE_GPRS, NETWORK_TYPE_GSM -> "G"
            NETWORK_TYPE_EDGE -> "E"
            NETWORK_TYPE_UMTS, NETWORK_TYPE_CDMA, NETWORK_TYPE_EVDO_0,
            NETWORK_TYPE_EVDO_A, NETWORK_TYPE_1xRTT, NETWORK_TYPE_EVDO_B,
            NETWORK_TYPE_EHRPD, NETWORK_TYPE_TD_SCDMA -> "3G"
            NETWORK_TYPE_HSDPA, NETWORK_TYPE_HSUPA, NETWORK_TYPE_HSPA -> "H"
            NETWORK_TYPE_HSPAP -> "H+"
            NETWORK_TYPE_LTE -> "4G"
            NETWORK_TYPE_NR -> "5G"
            else -> null
        }
    }
}

package com.sza.fastmediasorter.core.networkmonitor

/**
 * Pure mapper converting platform Wi-Fi standard constants (from ScanResult / WifiInfo)
 * into generation digits (4, 5, 6, 7) and display names.
 *
 * Performs no platform call and takes no Context.
 */
object WifiGenerationMapper {

    const val WIFI_STANDARD_LEGACY = 1
    const val WIFI_STANDARD_11N = 4
    const val WIFI_STANDARD_11AC = 5
    const val WIFI_STANDARD_11AX = 6
    const val WIFI_STANDARD_11BE = 7

    private const val GEN_4 = 4
    private const val GEN_5 = 5
    private const val GEN_6 = 6
    private const val GEN_7 = 7

    /** Returns generation digit (4, 5, 6, 7) or null for legacy/unknown. */
    fun generationOf(wifiStandard: Int): Int? = when (wifiStandard) {
        WIFI_STANDARD_11N -> GEN_4
        WIFI_STANDARD_11AC -> GEN_5
        WIFI_STANDARD_11AX -> GEN_6
        WIFI_STANDARD_11BE -> GEN_7
        else -> null
    }

    /** Returns standard display name string or null for legacy/unknown. */
    fun displayName(wifiStandard: Int): String? = when (wifiStandard) {
        WIFI_STANDARD_LEGACY -> "802.11 a/b/g"
        WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
        WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
        WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
        WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
        else -> null
    }
}

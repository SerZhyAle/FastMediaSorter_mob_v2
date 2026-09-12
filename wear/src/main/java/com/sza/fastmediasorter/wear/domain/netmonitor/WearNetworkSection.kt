package com.sza.fastmediasorter.wear.domain.netmonitor

enum class WearNetworkSection(val key: String) {
    Summary("summary"),
    Wifi("wifi"),
    Mobile("mobile"),
    Bluetooth("bluetooth"),
    Gnss("gnss"),
    Traffic("traffic"),
    Internet("internet"),
    History("history");

    companion object {
        fun fromKey(key: String?): WearNetworkSection =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: Summary
    }
}

data class WearNetworkCapabilities(
    val hasWifi: Boolean,
    val hasMobile: Boolean,
    val hasBluetooth: Boolean,
    val hasLocation: Boolean,
)

/**
 * All applicable sections for the button panel on the dashboard.
 * Note: per Quiz decision 5 and ADR-2, sections remain in the list so users can open them
 * and see live telemetry or a clear explanation (e.g. Mobile on a non-cellular watch).
 */
fun sectionsFor(capabilities: WearNetworkCapabilities): List<WearNetworkSection> = buildList {
    if (capabilities.hasWifi) add(WearNetworkSection.Wifi)
    add(WearNetworkSection.Mobile)
    if (capabilities.hasBluetooth) add(WearNetworkSection.Bluetooth)
    if (capabilities.hasLocation) add(WearNetworkSection.Gnss)
    add(WearNetworkSection.Traffic)
    add(WearNetworkSection.Internet)
    add(WearNetworkSection.History)
}

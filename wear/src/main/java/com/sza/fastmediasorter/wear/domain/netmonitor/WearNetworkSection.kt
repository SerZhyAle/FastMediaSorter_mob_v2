package com.sza.fastmediasorter.wear.domain.netmonitor

enum class WearNetworkSection(val key: String) {
    Summary("summary"),
    Wifi("wifi"),
    Mobile("mobile"),
    Bluetooth("bluetooth"),
    Gnss("gnss"),
    Internet("internet"),
    History("history"),
}

data class WearNetworkCapabilities(
    val hasWifi: Boolean,
    val hasMobile: Boolean,
    val hasBluetooth: Boolean,
    val hasLocation: Boolean,
)

fun sectionsFor(capabilities: WearNetworkCapabilities): List<WearNetworkSection> = buildList {
    add(WearNetworkSection.Summary)
    if (capabilities.hasWifi) add(WearNetworkSection.Wifi)
    if (capabilities.hasMobile) add(WearNetworkSection.Mobile)
    if (capabilities.hasBluetooth) add(WearNetworkSection.Bluetooth)
    if (capabilities.hasLocation) add(WearNetworkSection.Gnss)
    add(WearNetworkSection.Internet)
    add(WearNetworkSection.History)
}

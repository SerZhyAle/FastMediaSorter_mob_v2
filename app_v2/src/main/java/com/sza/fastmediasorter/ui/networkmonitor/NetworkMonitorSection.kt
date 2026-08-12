package com.sza.fastmediasorter.ui.networkmonitor

import android.content.Intent

/**
 * S1433: the addressable screens of the Network Monitor.
 *
 * Each case carries a stable [key] rather than relying on its ordinal or its declaration order, because the
 * launcher route, the shortcut and S1440's widgets all address a section from outside the process: an
 * ordinal would silently retarget every saved tile the day a section is inserted in the middle.
 */
enum class NetworkMonitorSection(val key: String) {
    Summary("summary"),
    Wifi("wifi"),
    Mobile("mobile"),
    Bluetooth("bluetooth"),
    Gnss("gnss"),
    Internet("internet"),
    History("history"),
    ;

    companion object {

        /** Intent extra carrying [key]. Public so external entry points can address a section by name. */
        const val EXTRA_SECTION = "extra_network_monitor_section"

        /**
         * Resolves [key] back to a section, falling back to [Summary].
         *
         * An unknown key is a caller from an older or newer build - a pinned shortcut outlives the release
         * that created it - so it opens the screen rather than refusing to launch.
         */
        fun fromKey(key: String?): NetworkMonitorSection =
            entries.firstOrNull { it.key == key } ?: Summary
    }
}

/** Writes [section] into a Monitor launch intent. */
fun Intent.putNetworkMonitorSection(section: NetworkMonitorSection): Intent =
    putExtra(NetworkMonitorSection.EXTRA_SECTION, section.key)

/** Reads the section a caller asked for, or [NetworkMonitorSection.Summary] when it asked for none. */
fun Intent.readNetworkMonitorSection(): NetworkMonitorSection =
    NetworkMonitorSection.fromKey(getStringExtra(NetworkMonitorSection.EXTRA_SECTION))

package com.sza.fastmediasorter.widget.networkmonitor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection

/**
 * S1440: the eight network-monitor indicators a widget or launcher gadget can show.
 *
 * One declaration owns key, label, icon, target sub-screen, refresh contract and default footprint,
 * because the widget, its configuration screen and the launcher gadget all need the same eight-way
 * table - three private copies would drift the first time an indicator changed.
 *
 * [key] is a **persisted storage format**: it is written into the widget preferences and into a
 * desktop cell's target column, and it outlives the release that wrote it. Never rename a key, never
 * renumber one, and never persist an ordinal instead.
 */
enum class NetworkMonitorIndicator(
    val key: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val section: NetworkMonitorSection,
    val refresh: Refresh,
    val spanW: Int,
    val spanH: Int
) {
    LOCAL_ADDRESS(
        key = "local_address",
        labelRes = R.string.widget_network_monitor_indicator_local_address,
        iconRes = R.drawable.ic_network_monitor,
        section = NetworkMonitorSection.Internet,
        refresh = Refresh.EVENT,
        spanW = 2,
        spanH = 1
    ),
    RADIO_STATUS(
        key = "radio_status",
        labelRes = R.string.widget_network_monitor_indicator_radio_status,
        iconRes = R.drawable.ic_wifi,
        section = NetworkMonitorSection.Summary,
        refresh = Refresh.EVENT,
        spanW = 2,
        spanH = 1
    ),
    SIGNAL_LEVEL(
        key = "signal_level",
        labelRes = R.string.widget_network_monitor_indicator_signal_level,
        iconRes = R.drawable.ic_signal_cellular,
        section = NetworkMonitorSection.Wifi,
        refresh = Refresh.EVENT,
        spanW = 1,
        spanH = 1
    ),
    LIVE_THROUGHPUT(
        key = "live_throughput",
        labelRes = R.string.widget_network_monitor_indicator_live_throughput,
        iconRes = R.drawable.ic_speed,
        section = NetworkMonitorSection.Internet,
        refresh = Refresh.VISIBLE_ONLY,
        spanW = 2,
        spanH = 1
    ),
    EXTERNAL_ADDRESS(
        key = "external_address",
        labelRes = R.string.widget_network_monitor_indicator_external_address,
        iconRes = R.drawable.ic_wifi_tethering,
        section = NetworkMonitorSection.Internet,
        refresh = Refresh.ON_TAP,
        spanW = 2,
        spanH = 1
    ),
    LAST_MEASUREMENT(
        key = "last_measurement",
        labelRes = R.string.widget_network_monitor_indicator_last_measurement,
        iconRes = R.drawable.ic_speed,
        section = NetworkMonitorSection.History,
        refresh = Refresh.ON_TAP,
        spanW = 2,
        spanH = 1
    ),
    RESOURCE_REACHABILITY(
        key = "resource_reachability",
        labelRes = R.string.widget_network_monitor_indicator_resource_reachability,
        iconRes = R.drawable.ic_mobile_network,
        section = NetworkMonitorSection.Internet,
        refresh = Refresh.ON_TAP,
        spanW = 2,
        spanH = 1
    ),
    SATELLITE_COUNT(
        key = "satellite_count",
        labelRes = R.string.widget_network_monitor_indicator_satellite_count,
        iconRes = R.drawable.ic_satellites,
        section = NetworkMonitorSection.Gnss,
        refresh = Refresh.EVENT,
        spanW = 1,
        spanH = 1
    ),
    ;

    /**
     * The target sub-screen in its persisted form.
     *
     * Both surfaces store this string rather than the enum, because `NetworkMonitorSection.fromKey`
     * resolves an unknown one back to the summary instead of refusing to launch - which is what a
     * widget configured by an older release needs when a section is renamed or dropped.
     */
    val sectionKey: String get() = section.key

    companion object {

        /**
         * Resolves a persisted [key] back to an indicator, falling back to [LOCAL_ADDRESS].
         *
         * Mirrors `NetworkMonitorSection.fromKey` deliberately: a widget placed by an older release
         * must keep working after this catalogue changes, showing something rather than nothing.
         */
        fun fromKey(key: String?): NetworkMonitorIndicator =
            entries.firstOrNull { it.key == key } ?: LOCAL_ADDRESS
    }
}

/** How often an indicator's value is worth re-reading. Strategic 4.3 assigns one class per indicator. */
enum class Refresh {

    /** Re-read when the system reports a change; cheap, because the platform pushes it. */
    EVENT,

    /** Re-read only when the user asks, because the read costs a network round trip. */
    ON_TAP,

    /** Re-read on a bounded poll while the surface is visible, and never while it is not. */
    VISIBLE_ONLY
}

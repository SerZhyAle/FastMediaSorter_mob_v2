package com.sza.fastmediasorter.widget.networkmonitor

import android.content.Context
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection

/**
 * Per-instance choice for a placed Network Monitor widget, keyed by `AppWidgetManager.EXTRA_APPWIDGET_ID`.
 *
 * Lives beside the other configurable widgets in the shared widget preferences file rather than in
 * the Hilt graph: an `AppWidgetProvider` is a `BroadcastReceiver`, so the provider that reads this has
 * no constructor to inject into, and the launcher gadget deliberately does not mirror these values.
 *
 * Everything persisted here is a stable string key, never an enum name and never an ordinal. Both
 * `NetworkMonitorIndicator.fromKey` and `NetworkMonitorSection.fromKey` resolve an unrecognised key to
 * a working default, which is what a widget placed by an older release needs once this catalogue
 * changes; an ordinal would instead retarget every saved tile the day an entry is inserted in the
 * middle.
 */
object NetworkMonitorWidgetIndicatorStore {

    private const val PREFS_NAME = "widget_prefs"
    private const val KEY_INDICATOR_PREFIX = "network_monitor_indicator_"
    private const val KEY_SECTION_PREFIX = "network_monitor_section_"
    private const val KEY_RESOURCE_PREFIX = "network_monitor_resource_"

    /** Sentinel matching the one the neighbouring resource-launch widget already stores. */
    const val NO_RESOURCE_ID = -1L

    /**
     * The indicator chosen for [appWidgetId], or null when this id was never configured.
     *
     * Null is a state the provider draws, not an error: a widget dropped on the home screen exists
     * before its configuration activity returns, and it must render an unconfigured cell rather than
     * guess an indicator.
     */
    fun read(context: Context, appWidgetId: Int): NetworkMonitorIndicator? {
        val stored = prefs(context).getString(KEY_INDICATOR_PREFIX + appWidgetId, null)
            ?: return null
        return NetworkMonitorIndicator.fromKey(stored)
    }

    fun write(context: Context, appWidgetId: Int, indicator: NetworkMonitorIndicator) {
        prefs(context).edit()
            .putString(KEY_INDICATOR_PREFIX + appWidgetId, indicator.key)
            .putString(KEY_SECTION_PREFIX + appWidgetId, indicator.sectionKey)
            .apply()
    }

    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(KEY_INDICATOR_PREFIX + appWidgetId)
            .remove(KEY_SECTION_PREFIX + appWidgetId)
            .remove(KEY_RESOURCE_PREFIX + appWidgetId)
            .apply()
    }

    /**
     * The sub-screen a tap on [appWidgetId] opens.
     *
     * Read back through [NetworkMonitorSection.fromKey] so a key written by another release resolves
     * to the summary instead of refusing to launch.
     */
    fun readSection(context: Context, appWidgetId: Int): NetworkMonitorSection =
        NetworkMonitorSection.fromKey(prefs(context).getString(KEY_SECTION_PREFIX + appWidgetId, null))

    /** The resource `RESOURCE_REACHABILITY` was pointed at, or [NO_RESOURCE_ID] when none was picked. */
    fun readResourceId(context: Context, appWidgetId: Int): Long =
        prefs(context).getLong(KEY_RESOURCE_PREFIX + appWidgetId, NO_RESOURCE_ID)

    fun writeResourceId(context: Context, appWidgetId: Int, resourceId: Long) {
        prefs(context).edit()
            .putLong(KEY_RESOURCE_PREFIX + appWidgetId, resourceId)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

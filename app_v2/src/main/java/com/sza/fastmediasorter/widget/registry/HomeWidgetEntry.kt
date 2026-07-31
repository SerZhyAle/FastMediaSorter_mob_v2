package com.sza.fastmediasorter.widget.registry

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * One pinnable home-screen widget offered by the in-app widget picker.
 *
 * Flavor availability is decided by installed-provider lookup (manifest gating) in
 * [HomeWidgetCatalog]; [settingGate] adds an optional runtime, settings-driven gate on top
 * (e.g. the game widget is offered only when the embedded game is enabled).
 */
data class HomeWidgetEntry(
    val providerClass: Class<out AppWidgetProvider>,
    /**
     * S1170: stable id of the launcher-desktop gadget that mirrors this widget.
     *
     * Stored, never derived from `providerClass.simpleName`, because it is persisted verbatim inside a
     * desktop cell's `target` column. A class rename - or an R8 pass that rewrites the name - would
     * otherwise orphan every cell the user had already placed, silently and irreversibly. These ids are
     * a storage format from the moment the first cell is written: do not rename or renumber them.
     */
    val gadgetKey: String,
    /**
     * S1170: the desktop cell's default footprint, taken from this widget's own `targetCellWidth` /
     * `targetCellHeight`, so a cell lands the size its twin has on the Android home screen.
     *
     * Lives here rather than only on the launcher gadget because the code that PLACES a cell sits in
     * `src/main` (Settings), while the gadget registry ships only in the launcher source set and is
     * invisible from there.
     */
    val gadgetSpanW: Int,
    val gadgetSpanH: Int,
    val labelRes: Int,
    val iconRes: Int,
    val descriptionRes: Int,
    val settingGate: ((AppSettings) -> Boolean)? = null,
) {
    fun component(context: Context): ComponentName = ComponentName(context, providerClass)
}

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
    val labelRes: Int,
    val iconRes: Int,
    val descriptionRes: Int,
    val settingGate: ((AppSettings) -> Boolean)? = null,
) {
    fun component(context: Context): ComponentName = ComponentName(context, providerClass)
}

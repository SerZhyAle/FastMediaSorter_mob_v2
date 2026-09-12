package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.AppLaunchPanelRouteIntents
import com.sza.fastmediasorter.widget.registry.HomeWidgetAccent

class FrontFlashlightWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_front_flashlight)
            val intent = AppLaunchPanelRouteIntents.frontFlashlight(context).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_front_flashlight_container, pendingIntent)
            // S2889: the identity glyph takes the sub-program's own tone, resolved in this process
            // because a theme attr inside a RemoteViews drawable resolves against the launcher's theme.
            HomeWidgetAccent.applyIconTint(views, R.id.widget_front_flashlight_icon, context, "front_flashlight")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

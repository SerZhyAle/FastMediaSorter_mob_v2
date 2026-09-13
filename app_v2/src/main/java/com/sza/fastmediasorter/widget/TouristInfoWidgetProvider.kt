package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.tourist.TouristInfoActivity
import com.sza.fastmediasorter.widget.registry.HomeWidgetAccent

import timber.log.Timber

/**
 * S3033: home-screen widget for Tourist Info sub-program - launches [TouristInfoActivity].
 */
class TouristInfoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        Timber.d("S3033: TouristInfoWidgetProvider.onUpdate count=%d", appWidgetIds.size)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_tourist_info)
            val intent = TouristInfoActivity.createIntent(context).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_tourist_info_container, pendingIntent)
            HomeWidgetAccent.applyIconTint(views, R.id.widget_tourist_info_icon, context, "tourist_info")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

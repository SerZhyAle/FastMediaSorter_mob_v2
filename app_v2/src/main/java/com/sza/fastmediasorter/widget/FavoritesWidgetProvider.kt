package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.main.MainActivity

/**
 * Widget provider for Favorites quick access
 * Shows list of favorite files with click to open
 */
class FavoritesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // First widget added
    }

    override fun onDisabled(context: Context) {
        // Last widget removed
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_favorites)

            // Intent to launch MainActivity with Favorites filter
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_favorites", true)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            views.setOnClickPendingIntent(R.id.widget_favorites_container, pendingIntent)
            
            // Set service intent for list view
            val serviceIntent = Intent(context, FavoritesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_favorites_list, serviceIntent)
            
            // Template for item clicks - launching PlayerActivity directly
            val clickIntent = Intent(context, com.sza.fastmediasorter.ui.player.PlayerActivity::class.java).apply {
                // Base flags, extras will be filled in by the service
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val clickPendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_favorites_list, clickPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            @Suppress("DEPRECATION")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_favorites_list)
        }
    }
}
